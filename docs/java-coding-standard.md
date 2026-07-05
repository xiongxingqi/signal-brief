# Java 编码规范

## 基本原则

优先级从高到低：

1. 正确性。
2. 安全性。
3. 可读性。
4. 一致性。
5. 可测试性。
6. 简洁性。
7. 局部技巧。

原则：

- 代码首先要让维护者快速理解业务意图。
- 同类问题在同一项目中使用同一种方案。
- 不为局部“优雅”破坏全局一致性。
- 新增抽象必须降低真实复杂度，不能只是换一种写法。
- 修改代码时控制边界，不顺手做无关重构。
- 本项目优先沿用 Spring Boot、MyBatis、Flyway、ROME 和 PostgreSQL 的现有边界，不为局部代码引入另一套并行方案。

## 文件与格式

### 编码

- 源码文件统一使用 `UTF-8`。
- 中文注释、日志、测试名需要保证工具链和 CI 都能正确处理。
- 避免在源码文件中混入不可见字符、BOM 和行尾空白。

### 文件组织

Java 源文件顺序保持稳定：

1. license 或版权声明，如果项目需要。
2. `package`。
3. `import`。
4. 顶层类型声明。

要求：

- 一个 `.java` 文件只放一个 public 顶层类型。
- 文件名必须和 public 顶层类型名一致。
- 类成员顺序应有明确逻辑，例如常量、字段、构造器、公开方法、私有方法。
- 重载方法不要被其他成员打散。
- 包结构按业务边界组织。当前主要边界包括 `feed`、`article`、`ingestion` 和 `config`，新增代码优先放入最贴近的现有包。
- 配置绑定类放在 `config` 包，使用 `@ConfigurationProperties` 表达配置结构，不在业务代码中直接读取散落的环境变量。

### import

- 禁止通配符导入，包括普通 import 和 static import。
- 删除未使用 import。
- 生产代码谨慎使用 static import；测试代码可以用于断言、mock DSL 等明显提升可读性的场景。
- import 排序交给 IDE 或 formatter，避免人工反复调整。
- 不引入没有使用到的依赖；新增依赖前先确认 JDK、Spring、Apache Commons 或 Guava 是否已经覆盖该能力。

### 格式

- 缩进、括号、换行和行宽以项目 formatter 为准。
- 没有 formatter 时，保持项目现有风格，不单独改格式。
- 新项目建议把格式化规则固化到构建或 CI，而不是依赖口头约定。
- 复杂表达式优先通过提取变量或提取方法降低阅读负担，而不是只靠换行。
- `if`、`for`、`while` 等控制语句即使只有一行，也建议写花括号，避免后续修改时引入隐藏错误。

## 命名

### 通用规则

- 命名表达业务语义，不只表达技术形态。
- 避免无信息量名称，例如 `data`、`info`、`obj`、`temp`、`handle`、`process`。
- 避免随意缩写，除非是团队稳定术语，例如 `DTO`、`VO`、`ID`、`URL`。
- 同一业务概念在代码、数据库、接口文档和日志中尽量使用同一套词。
- 缩写词按项目约定保持一致，不在同一项目里同时出现 `userId`、`userID`、`uid` 三种写法。

### Java 命名

- 类、接口、枚举、record 使用 `UpperCamelCase`。
- 方法、字段、局部变量使用 `lowerCamelCase`。
- 常量使用 `UPPER_SNAKE_CASE`。
- 包名使用全小写，避免下划线和大写字母。
- 测试类可使用 `XxxTest`、`XxxTests` 或项目既有命名。

### 布尔命名

布尔字段和方法必须能读成判断句：

```java
boolean enabled;
boolean deleted;

boolean canPublish();
boolean isVisibleToCurrentUser();
boolean hasExpired();
```

避免：

```java
boolean flag;
boolean status;
boolean type;
```

## 类设计

### 单一职责

