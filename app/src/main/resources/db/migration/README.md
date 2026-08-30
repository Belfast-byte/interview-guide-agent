# Flyway 数据库迁移

该目录存放 Flyway 自动执行的 PostgreSQL 数据库迁移脚本。应用启动时会按版本号执行 `V*.sql` 文件，并通过 `flyway_schema_history` 记录执行历史。

当前项目采用 Flyway 管理数据库 schema：

- `V1__init_schema.sql` 负责空库初始化。
- 后续 `V*.sql` 负责增量变更，必须保持幂等或可安全回放到对应版本状态。
- **已应用过的迁移文件禁止修改或删除**（Flyway 启动时校验 checksum，改动会导致 `Validate failed` 启动失败）。schema 变更一律新增 `V<新版本>__xxx.sql`；开发期确实要改未合并的迁移时，改完必须重置本地库（`docker compose -f docker-compose.dev.yml` 重建 postgres 数据卷）。
- Hibernate `ddl-auto` 使用 `validate`，只校验 schema，不负责建表或改表。
- Spring AI pgvector 表结构也由 Flyway 创建，`initialize-schema` 应保持 `false`。
- 测试环境使用 H2 + `ddl-auto: create-drop`，默认关闭 Flyway。

不要用 `psql` 直接执行本目录迁移脚本，否则 Flyway 无法记录执行历史，后续启动可能出现版本状态不一致。
