# 部署与试运行手册

本文用于 SignalBrief 第一版阿里云内网试运行部署。当前推荐形态是：

```text
Spring Boot executable jar + systemd + Docker Compose PostgreSQL
```

部署设计背景见 [docs/records/deployment-runbook.md](records/deployment-runbook.md)。本文只保存可共享的部署步骤和安全示例，不保存真实 `.env`、数据库密码、SMTP 密码或 AI Provider API key。

## 部署目标

第一版部署只服务个人内网试运行：

- 应用使用 `prod` profile 启动。
- PostgreSQL 使用 Docker Compose 管理，并通过 volume 持久化数据。
- 应用使用 `systemd` 托管，支持启动、停止、重启、日志查看和失败重启。
- RSS 定时、AI 摘要、邮件发送和 OpenAPI 默认关闭。
- 内部 API 只在手动验证时开启，不直接暴露到公网。

## 部署前检查

本地连接阿里云主机时，当前环境需要绕过异常的全局 SSH 配置：

```bash
ssh -F ~/.ssh/config aliyun
```

只读检查命令：

```bash
ssh -F ~/.ssh/config aliyun 'id'
ssh -F ~/.ssh/config aliyun 'cat /etc/os-release'
ssh -F ~/.ssh/config aliyun 'command -v docker'
ssh -F ~/.ssh/config aliyun 'docker compose version'
ssh -F ~/.ssh/config aliyun 'command -v java'
ssh -F ~/.ssh/config aliyun 'java -version'
ssh -F ~/.ssh/config aliyun 'df -h'
ssh -F ~/.ssh/config aliyun 'free -h'
ssh -F ~/.ssh/config aliyun 'ss -lntp'
```

2026-07-07 首次部署前对 `aliyun` 的盘点结论：

- 系统为 Ubuntu 26.04 LTS，`systemd` 可用。
- 登录用户为 `celestrong`，具备 `sudo` 组权限。
- 当时尚未安装 Docker 和 Java；首次试运行已完成安装。新机器部署时仍需按上面的检查命令确认。
- 根分区约 35G 可用。
- 内存约 1.6GiB，未配置 swap。
- 当前仅监听 SSH 端口。

新机器部署前需要先安装 Docker Engine、Docker Compose plugin 和 JDK 25。内存较小时建议配置 swap，避免 Maven 构建、Java 启动或数据库运行时被系统终止。

安装完成后必须确认：

```bash
docker version
docker compose version
java -version
```

## 推荐目录结构

建议部署目录：

```text
/opt/signal-brief
├── .env
├── app/
├── backup/
├── config/
│   └── application-prod.yaml
├── releases/
└── compose.yaml
```

目录职责：

- `.env`：Docker Compose 使用的 PostgreSQL 变量，同时供备份、恢复命令读取；只存在服务器本地，不提交 Git。
- `app/`：保存当前运行的 jar，通常用软链接指向 `releases/` 中的具体版本。
- `releases/`：保存历史 jar，便于快速回滚。
- `config/application-prod.yaml`：Spring Boot 包外生产配置，保存 datasource、功能开关、AI、SMTP 等应用运行配置；只存在服务器本地，不提交 Git。
- `backup/`：保存数据库备份。
- `compose.yaml`：管理 PostgreSQL 容器，默认读取同目录 `.env`。

创建目录：

```bash
sudo mkdir -p /opt/signal-brief/{app,releases,config,backup}
sudo chown -R celestrong:celestrong /opt/signal-brief
```

## Docker Compose 环境变量

PostgreSQL 容器变量放在部署根目录：

```text
/opt/signal-brief/.env
```

示例：

```env
POSTGRES_DB=signal_brief
POSTGRES_USER=signal_brief
POSTGRES_PASSWORD=replace-with-strong-database-password
POSTGRES_PORT=5432
```

`.env` 由 Docker Compose 自动读取，也会被备份、恢复命令临时加载。不要把 `SPRING_*` 或 `SIGNAL_BRIEF_*` 应用配置混入这里，避免容器基础设施配置和 Spring 应用配置混在一起。

## Spring 包外配置

生产应用配置放在：

```text
/opt/signal-brief/config/application-prod.yaml
```

示例：