- 一个类应只有一类主要变化原因。
- 如果一个类同时处理协议、业务、持久化、缓存、第三方调用，优先拆分。
- 构造器参数过多通常说明类职责过宽，需要重新看边界。

### 字段可见性

- 字段默认 `private`。
- 能不可变就不可变，优先使用 `final`。
- 不为测试随意放大字段或方法可见性。

## 方法设计

### 方法职责

- 一个方法表达一个明确动作。
- 方法内部保持同一抽象层次。
- 不在一个方法里混杂参数校验、业务编排、SQL 细节、文本解析和远程调用。
- 复杂条件优先提取具名变量或方法。

### 参数

- 参数数量过多时，优先封装为 command、query、criteria、context 或 options 对象。
- 避免通过参数顺序表达语义。
- 避免裸布尔参数，例如 `update(user, true, false)`。

可接受：

```java
public record UpdateUserCommand(
        Long userId,
        boolean notifyUser,
        boolean forceUpdate
) {
}
```

更清晰的业务选项可以使用枚举：

```java
updateUser(userId, UpdateMode.FORCE_WITH_NOTIFICATION);
```

### 返回值

- 返回值语义要明确。
- 不用 `null` 表达多种状态。
- 查询无结果时优先返回空集合。
- 单值可能不存在时，可以使用 `Optional`，但不要把 `Optional` 用作字段、DTO 属性或集合元素，除非项目已有明确约定。
- 方法有副作用时，方法名应清楚表达副作用。
- 返回集合时明确是否允许修改；如果调用方不应修改，返回不可变集合或只读视图。
- 对外暴露的时间窗口使用半开区间命名，例如 `startInclusive`、`endExclusive`，避免边界含义不清。
- 时间类型优先使用 `Instant` 表达机器时间；只有在展示或用户输入场景才转换为本地日期时间。

## 枚举、常量和值对象

- 禁止在业务代码中散落魔法值。
- 状态、类型、渠道、权限、来源等固定取值优先使用枚举。
- 分类、状态、执行结果等固定取值优先建模为枚举。当前文章分类统一使用 `ArticleCategory`，不要用字符串在业务代码中绕过类型约束。
- 枚举命名表达业务语义，不表达数据库值。
- 常量名必须解释业务意义，而不只是重复数值。

不推荐：

```java
private static final int DAYS_180 = 180;
```

推荐：

```java
private static final int POST_VISIBLE_DAYS_FOR_HALF_YEAR = 180;
```

如果值有稳定业务含义，优先进一步建模为枚举或值对象。

## 异常处理

### 基本规则

- 禁止空 `catch`。
- 不吞异常；确需吞掉时必须说明原因，并至少记录 `warn` 日志。
- 不使用模糊异常信息，例如“失败了”“出错了”。
- 包装异常时保留原始 cause。
- 不把底层 SQL、密钥、token、内部路径等敏感信息直接暴露给用户。
- 低频批处理场景允许单个 RSS 源失败后继续处理后续源，但必须记录源名称、失败原因和统计结果。

## 工具库与生态选型

- 常用字符串、集合、数组、对象和 I/O 辅助逻辑，优先使用项目已引入的 Apache Commons 与 Guava，避免手写重复工具方法。
- Apache Commons 优先用于通用判空、字符串处理、集合处理和 I/O 操作，例如 `StringUtils.isBlank`、`CollectionUtils.isEmpty`、`IOUtils`、`FileUtils`。
- Guava 优先用于 JDK 或 Spring 不足以表达清楚的不可变集合、缓存、限流、Multimap、Range 等场景，例如 `ImmutableList`、`CacheBuilder`、`RateLimiter`。
- 不为了使用工具库替换清晰的 JDK/Spring 原生 API；简单逻辑保持简单。
- 新代码不要使用 Guava `Optional`，统一使用 `java.util.Optional`。
- HTTP 客户端统一优先使用 Spring `RestClient`。配置应集中管理超时、默认 header、错误处理和日志，不在业务代码中直接散落底层 HTTP 客户端。
- JSON 解析和序列化优先使用 Spring 生态的 Jackson 能力，例如 Spring 管理的 `ObjectMapper`、`JsonNode`、record/DTO 映射和 HTTP message converter。不要随意引入 Gson、Fastjson 或手动 `new ObjectMapper()`。
- Spring Boot 已管理版本的依赖不要显式声明版本；只有项目额外引入且未被 Boot 管理的依赖，才在 `pom.xml` 属性中集中声明版本。

