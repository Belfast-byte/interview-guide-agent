# 当前服务器生产部署

生产环境使用仓库根目录的 `.env.production` 和 `docker-compose.prod.yml`。

## 启动

```bash
sudo docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
sudo docker compose --env-file .env.production -f docker-compose.prod.yml ps
```

当前通过 `https://interviewpilot.me` 访问。Oracle Cloud Security List / NSG 必须允许 TCP 80/443；本机防火墙也需允许 80/443。

## 查看日志

```bash
sudo docker compose --env-file .env.production -f docker-compose.prod.yml logs -f --tail=200 app
```

## 停止

```bash
sudo docker compose --env-file .env.production -f docker-compose.prod.yml down
```

不要使用 `down -v`，它会删除数据库、Redis、对象存储和应用配置卷。

## 备份

```bash
sudo BACKUP_ROOT=/var/backups/interview-guide ./scripts/backup-production.sh
```

备份包含 PostgreSQL、MinIO、应用 Provider 配置和恢复所需的生产环境变量。备份目录含密钥，必须限制访问并复制到服务器之外。

## 域名和 HTTPS

- `interviewpilot.me` 与 `www.interviewpilot.me` 的 DNS 记录指向服务器公网 IP。
- `deploy/Caddyfile` 配置站点域名，Caddy 自动申请和续期证书。
- `.env.production` 的 `CORS_ALLOWED_ORIGINS` 必须包含实际前端来源。
- Caddy 对外开放 TCP 80/443 和 UDP 443。
