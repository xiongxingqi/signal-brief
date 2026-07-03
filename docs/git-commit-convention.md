# Git 提交规范
## 目标

采用约定式提交，主要是为了做到这几件事：

- 让提交历史更容易阅读
- 让功能、新增修复、破坏性变更一眼可见
- 便于自动生成变更日志
- 便于结合语义化版本进行发布
- 降低团队协作时理解上下文的成本

## 适用范围

本规范适用于所有普通代码提交、文档提交、构建脚本提交和工程配置提交。

不建议把多个不相关改动塞进同一个提交；一个提交应尽量只表达一件事。

## 提交格式

约定式提交的基本结构如下：

```text
<type>[optional scope][!]: <description>

[optional body]

[optional footer(s)]
```

### 结构说明

- `type`：提交类型，必填
- `scope`：影响范围，可选
- `!`：表示破坏性变更，可选
- `description`：简短说明，必填
- `body`：详细说明，可选
- `footer`：补充信息，可选，比如关联 issue、破坏性变更说明

## 官方规范中的关键要求

以下内容是对 Conventional Commits 规范核心要求的整理：

- 提交信息必须以 `type` 开头
- `type` 后面可以带可选的 `scope`
- `scope` 放在圆括号中，通常是代码库中的某个模块名
- `type` 或 `scope` 后必须接 `: `，也就是英文冒号加空格
- `description` 必须紧跟在 `: ` 之后
- 如果要写 `body`，需要与标题之间空一行
- 如果要写 `footer`，需要与 `body` 之间空一行；如果没有 `body`，则与标题之间空一行
- 破坏性变更必须通过 `!`、`BREAKING CHANGE:` 或 `BREAKING-CHANGE:` 明确标识
- footer 的普通 token 建议使用类似 Git trailer 的写法，例如 `Refs:`、`Closes:`；除 `BREAKING CHANGE` 外，不建议在 token 中使用空格

## 提交类型

### 规范中明确要求的类型

`Conventional Commits` 明确规定了两个具有语义含义的类型：

- `feat`：新增功能
- `fix`：修复缺陷



### 推荐使用的常见类型

除了 `feat` 和 `fix`，规范也允许使用其他类型。下面是一组常见且实用的团队约定：

- `docs`：文档变更
- `style`：代码格式调整，不影响逻辑，比如空格、缩进、分号、格式化
- `refactor`：重构代码，不新增功能，也不修复缺陷
- `perf`：性能优化
- `test`：测试相关变更
- `build`：构建系统或依赖管理相关变更
- `ci`：CI/CD 配置变更
- `chore`：杂项维护，不属于功能、修复、文档、测试等
- `revert`：回滚某次提交

## Scope 约定

`scope` 用来说明本次提交主要影响的范围。它不是必须的，但在多人协作或单仓多模块项目中非常有帮助。

推荐写法：

- `feat(auth): add login rate limit`
- `fix(api): handle empty payload`
- `docs(readme): update installation guide`

推荐把 `scope` 写成清晰、稳定的模块名，例如：

- `api`
- `web`
- `ui`
- `auth`
- `docs`
- `build`
- `deps`

如果一次提交跨多个模块，但仍然属于同一件事，可以不写 `scope`，避免硬塞多个范围。

## Description 书写要求

`description` 是提交标题中最重要的部分，应该让人不打开 diff 也能大致知道这次提交在做什么。

推荐遵循以下约定：

- 简短明确，直接描述这次改动
- 聚焦结果，不写无意义短语
- 不以句号结尾
- 尽量避免“update stuff”“fix bug”这类过于模糊的表述
- 团队内部统一语言即可，中文或英文都可以，但同一仓库最好保持一致

推荐示例：

- `feat(auth): add password reset flow`
- `fix(upload): prevent crash when file size is zero`
- `docs: clarify local setup steps`
- `refactor(editor): split toolbar state logic`

不推荐示例：

- `fix: fix bug`
- `feat: update`
- `chore: modify code`
- `docs: some changes`

## Body 书写建议

如果标题已经足够清楚，可以不写正文；如果改动背景比较复杂，建议补充 `body`。

`body` 适合说明：

- 为什么要改
- 改了什么
- 有哪些取舍
- 是否有兼容性影响

示例：

```text
fix(api): prevent duplicate order creation

Add an idempotency check before inserting new orders.
This avoids duplicate records when the client retries the request.
```

## Footer 书写建议

`footer` 适合放关联信息，比如：

- 关联 Issue
- 关闭 Issue
- 审阅信息
- 破坏性变更说明

示例：

```text
feat(payment): support refund webhook

Refs: #102
```

```text
fix(auth): handle expired refresh token

Closes: #87
```

## 破坏性变更

如果提交包含不兼容变更，必须明确标识。常见写法有两种。

### 方式一：在标题中使用 `!`

```text
feat(api)!: remove legacy user endpoint
```

### 方式二：在 footer 中使用 `BREAKING CHANGE:`

```text
feat(api): remove legacy user endpoint

BREAKING CHANGE: /v1/users has been removed; use /v2/users instead.
```

也可以写成 `BREAKING-CHANGE:`：

```text
feat(api): remove legacy user endpoint

BREAKING-CHANGE: /v1/users has been removed; use /v2/users instead.
```

如果改动影响很大，推荐同时写清楚迁移说明，让后续维护者和使用方更容易处理。


## 推荐模板

### 最短可用模板

```text
<type>: <description>
```

### 常规模板

```text
<type>(<scope>): <description>
```

### 完整模板

```text
<type>(<scope>)!: <description>

<body>

<footer>
```

## 常用示例

```text
feat(editor): add markdown preview panel
```

```text
fix(login): prevent empty password submission
```

```text
docs: add git workflow guide
```

```text
refactor(storage): simplify cache invalidation logic
```

```text
perf(search): reduce duplicate database queries
```

```text
chore(deps): upgrade vite to v7
```

```text
revert: revert "feat(editor): add markdown preview panel"
```

## 简明规则

提交时优先按下面的顺序判断：

1. 这是新增功能吗？是的话优先用 `feat`
2. 这是修复问题吗？是的话优先用 `fix`
3. 这是文档、测试、重构、构建或杂项吗？按对应类型选择
4. 这次改动是否会造成不兼容？如果会，补上 `!` 或 `BREAKING CHANGE:`
5. 标题是否足够清楚，别人不看代码也能知道改了什么？

## 参考

- Conventional Commits 1.0.0: https://www.conventionalcommits.org/en/v1.0.0/
- Conventional Commits 1.0.0（中文）：https://www.conventionalcommits.org/zh-hans/v1.0.0/
- [语义化版本规范](../versioning/semantic-versioning.md)

