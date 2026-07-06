# 事务边界与状态机实践

本文记录本项目对 Spring 事务、MyBatis 写入和状态推进的使用原则。目标不是少用事务，而是把事务放在真正需要原子性的边界上，避免“只要有写操作就加大事务”带来的隐藏问题。

## 官方依据

Spring `@Transactional` 表示方法需要事务语义，默认传播行为是 `PROPAGATION_REQUIRED`，默认读写事务，默认对 `RuntimeException` 和 `Error` 回滚，对 checked exception 不回滚。Spring 默认基于代理拦截事务，同类内部方法调用不会经过代理。

MyBatis-Spring 接入 Spring 事务后，同一个 Spring 事务内会创建并复用一个 `SqlSession`，事务完成时提交或回滚；事务外调用 Mapper 方法会自动提交。MyBatis 默认本地缓存作用域是 `SESSION`，同一 `SqlSession` 内的查询会被缓存；`select` 默认不清缓存，`insert`、`update`、`delete` 默认清缓存。

这些规则说明两件事：

- 事务是显式业务边界，不是“写 SQL 就自动需要”的装饰。
- MyBatis 的 `SqlSession`、缓存和提交行为会随 Spring 事务边界变化。

## 事务解决什么问题

事务主要解决数据库内部的一致性问题。适合加事务的场景是：多个数据库变更必须作为一个整体成功或失败，中间任何一步失败都不能留下部分结果。

典型例子：

```java
@Transactional
public void createOrder(CreateOrderCommand command) {
    orderMapper.insertOrder(command.order());
    inventoryMapper.decreaseStock(command.skuId(), command.quantity());
    paymentMapper.insertPendingPayment(command.payment());
}
```

如果订单创建成功但库存没扣，或库存扣了但待支付记录没写，业务数据会不一致。这里所有操作都是数据库内部写入，耗时短，失败后也不希望保留中间状态，因此应该放在一个事务中。

注意：这里的 `insertPendingPayment` 只表示创建本地待支付记录，不表示已经请求外部支付渠道。

本项目后续可能需要事务的例子：

```java
@Transactional
public void createRetryTaskAndMarkDeliveryFailed(Long deliveryId, String reason) {
    deliveryMapper.markFailed(deliveryId, reason);
    retryTaskMapper.insertMailRetryTask(deliveryId);
}
```

如果“标记失败”和“创建重试任务”必须同时成立，就应该使用事务。

## 什么不适合放进大事务

不要把外部调用包进数据库大事务，例如 HTTP、AI Provider、SMTP、文件 I/O。外部调用不可由数据库回滚控制，而且耗时不稳定，会长时间占用连接和锁。

不推荐：

```java
@Transactional
public BriefGeneration archiveAiSummary(Instant startInclusive, Instant endExclusive) {
    Long id = mapper.insertGenerating(startInclusive, endExclusive, draftMarkdown);

    String summary = aiSummaryService.summarizeMarkdown(draftMarkdown);

    mapper.markSuccess(id, summary, clock.instant());
    return mapper.findById(id).orElseThrow();
}
```

问题是：AI 调用可能慢、超时或失败。事务会在等待外部服务时持续占用数据库连接。如果 AI 实际调用成功但后续数据库提交失败，数据库无法回滚外部世界已经发生的事实。

邮件发送同理。不能依赖数据库事务撤销一封已经发出的邮件。

订单支付也应拆开处理。外部支付请求不能放进创建订单的大事务：

```java
@Transactional
public Long createPendingOrder(CreateOrderCommand command) {
    Long orderId = orderMapper.insertOrder(command.order(), OrderStatus.PENDING_PAYMENT);
    inventoryMapper.reserveStock(command.skuId(), command.quantity());
    paymentMapper.insertPendingPayment(orderId, command.amount());
    return orderId;
}
```

事务提交后，再调用支付渠道：

```java
public void requestPayment(Long orderId) {
    Order order = orderMapper.findById(orderId).orElseThrow();

    PaymentRequestResult result = paymentClient.createPayment(order);

    paymentMapper.markRequested(orderId, result.providerTradeNo());
}
```

支付成功通常由回调、主动查询或补偿任务确认，再用短事务推进本地状态：

```java
@Transactional
public void handlePaymentSucceeded(String providerTradeNo) {
    Payment payment = paymentMapper.findByProviderTradeNo(providerTradeNo).orElseThrow();

    int updated = paymentMapper.markSucceededIfRequested(payment.id());
    if (updated != 1) {
        return;
    }

    orderMapper.markPaid(payment.orderId());
}
```

支付失败或超时时，也用短事务取消订单并释放预占库存：

```java
@Transactional
public void handlePaymentFailed(String providerTradeNo, String reason) {
    Payment payment = paymentMapper.findByProviderTradeNo(providerTradeNo).orElseThrow();

    int updated = paymentMapper.markFailedIfRequested(payment.id(), reason);
    if (updated != 1) {
        return;
    }

    orderMapper.cancel(payment.orderId());
    inventoryMapper.releaseReservedStock(payment.orderId());
}
```

