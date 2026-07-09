# 部署与试运行设计记录

本文记录 SignalBrief 第一版部署和内网试运行方案。它不是逐条命令手册，具体执行步骤后续放在 `docs/deployment.md`；本文只沉淀部署边界、关键决策和风险控制原则。

## 背景

当前项目已经具备 RSS 入库、运行记录、Markdown 简报草稿、AI 摘要、手动归档、手动邮件发送、归档查询和邮件记录查询能力。核心链路已经完成一次阿里云内网试运行，并使用真实 AI Provider 验证过摘要生成和归档链路；真实 SMTP 投递和自动化部署仍需要继续按受控步骤验证。

因此部署侧下一步重点是把已验证的手工步骤沉淀为可重复、可观察、可回滚的流程，并在 CI 稳定后再补充手动触发的 CD workflow。

## 第一版部署目标

第一版部署面向个人内网试运行，不面向公网开放服务。目标是：

- 应用可以在阿里云主机上稳定启动。
- PostgreSQL 数据可以跨应用升级和主机重启保留。
- Flyway 迁移可以在启动时自动执行，并在迁移失败时阻止应用继续运行。
- RSS 抓取、AI 摘要和邮件发送默认不自动执行。
- 内部 API 只在明确需要手动验证时开启，并避免暴露到公网。
- 升级前可以备份数据库，升级失败后可以回滚应用 jar 和配置。

## 推荐部署形态

第一版选择：

```text
Spring Boot executable jar + systemd + Docker Compose PostgreSQL
```

理由：

- 项目当前已有 Maven 构建和 `compose.yaml` 中的 PostgreSQL 服务，先复用现有形态。
- 应用还没有 Dockerfile、镜像仓库和镜像发布流水线，直接引入完整容器化会扩大本轮范围。
- `systemd` 适合管理单个长期运行的 Java 服务，能提供开机自启、失败重启、日志归集和状态检查。
- PostgreSQL 用 Docker Compose 管理，可以明确 volume 持久化边界，便于本地和部署环境保持相近。

本轮不引入 Nginx、HTTPS、公网域名、多实例部署、蓝绿发布或 Kubernetes。这些能力在个人试运行阶段不是瓶颈。

## 目录与配置边界

部署目录建议使用：

```text
/opt/signal-brief
```

目录职责：

- `app/`：保存当前运行的 jar。
- `releases/`：保存历史 jar，便于回滚。
- `config/`：保存包外 `application-prod.yaml` 等生产应用配置，不提交到 Git。
- `backup/`：保存数据库备份文件。
- `compose.yaml`：只管理 PostgreSQL。

生产 profile 必须显式设置：

```env
SPRING_PROFILES_ACTIVE=prod
SPRING_DOCKER_COMPOSE_ENABLED=false
```

配置分层和环境变量命名规则统一沉淀到 [配置约定](../configuration.md)。`application.yaml` 中 datasource 保留注释示例，并提供默认端口 `8080`；部署环境当前通过 `/opt/signal-brief/config/application-prod.yaml` 提供 datasource、功能开关、AI 和 SMTP 等包外配置。生产 datasource 缺失时应在 DataSource、Flyway 或 MyBatis 初始化阶段失败，避免误用本地开发库。部署根目录 `.env` 只服务 Docker Compose PostgreSQL，不混入 Spring 应用配置。

## 副作用开关

首次部署时所有可能访问外部系统或产生投递行为的能力都保持关闭：

```env
SIGNAL_BRIEF_INGESTION_ENABLED=false
SIGNAL_BRIEF_AI_SUMMARY_ENABLED=false
SIGNAL_BRIEF_MAIL_ENABLED=false
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
```

内部 API 可以在内网试运行时临时开启：

```env
SIGNAL_BRIEF_INTERNAL_API_ENABLED=true
```

但该接口不能直接暴露到公网。原因是内部 API 能触发 RSS 抓取、AI 摘要、归档和邮件发送，属于操作入口，不是公开业务 API。

