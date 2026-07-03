# 代码上下文保存规范


## 核心结论

代码保存上下文的目标是：让维护者不只看到“值”和“流程”，还能看到“这个值代表什么”“为什么要这样判断”“规则属于哪个业务概念”。

优先做这四件事：

1. 把隐含信息显式化。
2. 把裸值升级成有名字的概念。
3. 把相关参数和规则收拢到明确对象。
4. 让命名直接体现业务语义。

这不是要求把所有变量都包装成对象，也不是要求把代码写得更“复杂”。判断标准很简单：上下文丢失会不会让后来的人改错、传错、查错或重复实现一套规则。如果不会，就保持简单。

## 两类上下文

### 一级上下文

一级上下文是代码本身就应该表达清楚的信息：

- 这是什么对象。
- 这是什么状态。
- 当前动作是什么。
- 这些参数分别代表谁。
- 这个条件判断的业务含义是什么。

一级上下文优先靠这些方式保存：

- 命名。
- 类型。
- 枚举。
- 值对象。
- 参数对象。
- 方法拆分。
- 规则收拢。

### 二级上下文

二级上下文是代码结构不容易完整表达、但维护时必须知道的信息：

- 为什么这样做。
- 规则来源是什么。
- 兼容哪个历史版本。
- 为什么不能使用更直观的方案。
- 这里有什么安全、性能、事务、时序或第三方限制。

二级上下文更适合交给注释、测试名、文档链接或 ADR 保存。

## 上下文应该放在哪里

不同上下文适合不同载体，不要把所有信息都塞进注释。

| 上下文类型 | 优先载体 |
|------------|----------|
| 业务对象和动作 | 命名、方法名、类名 |
| 固定取值范围 | 枚举、常量、值对象 |
| 同组参数关系 | Command、Query、Criteria、Context |
| 复杂判断过程 | 中间变量、提取方法、规则对象 |
| 规则入口 | Policy、Strategy、领域方法、领域服务 |
| 决策原因和历史兼容 | 注释、测试名、ADR、文档链接 |

如果一段代码需要长注释才能解释“做什么”，通常是代码表达不够；如果代码已经能说明“做什么”，但说明不了“为什么必须这样”，注释才是合适位置。

## 少用裸值，多用有名字的概念

反例：

```java
if (post.getPrivacyType() == 1) {
    return true;
}
```

问题：

- `1` 的含义不清楚。
- 合法取值范围不清楚。
- 规则来源不清楚。

改进：

```java
if (post.getPrivacyType() == PostPrivacyType.MUTUAL_FOLLOW) {
    return true;
}
```

更进一步：

```java
if (post.isVisibleToMutualFollowers()) {
    return true;
}
```

这里保存的上下文是：

- 这是“帖子隐私类型”。
- 当前分支是“互相关注可见”。
- 代码表达的是业务语义，不是数据库值。

## 用枚举表达有限状态

枚举的价值不只是防止拼错，而是把取值范围、命名和行为绑定在一起。

反例：

```java
String status; // "1" "2" "3"
```

改进：

```java
public enum OrderStatus {
    CREATED,
    PAID,
    CANCELED,
    REFUNDED;

    public boolean canRefund() {
        return this == PAID;
    }
}
```

适合枚举化的字段：

- 订单状态。
- 支付状态。
- 审核状态。
- 隐私类型。
- 消息类型。
- 权限类型。
- 来源渠道。

注意：

- 枚举名表达业务状态，不表达存储值。
- 数据库值通过转换器、TypeHandler 或显式字段适配。
- 不要为了省事在业务代码中到处比较 `"1"`、`1`、`"PAID"`。

## 把值升级成类型

如果所有 ID 都是 `Long`，编译器无法区分业务含义。

反例：

```java
Long userId;
Long postId;
Long communityId;
```

在核心领域逻辑中可以使用更明确的类型：

```java
public record UserId(Long value) {
}

public record PostId(Long value) {
}

public record CommunityId(Long value) {
}
```

适用场景：

- 核心业务域。
- 容易传错的多个同类型参数。
- 金额、时间范围、比例、坐标、库存等有明确业务规则的值。
- 需要附带校验、格式化或行为的值。

不必过度使用：

- 简单 CRUD。
- 只在 Controller DTO 中短暂存在的字段。
- 项目基础设施暂时不支持值对象映射的区域。

原则是：复杂度来自业务时，用类型承载语义；复杂度只来自包装本身时，不要包装。

值对象要尽量承载约束，而不只是给 `Long`、`String` 换一个壳。比如金额对象应该明确币种、精度和合法范围；时间窗口对象应该明确开始、结束和边界是否包含。

## 用参数对象保存关系

零散参数很容易丢失上下文。

反例：

```java
queryPosts(userId, topicId, communityId, endDate, offset, pageSize);
```

问题：