这种拆解依赖状态、幂等键和条件更新保证正确性：订单可以从 `PENDING_PAYMENT` 推进到 `PAID` 或 `CANCELLED`；支付单可以从 `PENDING` 推进到 `REQUESTED`、`SUCCEEDED` 或 `FAILED`。支付渠道流水号应有唯一约束，重复回调时条件更新返回 `0`，表示已经处理过，可以安全忽略。

## 状态机如何拆解事务

当流程包含外部调用，正确性通常靠“状态机 + 短数据库操作”维护，而不是一个大事务维护。

简报归档当前采用：

```text
INSERT brief_generation status=GENERATING
-> 调用 AI Provider
-> 成功：UPDATE status=SUCCESS, summary_markdown=...
-> 失败：UPDATE status=FAILED, error_summary=...
```

这里允许外部观察到 `GENERATING`、`SUCCESS`、`FAILED`。这不是脏数据，而是业务状态。即使 AI 调用失败，也能保留失败记录，便于审计和补偿。

邮件发送当前采用：

```text
INSERT brief_mail_delivery status=PENDING
-> 调用 SMTP
-> 成功：UPDATE status=SENT, sent_at=...
-> 失败：UPDATE status=FAILED, error_summary=...
```

这样做的好处是：

- 每个收件人的发送结果可独立审计。
- 单个收件人失败不影响其他收件人。
- 失败状态可以被后续重试队列消费。
- 数据库不会在 SMTP 调用期间持有长事务。

状态机的核心要求是：状态必须单向推进，更新语句必须带当前状态条件。例如本项目的状态更新只允许从 `GENERATING` 转出，邮件发送只允许从 `PENDING` 转出：

```sql
UPDATE brief_generation
SET status = 'SUCCESS'
WHERE id = #{id}
  AND status = 'GENERATING'
```

这种条件更新可以避免重复完成、重复失败或并发覆盖。调用方必须检查更新行数，`1` 表示状态转换成功，`0` 表示状态已经被其他流程推进或当前状态不允许转换。

## 判断规则

该加事务：

- 多张表或多条记录必须强一致。
- 中间失败不能留下部分结果。
- 全部操作都是数据库内部操作，耗时短。
- 需要数据库锁、唯一约束或条件更新共同保证并发安全。

不该加大事务：

- 中间包含 HTTP、AI Provider、SMTP、文件 I/O 等外部调用。
- 需要保留失败记录和中间状态。
- 每一步都是状态推进，允许看到 `GENERATING`、`PENDING`、`FAILED`。
- 事务会长时间占用数据库连接、锁或 MyBatis `SqlSession`。

可以折中使用短事务：

- 在外部调用前，用一个短事务创建一组待处理记录。
- 外部调用结束后，用另一个短事务推进状态。
- 多条数据库状态更新必须同时成立时，再把这些数据库更新放进同一个事务。

## 本项目约定

- 不因为“有写操作”自动添加 `@Transactional`。
- `@Transactional` 优先放在 Service 层公开方法上，不放在 Mapper 或 Controller 上。
- 不把 RSS 抓取、AI 调用、SMTP 发送放入长事务。
- 状态表更新必须使用条件更新保护状态流转，并检查影响行数。
- 需要保留失败记录的流程，优先使用 `PENDING` / `GENERATING` / `FAILED` / `SUCCESS` 这类状态推进模型。
- 使用 PostgreSQL `INSERT ... RETURNING id` 且通过 MyBatis `@Select` 接收返回值时，必须显式配置 `flushCache = TRUE`，因为它实际是写操作。

## 审查清单

新增写入流程时先问：

1. 如果第 2 步失败，第 1 步留下来是不是错误数据？
2. 这个流程里有没有 HTTP、SMTP、AI、文件 I/O 等外部副作用？
3. 失败是否需要被查询、重试或审计？
4. 状态是否可以拆成 `PENDING`、`RUNNING`、`SUCCESS`、`FAILED`？
5. 并发执行时，是否有唯一约束、条件更新或锁保护？
6. 如果加事务，事务期间会不会等待外部服务？

如果答案显示“需要保留状态、包含外部调用、允许后续补偿”，优先状态机拆解；如果答案显示“全是数据库内部操作，部分成功就是错误”，优先事务。

## 参考资料

- [Spring Framework Reference：Using `@Transactional`](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)
- [MyBatis-Spring Reference：Transactions](https://mybatis.org/spring/transactions.html)
- [MyBatis 3 Reference：Mapper XML Files](https://mybatis.org/mybatis-3/sqlmap-xml.html)
- [MyBatis 3 Reference：Configuration](https://mybatis.org/mybatis-3/configuration.html)