```yaml
server:
  port: 8080

spring:
  docker:
    compose:
      enabled: false
  datasource:
    url: jdbc:postgresql://localhost:5432/signal_brief
    username: signal_brief
    password: replace-with-strong-database-password

springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false

signal-brief:
  internal-api:
    enabled: false
  ingestion:
    enabled: false
  ai-summary:
    enabled: false
  mail:
    enabled: false
```

配置分层和命名规则统一见 [配置约定](configuration.md)。`application-prod.yaml` 是包外配置，优先级高于 jar 内配置；systemd 会通过 `SPRING_CONFIG_ADDITIONAL_LOCATION=file:/opt/signal-brief/config/` 显式指定该目录。这里的 datasource 用户名、密码和库名必须与 `/opt/signal-brief/.env` 中 PostgreSQL 容器配置保持一致。

开启 AI 摘要时再补充：

```yaml
signal-brief:
  ai-summary:
    enabled: true
    base-url: https://api.example.com
    api-key: replace-with-provider-api-key
    model: replace-with-provider-model
```

开启邮件发送时再补充：

```yaml
spring:
  mail:
    host: smtp.example.com
    port: 587
    username: noreply@example.com
    password: replace-with-smtp-password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

signal-brief:
  mail:
    enabled: true
    from: noreply@example.com
    recipients: reader@example.com
    subject-prefix: SignalBrief 技术半月报
```

个人邮箱或第一版试运行时，`spring.mail.username` 和 `signal-brief.mail.from` 建议使用同一个邮箱账号。只有当 SMTP 服务商明确支持已验证发件地址或域名代理发送时，才把两者配置为不同值。

密码、主题、API key 等值如果包含 YAML 特殊字符，应使用引号包裹，并在首次启动前单独验证加载结果。

配置文件权限建议：

```bash
chmod 600 /opt/signal-brief/.env /opt/signal-brief/config/application-prod.yaml
```

## PostgreSQL 启动与持久化

把仓库中的 `compose.yaml` 放到 `/opt/signal-brief/compose.yaml`。该文件使用 `postgres-data` 命名 volume 持久化数据库数据。

启动 PostgreSQL：

```bash
cd /opt/signal-brief
docker compose up -d postgres
docker compose ps
```

查看数据库容器日志：

```bash
cd /opt/signal-brief
docker compose logs -f postgres
```

应用的 JDBC URL 使用 `localhost:5432`，因此 PostgreSQL 容器需要按 `compose.yaml` 映射到宿主机本机回环地址。不要把数据库端口绑定到 `0.0.0.0` 或直接暴露到公网。

## 应用构建与发布

本地构建：

```bash
./mvnw -Dspring.docker.compose.enabled=false test
./mvnw package
```

构建产物：

```text
target/signal-brief-1.2.0-SNAPSHOT.jar
```

上传到服务器：

```bash
scp -F ~/.ssh/config target/signal-brief-1.2.0-SNAPSHOT.jar aliyun:/opt/signal-brief/releases/signal-brief-1.2.0-SNAPSHOT.jar
```

切换当前版本：

```bash
ln -sfn /opt/signal-brief/releases/signal-brief-1.2.0-SNAPSHOT.jar /opt/signal-brief/app/signal-brief.jar
```

## systemd 服务配置

创建服务文件：

```bash
sudo vim /etc/systemd/system/signal-brief.service
```

内容：

```ini
[Unit]
Description=SignalBrief personal newsletter service
After=network-online.target
Wants=network-online.target

[Service]
User=celestrong
Group=celestrong
WorkingDirectory=/opt/signal-brief
Environment=SPRING_PROFILES_ACTIVE=prod
Environment=SPRING_CONFIG_ADDITIONAL_LOCATION=file:/opt/signal-brief/config/
ExecStart=/usr/bin/java -jar /opt/signal-brief/app/signal-brief.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

加载并启动：

```bash
sudo systemctl daemon-reload
sudo systemctl enable signal-brief
sudo systemctl start signal-brief
```

## 启动、停止与日志

查看状态：

```bash
systemctl status signal-brief
```

查看日志：

```bash
journalctl -u signal-brief -n 200 --no-pager
journalctl -u signal-brief -f
```

重启应用：

```bash
sudo systemctl restart signal-brief
```

停止应用：

```bash
sudo systemctl stop signal-brief
```

如果启动失败，优先检查：

- systemd 中 `SPRING_PROFILES_ACTIVE=prod` 是否设置。
- `/opt/signal-brief/config/application-prod.yaml` 是否存在且服务用户可读。
- `spring.datasource.url`、`spring.datasource.username`、`spring.datasource.password` 是否存在，并与 `/opt/signal-brief/.env` 中 PostgreSQL 配置一致。
- PostgreSQL 是否健康。
- Flyway 是否因为迁移校验失败而阻止启动。
- `server.port` 是否被占用。

## 首次试运行流程

首次启动建议使用最小副作用配置：

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false

signal-brief:
  internal-api:
    enabled: false
  ingestion:
    enabled: false
  ai-summary:
    enabled: false
  mail:
    enabled: false
```

