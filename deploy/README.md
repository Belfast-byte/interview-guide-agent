# 当前服务器生产部署

生产环境使用仓库根目录的 `.env.production` 和 `docker-compose.prod.yml`。

## 启动

```bash
sudo docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
sudo docker compose --env-file .env.production -f docker-compose.prod.yml ps
```

当前通过 `https://141433.xyz` 访问。Cloudflare SSL/TLS 模式必须使用 `Full (strict)`；本机防火墙已允许 80/443。

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

## Cloudflare 配置

DNS A 记录指向 `129.146.62.132` 并开启代理。Cloudflare 控制台中设置：

- SSL/TLS 加密模式：`Full (strict)`
- WebSockets：开启
- `/api/*`、`/ws/*`、`/actuator/*`：绕过缓存

Caddy 自动申请并续期源站证书。
