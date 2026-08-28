# Progress Log

## Context Recovery Block

- **Current milestone**: #6 — 执行最终纯净性审计并提交
- **Current status**: IN_PROGRESS
- **Last completed**: T06 / commit `a5554ea`
- **Current artifact**: `docs/design_spec/35-memory-three-layer-tickets.md` T07
- **Key context**: 旧 Topic/Claim、CandidateClaim LLM 链、旧表和 LEGACY enrichment 已删除；三个场景测试通过。
- **Known issues**: 当前环境没有 Docker/psql，无法执行真实 PostgreSQL 空库 Flyway；迁移链已通过依赖和旧表引用静态审计。
- **Next action**: 完成 diff、旧引用、测试覆盖与工作树归属审计，提交 T07。

## Audit

- `candidate_memory_topics` / `candidate_memory_claims` 只有写侧和旧历史读取接口，无 v4 消费者，已连同实体、Repository、schema 与测试删除。
- CandidateClaim extraction 只为旧 claim 表生产数据，已删除 service、Spring AI generator 和两个 Prompt。
- `V20260820` 保留仍被 session/plan 使用的 candidate/focus 字段并改为准确名称；`V20260822` 保留 session tenant 与 MCP audit。
- `LEGACY_UNENRICHED` 仅服务历史回填，与开发库重建原则冲突，已从 enum、约束和测试删除。

## Delivery

- 正式 Redis persistence 场景验证换场景保持 TargetEnvelope，并只用当前 Episode 形成 Evaluation contribution。
- 练习场景验证 scope 不扩张、完整诊断可见、提示完成写为 ASSISTED 且 transfer 等待正式重评。
- ASK/CALL_TOOL 测试覆盖 PLANNED 执行、SUCCEEDED 只补 Patch、超时 EXECUTING 复用幂等键。
- 后端全集按三个互斥分片通过，分片耗时分别 26 秒、29 秒、16 秒；单个命令均受 60 秒硬超时约束。
- 前端 `pnpm run build` 通过；保留既有 CSS minifier `:where()` 警告，不影响构建结果。
- 单任务全量命令在 60 秒时进入收尾但未汇总，因此未把超时运行记为通过。
