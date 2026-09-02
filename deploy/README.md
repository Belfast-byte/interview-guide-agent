# 当前服务器生产部署

生产环境使用仓库根目录的 `.env.production` 和 `docker-compose.prod.yml`。

## 启动

```bash
sudo docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
sudo docker compose --env-file .env.production -f docker-compose.prod.yml ps
```

当前通过 `http://129.146.62.132` 访问。Oracle Cloud Security List / NSG 还必须允许 TCP 80；本机防火墙已经允许 80/443。

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

## 切换域名和 HTTPS

1. 把域名 A 记录指向服务器公网 IP。
2. 将 `deploy/Caddyfile` 第一行 `:80` 改为实际域名。
3. 将 `.env.production` 的 `CORS_ALLOWED_ORIGINS` 改为 `https://实际域名`。
4. 在 `docker-compose.prod.yml` 的 Caddy 端口中增加 `443:443` 和 `443:443/udp`。
5. 重新执行启动命令，Caddy 会自动申请证书。