- `userId` 是当前用户还是目标用户不清楚。
- `endDate` 是创建时间上限还是统计截止时间不清楚。
- 参数顺序容易写错。

改进：

```java
public record PostQueryContext(
        Long currentUserId,
        Long topicId,
        Long communityId,
        LocalDateTime maxPostCreatedAt,
        int offset,
        int pageSize
) {
}
```

调用：

```java
queryPosts(context);
```

参数对象适合命名为：

- `Command`：表达一次写操作意图。
- `Query` / `Criteria`：表达查询条件。
- `Context`：表达执行业务规则所需上下文。
- `Options`：表达可选行为。
- `Request` / `Response`：表达协议边界。

参数对象不应变成“什么都能塞”的大袋子。如果一个对象同时承载查询条件、写操作意图、当前登录态和展示选项，说明边界还没有拆清楚。

## 警惕裸布尔参数

反例：

```java
updateUser(user, true, false);
```

这段代码无法从调用处看出两个布尔值的含义。

改进一：命令对象。

```java
public record UpdateUserCommand(
        User user,
        boolean notifyUser,
        boolean forceUpdate
) {
}
```

改进二：枚举表达模式。

```java
updateUser(user, UpdateMode.FORCE_WITH_NOTIFICATION);
```

改进三：拆成意图明确的方法。

```java
forceUpdateUserAndNotify(user);
```

选择标准：

- 如果两个布尔值会组合出多种业务模式，用枚举。
- 如果布尔值只是参数对象的一部分，用命令对象。
- 如果方法只有一个稳定业务动作，用具名方法。

这类问题在重构目录里通常对应 Remove Flag Argument / Replace Parameter with Explicit Methods：调用处不应该靠 `true` / `false` 猜业务分支。

## 方法名写业务动作

反例：

```java
process();
handle();
check();
update();
```

这些名字没有保存足够上下文。

改进：

```java
validateUserJoinCommunityRequest();
calculatePostVisibility();
markOrderAsPaid();
loadCurrentUserAccessiblePosts();
```

命名建议：

- 用动词表达动作。
- 用业务对象表达作用对象。
- 必要时加角色限定，例如 `currentUser`、`targetUser`、`operatorUser`。
- 查询方法说明返回范围，例如 `findVisiblePostsForCurrentUser`。
- 有副作用的方法不要命名成纯查询。

## 让规则靠近数据和概念

散落的 if 判断会让规则来源变得模糊。

反例：

```java
if (post.getPrivacyType() == 0) {
    return true;
}
if (post.getPrivacyType() == 1) {
    return relationService.isMutualFollow(currentUserId, post.getAuthorId());
}
return false;
```

改进：

```java
return postVisibilityPolicy.isVisibleTo(currentUserId, post);
```

或者：

```java
return post.canBeViewedBy(currentUserId, relationContext);
```

规则收拢后的价值：

- 维护者知道规则入口在哪里。
- 同一规则不会在多个地方演化出不同版本。
- 测试可以围绕规则对象编写。

不要机械地把所有规则都塞进 Entity。需要数据库、远程服务、权限上下文或复杂协作的规则，更适合放在 Policy、Domain Service 或 Application Service 中。

领域建模的重点不是套模式名，而是让代码里的词和业务讨论里的词尽量一致。团队如果已经用“互相关注可见”“补偿任务”“冻结金额”这些词，代码里就不要换成只有开发者才懂的 `type=1`、`mode=B`、`amount2`。

## 中间变量保存推理过程

反例：

```java
return userSetting != null
        && privacyType == PostPrivacyType.MUTUAL_FOLLOW
        && postCreateTime.isAfter(minVisibleTime)
        && !blacklistUsers.contains(currentUserId);
```

改进：

```java
boolean hasUserSetting = userSetting != null;
boolean isMutualFollowOnly = privacyType == PostPrivacyType.MUTUAL_FOLLOW;
boolean isWithinVisibleWindow = postCreateTime.isAfter(minVisibleTime);
boolean isCurrentUserNotBlacklisted = !blacklistUsers.contains(currentUserId);

return hasUserSetting
        && isMutualFollowOnly
        && isWithinVisibleWindow
        && isCurrentUserNotBlacklisted;
```

中间变量不是为了“多写几行”，而是保存判断过程。特别适用于：

- 多条件组合。
- 金额计算。
- 权限判断。
- 时间窗口判断。
- 状态流转。

相反，如果表达式本身已经很清楚，强行拆出一堆变量反而会制造噪音。提取变量的价值在于解释意图，不在于把每个操作都命名一次。

## 常量只是第一步，概念才是重点

反例：

```java
private static final int DAYS_180 = 180;
```

改进：

```java
private static final int POST_VISIBLE_DAYS_FOR_HALF_YEAR = 180;
```

如果这个值有稳定业务含义，可以继续抽成枚举：