## Spring 使用约定

- 依赖注入优先使用构造器注入。
- 必需依赖使用 `final` 字段。
- 单构造器场景不需要显式写 `@Autowired`，除非项目已有统一要求。
- setter 注入只用于可选依赖或确有重配置需要的场景。
- 不推荐字段注入，尤其是生产代码。
- 构造器参数过多是职责过宽的信号，优先重新看类边界，而不是继续堆依赖。
- 主启动类放在应用顶层包，避免组件扫描范围过窄或过宽。
- `@Transactional` 优先放在 Service 层公开业务方法上。
- 避免同类内部方法调用导致事务、缓存、异步等代理失效。
- Bean 名称、Profile、条件装配要能从命名看出用途。
- `@Scheduled` 任务必须可配置关闭，默认避免本地启动、测试或 CI 意外访问外部网络。
- 外部调用、定时任务和邮件等副作用入口要集中配置超时、开关、默认 header 或收件人，不在业务方法里硬编码。

### 自定义配置与自动配置边界

- 默认优先使用 Spring Boot 自动配置和官方属性；只有现有属性无法表达项目语义，或需要隔离某个能力的行为时，才新增自定义配置或 Bean。
- 项目自定义配置统一放在 `signal-brief.*` 前缀下，通过 `@ConfigurationProperties` record 建模；不要把项目语义塞进 `spring.*`，也不要在业务代码中直接读取散落的环境变量。
- 自定义 Bean 默认暴露业务语义清晰的 Bean，例如 `feedRestClient`；除非确实要作为全局扩展点，不要随意暴露 `ObjectMapper`、`RestClientCustomizer`、`ClientHttpRequestFactory`、`TaskExecutor`、`CacheManager` 等通用类型 Bean。
- 多个同类型 Bean 并存时，禁止裸注入通用类型；使用有语义的 Bean 名称和 `@Qualifier` 明确选择，例如 `@Qualifier("feedRestClient") RestClient restClient`。
- 谨慎使用 `@Primary`。只有确实存在全局默认实现，并且所有无 `@Qualifier` 注入点都应使用它时，才允许加 `@Primary`。
- 谨慎使用 `RestClientCustomizer`、`Jackson2ObjectMapperBuilderCustomizer`、`WebMvcConfigurer`、`SecurityFilterChain`、`FlywayConfigurationCustomizer` 等全局扩展点。使用前要确认影响范围，并在配置类或 record 文档中说明原因。
- 专用 HTTP 客户端优先通过专用 `RestClient` 隔离超时、默认 header、重试和日志；不要修改全局 `RestClient.Builder` 来满足单个外部系统的需求。
- `@Configuration(proxyBeanMethods = false)` 只用于配置类内部不直接调用其他 `@Bean` 方法的场景；需要引用其他 Bean 时，优先通过 `@Bean` 方法参数注入，让 Spring 容器解析依赖。
- 新增自定义配置时，同步补充配置绑定测试、`.env.example`、README 或相关 record，验证默认值、显式绑定和非法值处理。

## MyBatis 与数据库

