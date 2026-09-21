# Agent 技术规格

本目录由 Agent 维护，用于把 [`../design/`](../design/README.md) 中的意图翻译为可实施、可测试、可验收的技术文档。技术规格应记录状态和上游设计；与设计意图冲突时，先暴露差异，不直接改写设计。

新文档优先使用 `NN-topic-spec.md`；派生的执行资料使用 `NN-topic-plan.md`、`NN-topic-tickets.md` 或 `NN-topic-review-YYYY-MM-DD.md`。已完成且被后续规格替代的执行方案直接删除，历史内容通过 Git 查询；仍有未完成验收或当前业务契约的文档继续保留。

## 当前 Agent 控制边界

[36-agent-loop-working-memory-spec.md](./36-agent-loop-working-memory-spec.md) 继续约束 Agent Loop 与 Tool；三层记忆的最新目标以 [38-memory-business-reuse-spec.md](./38-memory-business-reuse-spec.md) 为准（业务复用代码已实现，验收范围见 37），冲突的旧记忆设计不再继续建设。出现历史冲突时统一按以下边界解释：

- 模型决定 Target/Gap、追问或切换、可选只读 Tool、参数/顺序、出题与结束建议；
- Java 只保证权限、Session/Turn 合法性、最大轮次、Plan 成员关系、Tool 安全、证据真实性、沙箱和数据库完整性；
- 固定 Skill/ID 查询是普通代码；语义搜索才可能是 Agent Tool；沙箱提交是 Application Command；
- 只读 Tool 和未提交提案可重算，不持久化 Intent/Execution/Recovery；
- WorkState/Patch/ActionIntent 不再是目标架构，SandboxExecution 继续作为副作用事实源。

Java 业务代码改错题以 [40-java-code-repair-spec.md](./40-java-code-repair-spec.md) 为当前实现规格，验收记录见 [41 号 tickets](./41-java-code-repair-tickets.md)。旧 Pi / MCP 仓库分析专项已撤回，相关接入与部署路线不再建设；历史审计记录和现存 API 的权限说明保留其原有事实语义。

[44-native-tools-agent-runtime-spec.md](./44-native-tools-agent-runtime-spec.md) 记录原生工具迁移实现与验收边界（实现及离线验证完成）：保留自有 Runtime 和三个角色，使用 Spring AI 执行原生查询及出题/结束提案工具，删除自定义工具协议。其工具传输与执行实现更新 36、39、42 的对应部分；真实模型和原页面验收尚待环境。

## 文档索引

| 文档 | 类型 | 当前状态 |
|---|---|---|
| [02-auth-permission.md](./02-auth-permission.md) | 认证与权限规格 | 已实施 |
| [10-text-interview.md](./10-text-interview.md) | 自适应文本面试实现设计 | 已按 36 校准 |
| [11-algorithm-interview.md](./11-algorithm-interview.md) | 算法面试与延迟执行规格 | 已按 36 校准 |
| [13-adaptive-optimization.md](./13-adaptive-optimization.md) | 候选人侧优化方案 | 策略部分已按 36 重写 |
| [14-assessment-probe-gaps.md](./14-assessment-probe-gaps.md) | ProbeGap 技术方案 | 现状已落地，目标语义已按 36 校准 |
| [20-implementation-modules.md](./20-implementation-modules.md) | 模块边界与交付切片 | 实施基线，已按 36 校准 |
| [31-candidate-provider-and-interview-history-spec.md](./31-candidate-provider-and-interview-history-spec.md) | Provider 与面试历史规格 | 待实施 |
| [36-agent-loop-working-memory-spec.md](./36-agent-loop-working-memory-spec.md) | Agent Loop、Tool、Working Memory 与删旧迁移 | Loop 规则继续适用；记忆目标以 38 为准 |
| [37-complexity-reduction-refactoring-plan.md](./37-complexity-reduction-refactoring-plan.md) | 复杂度削减重构指南 | 记录实际实施状态与历史边界 |
| [38-memory-business-reuse-spec.md](./38-memory-business-reuse-spec.md) | 三层记忆业务复用规格 | 当前记忆规格，业务代码已实现；验证边界见 37 |
| [39-interview-agent-tools-spec.md](./39-interview-agent-tools-spec.md) | 面试 Agent 内部工具扩展规格 | 已实施材料、题库、评估和代码任务四个只读工具；knowledge_search 仍为候选 |
| [40-java-code-repair-spec.md](./40-java-code-repair-spec.md) | Java 业务代码改错题规格 | 已实施题型、模式、schema、编辑器、持久化及 39 号工具联动 |
| [41-java-code-repair-tickets.md](./41-java-code-repair-tickets.md) | Java 改错题实施 tickets | 基线、提交、验收证据及真实模型抽样限制 |
| [42-interview-context-reference-rag-spec.md](./42-interview-context-reference-rag-spec.md) | 出题上下文精简与专业参考 RAG | 代码已实现，真实 RAG 验收待完成；Planner 保持原样 |
| [43-interview-context-reference-rag-tickets.md](./43-interview-context-reference-rag-tickets.md) | 出题上下文与参考 RAG tickets | 实施及验收进度 |
| [44-native-tools-agent-runtime-spec.md](./44-native-tools-agent-runtime-spec.md) | Spring AI 原生工具与自有 Runtime 改进规格 | 原生迁移及离线回归完成；真实模型/页面验收待环境 |
| [45-native-tools-agent-runtime-tickets.md](./45-native-tools-agent-runtime-tickets.md) | 原生工具迁移 tickets | 执行进度、验证及逐票推送记录 |