```java
public enum PostVisibilityWindow {
    ALL_TIME(0),
    HALF_YEAR(180),
    THIRTY_DAYS(30);

    private final int days;

    PostVisibilityWindow(int days) {
        this.days = days;
    }

    public int days() {
        return days;
    }
}
```

判断标准：

- 临时局部值：用具名变量。
- 多处复用的固定值：用常量。
- 有固定取值范围：用枚举。
- 有校验、单位、行为：用值对象。
- 有复杂策略：用策略类或领域服务。

## 注释保存决策，不翻译代码

注释不是保存上下文的第一手段。能靠命名、类型、方法和测试表达清楚的，不要交给注释。

适合写注释：

- 业务规则来源。
- 兼容旧版本。
- 非直觉约束。
- 第三方组件限制。
- 性能、安全、事务、并发上的特殊取舍。
- 数学公式、协议规则或外部资料出处。
- 临时方案的退出条件。

推荐：

```java
// 兼容 2025-12 前的旧客户端：未传 privacyType 时按公开处理。
if (privacyType == null) {
    return PostPrivacyType.PUBLIC;
}
```

不推荐：

```java
// 判断 privacyType 是否为空
if (privacyType == null) {
    return PostPrivacyType.PUBLIC;
}
```

判断标准：

如果删除注释后，代码不知道“做什么”，应优先重构代码；如果删除注释后，代码知道“做什么”但不知道“为什么”，注释通常有价值。

`TODO` 不要只写人名或一句“以后优化”。最好带 issue、日期、触发条件或删除条件，让后来的人知道它为什么存在、什么时候可以处理。

## 测试也是上下文文档

测试名和测试数据能保存业务规则。

反例：

```java
@Test
void test1() {
}
```

改进：

```java
@Test
void shouldRejectRefundWhenOrderHasNotBeenPaid() {
}
```

测试应保存：

- 输入条件。
- 业务预期。
- 边界规则。
- 曾经发生过的问题。
- 状态流转约束。

对复杂业务，测试往往比注释更可靠，因为它能随着代码执行。

## 边界处要显式保存上下文

上下文最容易在系统边界丢失：

- Controller 入参。
- 消息队列 payload。
- 第三方回调。
- 定时任务参数。
- 数据库状态字段。
- 缓存 key。
- 日志。

建议：

- 入参 DTO 字段名写清业务角色。
- 消息体包含业务主键、事件类型、发生时间和幂等键。
- 第三方回调先转换为内部命令对象。
- 缓存 key 包含业务名和维度。
- 日志包含定位所需最小上下文。

边界处先把外部语言翻译成内部语言。比如第三方回调里的字段名、状态码和签名结果，不要直接在业务流程里到处传播，先转换成内部 command / event / result 对象。

## 常见反模式

| 反模式 | 问题 | 优先改法 |
|--------|------|----------|
| 魔法值 | 读不出业务含义 | 常量、枚举、值对象 |
| 长参数列表 | 参数关系和顺序不清楚 | 参数对象、命令对象 |
| 裸布尔参数 | 调用处不知道开关含义 | 枚举、选项对象、具名方法 |
| `process` / `handle` | 方法意图模糊 | 业务动作命名 |
| 散落 if | 规则入口不清楚 | Policy、Strategy、领域方法 |
| 翻译型注释 | 注释替代码解释 what | 改名、提取方法、提取变量 |
| 数据库值直穿业务 | 存储细节污染业务 | 枚举转换、TypeHandler、Converter |
| 日志只有 `error` | 无法定位问题 | 加业务主键和动作上下文 |

## Review 检查清单

- 是否出现新的魔法值。
- 多个同类型参数是否容易传错。
- 布尔参数在调用处是否可读。
- 方法名是否表达业务动作。
- 状态、类型、渠道、权限是否枚举化。
- 复杂条件是否有具名变量或规则对象。
- 业务规则是否散落在多个 if 中。
- 注释是否解释 why，而不是翻译 what。
- 测试名是否保存业务条件和预期。
- 日志是否保存定位问题所需上下文。

## 参考

- Java 编码规范: java-coding-standard.md
- Google Java Style Guide: https://google.github.io/styleguide/javaguide.html
- Java Language Specification, Names: https://docs.oracle.com/en/java/javase/26/docs/specs/jls/jls-6.html
- Martin Fowler Refactoring Catalog, Replace Magic Literal: https://refactoring.com/catalog/replaceMagicLiteral.html
- Martin Fowler Refactoring Catalog, Introduce Parameter Object: https://refactoring.com/catalog/introduceParameterObject.html
- Martin Fowler Refactoring Catalog, Extract Variable: https://refactoring.com/catalog/extractVariable.html
- Martin Fowler Refactoring Catalog, Remove Flag Argument: https://refactoring.com/catalog/removeFlagArgument.html
- DDD Reference by Eric Evans: https://www.domainlanguage.com/ddd/reference/