- 数据库结构变更必须通过 Flyway 迁移脚本表达，不手工依赖本地库状态。
- Java record 或不可变对象使用 MyBatis 构造器映射时，SQL 列别名、`@Arg` 名称和构造参数语义必须一致。
- SQL 中使用清晰的列别名，避免把下划线字段、Java 字段和 record 构造参数混在一起猜。
- 写入路径和查询路径可以拆分 Mapper。当前 `ArticleMapper` 负责写入与去重，`ArticleQueryMapper` 负责简报候选查询。
- 去重规则必须同时考虑应用层判断和数据库唯一约束，不能只改一侧。
- 查询时间窗口统一使用 `[startInclusive, endExclusive)`；涉及文章有效时间时，当前口径为 `COALESCE(published_at, created_at)`。

## RSS、HTTP 与内容处理

- RSS / Atom 内容交给 ROME 解析；不要在解析前把响应体强制转为 `String`，保留 `InputStream` 以便 XML 解析器处理 BOM 和 XML 声明中的编码。
- HTTP 获取统一走 Spring `RestClient`，底层实现由依赖和集中配置决定，业务代码不直接依赖 Apache HttpClient API。
- 单个 feed 的抓取、解析和入库错误要隔离，不能影响整个批次。
- 外部源返回的标题、摘要和链接都视为不可信输入；后续用于邮件或 HTML 时必须清理或转义。

## Lombok 使用

- 按需使用 Lombok，不用注解堆叠掩盖真实结构。
- 持久化实体谨慎使用 `@Data`，避免隐式生成过多方法。
- 优先使用 `@Getter`、`@Setter`、`@RequiredArgsConstructor` 等更窄的注解。
- 对 equals/hashCode 有业务含义的类必须谨慎，尤其是 Entity。
- 如果 getter/setter 有业务约束，直接手写，不依赖 Lombok 默认生成。

## 测试代码

- 纯业务逻辑优先写普通 JUnit 5 单元测试，不启动 Spring 上下文。
- 需要 Mockito 注解、自动创建 mock 或 strict stubbing 时使用 `@ExtendWith(MockitoExtension.class)`；简单协作对象可以用手写 fake 或 recording stub。
- 数据库、迁移和 Mapper 行为使用 `*IT` 集成测试覆盖，由 Maven Failsafe 在 `verify` 阶段运行。
- 本地日常默认只跑 `./mvnw test`；需要避免 Spring Boot Docker Compose 接管时设置 `-Dspring.docker.compose.enabled=false`。
- 测试 fixture 放在 `src/test/resources`，命名表达来源和场景，不把运行时生成数据混入 fixture。

## 注释与 Javadoc

- 注释密度参考 Spring 等成熟开源框架，默认保持克制。
- 只在公共契约、框架扩展点、非显而易见的业务约束、兼容性取舍、并发、事务、资源生命周期等位置补充 Javadoc 或短注释。
- 注释解释代码本身看不出来的原因、约束和取舍。
- 不写“给变量赋值”“循环列表”“判断是否公开”这类翻译型注释。
- 不为直观代码、简单字段和常规 getter/setter 写解释性注释。
- 如代码需要大量注释才能读懂，优先重命名、拆分方法或提取对象。
- 公共 API、复杂业务规则、安全约束、兼容旧逻辑要写明行为约定。
- `TODO` 必须可检索，最好带 issue、日期或明确触发条件。

推荐：

```java
// 兼容 2025-12 前的旧客户端：未传 privacyType 时按公开处理。
```

不推荐：

```java
// 判断是否公开
```

## 参考

- Google Java Style Guide: https://google.github.io/styleguide/javaguide.html
- Spring Framework Code Style: https://github.com/spring-projects/spring-framework/wiki/Code-Style
- Spring Framework Dependency Injection: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html
- Spring Boot Beans and Dependency Injection: https://docs.spring.io/spring-boot/reference/using/spring-beans-and-dependency-injection.html
- Oracle Code Conventions for the Java Programming Language: https://www.oracle.com/java/technologies/javase/codeconventions-contents.html
- Oracle Secure Coding Guidelines for Java SE: https://www.oracle.com/java/technologies/javase/seccodeguide.html
- Java Language Specification, Names: https://docs.oracle.com/en/java/javase/26/docs/specs/jls/jls-6.html
