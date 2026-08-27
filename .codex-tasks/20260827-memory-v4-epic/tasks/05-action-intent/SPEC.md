# Task Spec: memory-v4-action-intent

## Goal

让 ASK/CALL_TOOL 在产生外部副作用前持久化 ActionIntent，并以同一 intent 和幂等键恢复未完成动作。

## In Scope

- ActionIntent、ASK/CALL_TOOL payload、状态机和 PostgreSQL 存储。
- Intent + Pending Patch、结果 + SUCCEEDED、ActionResult Patch + APPLIED 三段短事务。
- PLANNED、EXECUTING、SUCCEEDED 的恢复；FAILED 显式保留。
- ASK 不重复展示，CALL_TOOL 不重复执行。

## Out of Scope

- Episode 生成和召回。
- Semantic 聚合与消费。
- 自动重试、兼容旧动作或迁移旧数据。

## Success Criteria

- 外部动作执行前可从 PostgreSQL 读到对应 Intent 与 ACTION_PENDING WorkState。
- 合法状态迁移由代码裁决，失败不吞错。
- 恢复复用原 intent、payload 和 idempotency key。
- 同一 session 同时只有一个未完成 Intent。
