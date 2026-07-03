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

### import

- 禁止通配符导入，包括普通 import 和 static import。
- 删除未使用 import。
- 生产代码谨慎使用 static import；测试代码可以用于断言、mock DSL 等明显提升可读性的场景。
- import 排序交给 IDE 或 formatter，避免人工反复调整。

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

## 枚举、常量和值对象

- 禁止在业务代码中散落魔法值。
- 状态、类型、渠道、权限、来源等固定取值优先使用枚举。
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

## 工具库与生态选型

- 常用字符串、集合、数组、对象和 I/O 辅助逻辑，优先使用项目已引入的 Apache Commons 与 Guava，避免手写重复工具方法。
- Apache Commons 优先用于通用判空、字符串处理、集合处理和 I/O 操作，例如 `StringUtils.isBlank`、`CollectionUtils.isEmpty`、`IOUtils`、`FileUtils`。
- Guava 优先用于 JDK 或 Spring 不足以表达清楚的不可变集合、缓存、限流、Multimap、Range 等场景，例如 `ImmutableList`、`CacheBuilder`、`RateLimiter`。
- 不为了使用工具库替换清晰的 JDK/Spring 原生 API；简单逻辑保持简单。
- 新代码不要使用 Guava `Optional`，统一使用 `java.util.Optional`。
- HTTP 客户端统一优先使用 Spring `RestClient`。配置应集中管理超时、默认 header、错误处理和日志，不在业务代码中直接散落底层 HTTP 客户端。
- JSON 解析和序列化优先使用 Spring 生态的 Jackson 能力，例如 Spring 管理的 `ObjectMapper`、`JsonNode`、record/DTO 映射和 HTTP message converter。不要随意引入 Gson、Fastjson 或手动 `new ObjectMapper()`。

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

## Lombok 使用

- 按需使用 Lombok，不用注解堆叠掩盖真实结构。
- 持久化实体谨慎使用 `@Data`，避免隐式生成过多方法。
- 优先使用 `@Getter`、`@Setter`、`@RequiredArgsConstructor` 等更窄的注解。
- 对 equals/hashCode 有业务含义的类必须谨慎，尤其是 Entity。
- 如果 getter/setter 有业务约束，直接手写，不依赖 Lombok 默认生成。


## 注释与 Javadoc

- 注释解释代码本身看不出来的原因、约束和取舍。
- 不写“给变量赋值”“循环列表”这类翻译型注释。
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