确认应用启动成功后，再临时开启内部 API：

```yaml
signal-brief:
  internal-api:
    enabled: true
```

重启应用后，从服务器本机或可信内网访问内部接口。阿里云安全组不要向公网开放 `8080`，需要远程操作时优先使用 SSH 登录服务器后执行本机 `curl`。

```bash
curl -X POST http://localhost:8080/internal/ingestions/rss
curl http://localhost:8080/internal/ingestions/rss/runs
```

按顺序验证：

1. 手动触发 RSS 入库，确认返回 `runId`。
2. 查询 RSS 运行记录，确认源级统计和失败摘要可见。
3. 生成 Markdown 简报草稿，确认分类、链接和时间窗口正确。
4. 配置真实 AI Provider 后开启 `signal-brief.ai-summary.enabled=true`，只手动生成一次 AI 摘要。
5. 摘要质量确认后执行归档。
6. 配置 SMTP 后开启 `signal-brief.mail.enabled=true`，先发送到测试收件人。
7. 查询简报归档和邮件记录，确认 `SUCCESS`、`SENT` 或 `FAILED` 状态可追踪。

RSS 定时任务在试运行稳定前保持关闭。只有当手动链路稳定、备份可用、AI 和 SMTP 配置确认后，才考虑开启：

```yaml
signal-brief:
  ingestion:
    enabled: true
```

## 备份与恢复

升级应用、修改 PostgreSQL 配置、开启定时任务、开启 AI 或开启邮件前，建议先备份数据库。

备份：

```bash
cd /opt/signal-brief
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "/opt/signal-brief/backup/signal-brief-$(date +%Y%m%d%H%M%S).sql"
```

恢复前先停止应用，避免恢复过程中继续写入：

```bash
sudo systemctl stop signal-brief
```

恢复到空库：

```bash
cd /opt/signal-brief
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < /opt/signal-brief/backup/backup-file.sql
```

恢复完成后再启动应用：

```bash
sudo systemctl start signal-brief
```

## 升级与回滚

升级流程：

1. 本地运行基础测试。
2. 本地执行 `./mvnw package`。
3. 上传新 jar 到 `/opt/signal-brief/releases/`。
4. 备份数据库。
5. 切换 `/opt/signal-brief/app/signal-brief.jar` 软链接。
6. 重启 `signal-brief` 服务。
7. 查看 `systemctl status` 和 `journalctl`。
8. 查询内部运行记录确认业务状态。

仅应用代码异常时，回滚上一版 jar：

```bash
sudo systemctl stop signal-brief
ln -sfn /opt/signal-brief/releases/previous-version.jar /opt/signal-brief/app/signal-brief.jar
sudo systemctl start signal-brief
```

如果新版本已经执行了 Flyway 迁移，不能简单回滚 jar。此时需要按备份恢复数据库，或继续编写向前修复迁移。已经应用到数据库的 Flyway 迁移脚本不要修改。

## 安全注意事项

- 不要提交服务器上的 `.env` 和 `config/application-prod.yaml`。
- 不要把内部 API 直接暴露到公网。
- 阿里云安全组默认只开放 SSH；如需开放应用端口，必须先确认内部 API 和 OpenAPI 都处于关闭状态。
- PostgreSQL 端口只绑定 `127.0.0.1`，不要向公网或内网直接开放数据库。
- OpenAPI / Swagger UI 默认关闭，只在可信网络内临时开启。
- 数据库、SMTP 和 AI Provider 密钥只放部署环境。
- 日志中不要输出完整密钥、密码或授权头。
- 首次试运行不要同时开启 RSS 定时、AI 和邮件，按一个能力一个能力验证。
