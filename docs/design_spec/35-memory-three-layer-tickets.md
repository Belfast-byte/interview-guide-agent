# 三层记忆 v4 实施 Tickets（历史记录）

> 维护：Agent
>
> 原始基线：34 号规格 v4
>
> 状态：**已实施历史票据，不得继续作为实现指令**
>
> 取代文档：[三层记忆 v5](./34-memory-three-layer-spec.md)、[Agent Loop 与 Working Memory 演进规格](./36-agent-loop-working-memory-spec.md)

## 0. 为什么停止执行本票据

这组票据曾经要求：

- 用 PostgreSQL WorkState 保存会话运行状态；
- 所有变化写 Typed Patch 和 revision；
- Java `NextActionPolicy` 固定下一动作；
- ASK/CALL_TOOL 先写 ActionIntent，再分阶段恢复；
- 通过 ToolResultEvent 把异步结果送回另一轮 ReAct。

代码审计证明这些机制复制了 Session、Turn、Assessment、ProbeGap、Evidence 和 SandboxExecution 已有事实，并把可重算的模型/只读 Tool 中间步骤变成长期恢复协议。它们增加了状态同步成本，却没有保护新的业务不变量。

因此，本文件只记录 v4 当时做过什么。后续改动不得引用 T02/T03 的 WorkState、Patch、Intent 或固定策略作为架构约束。

## 1. 原 Tickets 的当前裁决

| 原 Ticket | 原职责 | 当前裁决 | 有效替代 |
|---|---|---|---|
| T01 | SessionMode、level、scope、Target 初始化 | SIMPLIFY | 保留 mode/scope/不可变 Target；删除固定 follow-up/tool budget 和运行时 Plan 状态 |
| T02 | WorkState、Typed Patch、NextActionPolicy | DELETE | CoverageProjector + Turn 边界 WorkingMemorySnapshot |
| T03 | ActionIntent 执行与恢复 | DELETE | ASK 从领域事实重跑；只读 Tool 请求内执行；沙箱由 SandboxExecution 负责 |
| T04 | 不可变 Episode | KEEP/SIMPLIFY | 保留 Episode/纠正关系；删除 workRevision、Assessment Patch 和 generic ToolResult 引用 |
| T05 | QuestionExposure 与召回 | REDESIGN | 保留曝光事实；`memory_search` 由 Agent 按需调用，不强制 draft 后固定召回 |
| T06 | Semantic 双轨 | SIMPLIFY | Contribution 是事实源；默认按读聚合，不让 materialized state 承担 correctness |
| T07 | v4 清理与恢复验收 | REPLACE | 以 36 号规格的迁移切片和验收测试为准 |

## 2. 必须保留的业务语义

- Evaluation 与 Practice 会话模式和用户 scope；
- 正式 Planner/Assessor 的历史隔离；
- 每个已回答 Turn 对应可追溯 Episode；
- 已展示问题形成 QuestionExposure；
- Evaluation/Practice SemanticContribution 不跨轨；
- 历史数据只帮助中性去重或练习，不影响本轮正式评分；
- LLM、MCP、沙箱等外部调用不在数据库事务内；
- 沙箱隔离、稳定业务幂等、终态守卫和真实 Evidence。

## 3. 必须删除的旧目标

- `InterviewWorkState`、TargetState、WorkPhase、WorkIssue、WorkBudget；
- `agent_work_states` 和 `agent_work_state_patches`；
- WorkStateOperation、Reducer、Patch source/revision 幂等；
- `NextActionPolicy` 的固定“等待→达标切换→回答 issue→工具 issue→结束”顺序；
- ASK/CALL_TOOL ActionIntent 及 PLANNED/EXECUTING/SUCCEEDED/APPLIED/FAILED 状态机；
- `agent_action_intents`、恢复 scheduler 和 activeActionIntentId；
- read-only Tool 的 execution/idempotency/recovery；
- sandbox 的 generic Tool/Intent/Event 包装；
- Episode 的 workRevisionBefore/After；
- 依赖上述类型、表、Prompt 字段和 API 投影的测试。

## 4. 新实施入口

实施顺序不再沿用 T01→T07。唯一有效入口是 36 号规格：

1. 先把 SandboxExecution 固定为副作用唯一事实源；
2. 建立 CoverageProjector，使 API/Context 不再读取 WorkState；
3. 切换 ASK/FINISH 到真实 Agent Loop 和 Turn Snapshot；
4. 启用第一个真实只读 Tool（`rubric_search`）；
5. 删除 WorkState/Patch/Intent/ToolResultEvent 代码与 schema；
6. 收缩 Episodic/Semantic 派生状态。

迁移期间不双写两套状态机、不保留长期 feature flag、不新增 compatibility adapter。每个切片必须留下单一运行路径并通过并发、事实恢复、公平性和沙箱测试。