## 试运行顺序

首次试运行按最小副作用逐步推进：

1. 只启动 PostgreSQL，确认数据卷、端口和健康检查正常。
2. 启动应用，确认 `prod` profile、datasource、Flyway 和基础 Bean 加载正常。
3. 开启内部 API，手动触发 RSS 入库，确认文章、运行记录和源级明细入库。
4. 生成 Markdown 简报草稿，确认候选文章窗口、分类和链接结构正确。
5. 配置真实 AI Provider 后开启 AI 摘要，只验证一次手动生成。
6. 确认 AI 摘要内容质量后执行归档。
7. 配置 SMTP 后开启邮件发送，只对测试收件人投递。
8. 查询归档和邮件记录，确认成功与失败状态都能追踪。

RSS 定时任务在试运行稳定前继续关闭。只有当手动链路稳定、数据库备份可用、AI 和 SMTP 配置确认后，才考虑开启 `SIGNAL_BRIEF_INGESTION_ENABLED=true`。

## 试运行结论

2026-07-08 已按部署手册完成一次阿里云内网试运行部署。该结果说明第一版 `executable jar + systemd + Docker Compose PostgreSQL` 形态可以支撑个人内网试运行，部署文档后续应继续沉淀真实试运行暴露的问题，而不是记录服务器密钥、真实密码或个人邮箱授权码。

首次部署完成后，后续重点从“让系统跑起来”转为：

- 基于真实 AI Provider 试运行结果继续调整摘要质量、超时和失败记录。
- 继续观察 RSS / Atom 内容提取质量，必要时进入文章内容增强模块。
- 建立抓取失败告警和源级健康状态。
- 在备份可验证后，再逐步开启 RSS 定时、自动归档和邮件发送。

## 数据持久化与备份

PostgreSQL 必须使用命名 volume 或明确的宿主机挂载目录，不能依赖容器临时文件系统。升级应用前先执行数据库备份，至少覆盖以下场景：

- Flyway 新迁移上线前。
- 修改 datasource 或 PostgreSQL 容器配置前。
- 开启定时任务、AI 摘要或邮件发送前。

备份优先使用 `pg_dump` 导出逻辑备份。恢复流程需要在文档中给出可执行命令，并明确恢复前应停止应用，避免恢复过程中继续写入。

## 升级与回滚原则

应用升级只替换 jar 和环境配置，不在同一步骤中修改数据库数据。包含数据库迁移的升级需要先备份，再启动新 jar 触发 Flyway。

回滚分两类：

- 仅应用代码异常：停止服务，切回上一版 jar，重启服务。
- Flyway 已执行且迁移不兼容：不能只回滚 jar，需要按备份恢复数据库，或编写向前修复迁移。

Flyway 已经应用到数据库的版本脚本不要修改。后续结构变化必须新增迁移文件。

## 运维观测

第一版不引入完整监控系统，但部署文档需要覆盖最基本的排查入口：

- `systemctl status signal-brief` 查看服务状态。
- `journalctl -u signal-brief` 查看应用日志。
- `docker compose ps` 查看 PostgreSQL 容器状态。
- 查询 RSS 运行记录、简报归档和邮件记录确认业务状态。

日志中不能输出真实 API key、SMTP 密码或数据库密码。错误摘要应保留异常类型、HTTP 状态和短消息，避免泄漏密钥。

## 后续方向

部署手册完成并经过一次内网试运行后，再考虑以下能力：

- Dockerfile 和镜像化发布。
- 自动化部署脚本。
- CI/CD 中的 CD 阶段：在 CI 验证通过后提供手动触发的受控部署入口，后续再评估标签发布或 `main` 合并后自动部署。
- RSS 源连续失败告警。
- 定时自动生成、归档和发送。
- HTML 邮件和邮件失败重试。

这些能力都应基于试运行反馈分批推进，不和第一版部署混在一起。
