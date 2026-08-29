# Agent 技术规格

本目录由 Agent 维护，用于把 [`../design/`](../design/README.md) 中的意图翻译为可实施、可测试、可验收的技术文档。技术规格应记录状态和上游设计；与设计意图冲突时，先暴露差异，不直接改写设计。

新文档优先使用 `NN-topic-spec.md`；派生的执行资料使用 `NN-topic-plan.md`、`NN-topic-tickets.md` 或 `NN-topic-review-YYYY-MM-DD.md`。现有文件本次保留原名，避免在目录迁移之外再做批量重命名。

## 当前 Agent 控制边界

[36-agent-loop-working-memory-spec.md](./36-agent-loop-working-memory-spec.md) 是 Agent 策略、Tool、Working Memory 和 crash recovery 的当前技术权威。出现历史冲突时统一按以下边界解释：

- 模型决定 Target/Gap、追问或切换、可选只读 Tool、参数/顺序、ASK/FINISH；
- Java 只保证权限、Session/Turn 合法性、最大轮次、Plan 成员关系、Tool 安全、证据真实性、沙箱和数据库完整性；
- 固定 Skill/ID 查询是普通代码；语义搜索才可能是 Agent Tool；沙箱提交是 Application Command；
- 只读 Tool 和未提交 ASK 可重算，不持久化 Intent/Execution/Recovery；
- WorkState/Patch/ActionIntent 不再是目标架构，SandboxExecution 继续作为副作用事实源。

## 文档索引

| 文档 | 类型 | 当前状态 |
|---|---|---|
| [02-auth-permission.md](./02-auth-permission.md) | 认证与权限规格 | 已实施 |
| [10-text-interview.md](./10-text-interview.md) | 自适应文本面试实现设计 | 已按 36 校准 |
| [11-algorithm-interview.md](./11-algorithm-interview.md) | 算法面试与延迟执行规格 | 已按 36 校准 |
| [12-code-analysis-service.md](./12-code-analysis-service.md) | 代码分析服务规格 | Tool 边界已按 36 校准 |
| [13-adaptive-optimization.md](./13-adaptive-optimization.md) | 候选人侧优化方案 | 策略部分已按 36 重写 |
| [14-assessment-probe-gaps.md](./14-assessment-probe-gaps.md) | ProbeGap 技术方案 | 现状已落地，目标语义已按 36 校准 |
| [20-implementation-modules.md](./20-implementation-modules.md) | 模块边界与交付切片 | 实施基线，已按 36 校准 |
| [30-improvement-spec-2026-08-16.md](./30-improvement-spec-2026-08-16.md) | 综合改进规格 | 历史基线，Agent 策略已被 36 取代 |
| [31-candidate-provider-and-interview-history-spec.md](./31-candidate-provider-and-interview-history-spec.md) | Provider 与面试历史规格 | 待实施 |
| [32-adaptive-agent-remediation-spec.md](./32-adaptive-agent-remediation-spec.md) | 代码治理与体验改进规格 | 已完成历史记录，不是当前架构约束 |
| [33-remediation-execution-plan.md](./33-remediation-execution-plan.md) | 32 号规格执行计划 | 已完成历史记录，不是当前架构约束 |
| [34-memory-three-layer-spec.md](./34-memory-three-layer-spec.md) | 三层记忆目标规格 | v5 目标规格 |
| [35-memory-three-layer-tickets.md](./35-memory-three-layer-tickets.md) | 三层记忆 v4 实施票据 | 历史记录，禁止继续执行 |
| [36-agent-loop-working-memory-spec.md](./36-agent-loop-working-memory-spec.md) | Agent Loop、Tool、Working Memory 与删旧迁移 | 目标规格，待实施 |
