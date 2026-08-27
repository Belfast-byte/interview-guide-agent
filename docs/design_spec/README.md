# Agent 技术规格

本目录由 Agent 维护，用于把 [`../design/`](../design/README.md) 中的意图翻译为可实施、可测试、可验收的技术文档。技术规格应记录状态和上游设计；与设计意图冲突时，先暴露差异，不直接改写设计。

新文档优先使用 `NN-topic-spec.md`；派生的执行资料使用 `NN-topic-plan.md`、`NN-topic-tickets.md` 或 `NN-topic-review-YYYY-MM-DD.md`。现有文件本次保留原名，避免在目录迁移之外再做批量重命名。

## 文档索引

| 文档 | 类型 | 当前状态 |
|---|---|---|
| [02-auth-permission.md](./02-auth-permission.md) | 认证与权限规格 | 已实施 |
| [10-text-interview.md](./10-text-interview.md) | 自适应文本面试实现设计 | 待评审 |
| [11-algorithm-interview.md](./11-algorithm-interview.md) | 算法面试与延迟执行规格 | 待评审 |
| [12-code-analysis-service.md](./12-code-analysis-service.md) | 代码分析服务规格 | 待评审 |
| [13-adaptive-optimization.md](./13-adaptive-optimization.md) | 候选人侧优化方案 | 待评审 |
| [14-assessment-probe-gaps.md](./14-assessment-probe-gaps.md) | ProbeGap 技术方案 | 已落地 |
| [20-implementation-modules.md](./20-implementation-modules.md) | 模块边界与交付切片 | 实施基线 |
| [30-improvement-spec-2026-08-16.md](./30-improvement-spec-2026-08-16.md) | 综合改进规格 | 实施基线 |
| [31-candidate-provider-and-interview-history-spec.md](./31-candidate-provider-and-interview-history-spec.md) | Provider 与面试历史规格 | 待实施 |
| [32-adaptive-agent-remediation-spec.md](./32-adaptive-agent-remediation-spec.md) | 代码治理与体验改进规格 | 已完成 |
| [33-remediation-execution-plan.md](./33-remediation-execution-plan.md) | 32 号规格执行计划 | 已完成 |
| [34-memory-three-layer-spec.md](./34-memory-three-layer-spec.md) | 三层记忆目标规格 | Approved v4，待实施 |
| [35-memory-three-layer-tickets.md](./35-memory-three-layer-tickets.md) | 三层记忆 v4 实施票据 | 待实施 |
