# Agent Loop 与 Working Memory 演进规格

> 维护：Agent；上游产品意图以 [面试 Agent 的运行方式](../design/03-agent-loop-and-working-memory.md) 为准。
> 状态：目标规格，待实施。
> 依据：[2026-08-29 架构审计](../review/adaptive-agent-complexity-audit-2026-08-29.md)。
> 最后更新：2026-08-29

本规格取代 34 号 v4 中 WorkState/Patch/ActionIntent 和固定 NextActionPolicy，取代 35 号旧 T02/T03，并校准 10/11/12/13/14/20 中的模型/Java、Tool 和恢复边界。Episodic/Semantic 的当前目标仍见 [34-memory-three-layer-spec.md](./34-memory-three-layer-spec.md) v5。

## 0. 目标与非目标

目标只有四个：

1. 模型真正决定当前 Target/Gap、追问或切换、只读 Tool 和结束建议；
2. Java 收缩为权限、数据完整性、硬上限、Tool 安全和外部副作用边界；
3. Working Memory 成为 Agent 的短期认知，而不是第二套 workflow 数据库；
4. 删除可从 Session/Turn/Assessment/Evidence/SandboxExecution 重建的 Intent、Patch、revision 和 recovery。

不建设通用 Agent 平台，不建立通用 Role Registry，不恢复每次 LLM/只读 Tool 中间步骤，不保存完整思维链，不新增事件总线、checkpoint、分布式锁或 silent fallback。

最小闭环是：

~~~text
ContextAssembler
  -> InterviewAgentLoop
  -> 0..N read-only Tool observations
  -> ASK | FINISH
  -> hard-boundary validation
  -> short transaction
~~~

Planner 与 Assessor 仍是明确的结构化模型调用，不包装成通用 Role runtime。

## 1. 唯一事实源

| 事实 | 唯一事实源 | 删除的副本 |
|---|---|---|
| 会话生命周期、最大轮次 | Session | WorkPhase、WorkState.FINISHED |
| 用户看到的问题和提交的回答 | Turn | ASK Intent、awaitingAnswerTurnIndex |
| 正式回答判断 | Assessment | WorkState.currentDepth |
| 尚缺的正式证据 | ProbeGap | WorkIssue/status |
| 可追溯能力证据 | Evidence | WorkEvidenceRef、ToolExecution 摘要 |
| 本场覆盖 | Plan + Turn + Assessment + ProbeGap + Evidence 投影 | TargetWorkState、remainingBudget、Plan runtime status |
| Agent 当前注意力和假设 | 最新 Turn 的 WorkingMemorySnapshot | agent_work_states、Patch journal |
| 代码执行状态和结果 | SandboxExecution | sandbox Intent、ToolExecution、ToolResultEvent |
| 长期经历 | Episode | workRevisionBefore/After、题答副本 |
| 长期能力输入 | SemanticContribution | SemanticState 作为 correctness 事实 |

## 2. 目标执行链

### 2.1 创建会话与首题

~~~text
Planner LLM
  -> Java 校验 catalog/scope/depth ceiling/maxTurns
  -> 短事务保存 Session + immutable Plan
  -> ContextAssembler + InterviewAgentLoop
  -> 短事务保存首个 Turn + WorkingMemorySnapshot
~~~

### 2.2 提交回答

~~~text
条件更新当前 Turn.answer
  -> 事务外 Assessor LLM，形成 Assessment/Evidence/ProbeGap proposal
  -> ContextAssembler 组装最新事实、Coverage、上一 Snapshot
  -> InterviewAgentLoop 选择 Target/Gap/Tool/ASK/FINISH
  -> 一个短事务保存：
       Assessment + Evidence + ProbeGap + Episode
       + 下一 Turn/WorkingMemorySnapshot
       或 Session.COMPLETED
~~~

### 2.3 代码提交

~~~text
Application Service
  -> 稳定业务键 createOrReuse SandboxExecution(PENDING)
  -> Redis Stream + 隔离沙箱
  -> 条件更新唯一终态
  -> 同一事务写入唯一 Evidence 并标记 consumedAt
  -> 后续 Agent Loop 从最新事实组装 Context
~~~

### 2.4 最终报告

报告只读取 Session、Plan、Turn、Assessment 和 Evidence，继续使用确定性聚合；不读取 Working Memory、Intent、Tool 执行状态或 Semantic cache。

## 3. 模型与 Java 的控制边界

| 决策 | Owner |
|---|---|
| 当前优先 Gap、临时排序和工作假设 | Model |
| 继续深挖、切 Target、Target 顺序 | Model |
| 是否查 Rubric/题库/历史、查询参数和调用顺序 | Model |
| 下一题目标、内容、采用来源和提前结束建议 | Model |
| tenant/candidate ownership、Session/Turn 合法性 | Java |
| maxTurns、depth ceiling、Target 属于 Plan | Java |
| Tool allowlist/schema/scope、只读/副作用分类 | Java |
| 单次一个问题、Evidence quote/anchor/provenance 真实性 | Java |
| unique、条件 answer claim、必要的一个 Session version | Database + Java transaction |
| 沙箱隔离、稳定幂等和终态 | SandboxExecution owner |

Java 拒绝非法提案时返回结构化 validation Observation，由模型重新决定；禁止 Java 替模型换 Gap、换 Target、重写题目或生成兜底答案。

必须删除的策略硬编码包括：取第一个 Gap、固定维度顺序、达到 expectedDepth 自动切换、固定 answer issue 优先于 tool issue、`followUpDepth`/`triggerType`/`mandatorySubset` 决定动作，以及 Prompt 要求模型严格服从 Java 预选 `selectedGap`。

## 4. 领域模型调整

### Session / Plan

- Session 只保留 owner、创建快照、mode、status、`maxTurns` 和一个并发 version；current turn 从 Turn 推导。
- 达到 `maxTurns` 后代码直接完成 Session，不再调用模型生成不可能被接受的 ASK。
- Plan 创建后不可变，只描述合法 Target、TopicKey、固定 Skill、证据目标、expected depth 和 depth ceiling。
- expected depth 是 Agent 的参考；depth ceiling 和 maxTurns 是硬边界。
- 删除 Plan 的运行时 completedTurns/status、固定 dimensionOrder 行为、follow-up/tool budget 和 replan 历史。

### Turn

- Turn 保存用户真正看到的问题、回答、target 和最终采用的 provenance。
- Snapshot 与问题在同一 Turn 保存；`(session_id, turn_index)` 继续唯一。
- answer 使用条件更新，只能从 null 写入一次；不同 payload 重放必须显式冲突。
- 删除 `uk_agent_turn_source_probe_gap`，保留外键：同一 Gap 允许用不同场景多轮验证。
- 不保存只读 Tool 的每次执行，只保存最终采用的 Rubric/Question/Exposure 引用。

### Assessment / ProbeGap / Evidence

- Assessor 只生成正式判断，不再生成 WorkState Patch。
- quote、Gap anchor 和代码 provenance 必须命中真实来源。
- ProbeGap 是带 Assessment/Turn 来源的正式事实；后续 Assessment 可以记录其关闭事实。
- Rubric/题库 Observation 只提供问题或评分 provenance，不自动成为候选人能力 Evidence。
- 沙箱和代码分析结果以稳定 execution/artifact ID 进入 Evidence。

## 5. Working Memory 契约

~~~text
WorkingMemory
  basedOnTurnIndex?
  activeTargetId?
  activeGapId?
  gapPriorities[]: gapId / reason
  hypotheses[]:
    statement / status
    supportingEvidenceIds[]
    contradictingEvidenceIds[]
  nextProbeIntent?
  adoptedObservationRefs[]
~~~

规则：

1. 只保存 ID/reference 和短期认知，不复制领域对象全文；
2. 不保存 phase、revision、remainingBudget、followUpDepth、triggerType、Intent 或 Tool status；
3. Working hypothesis 不能进入 Assessment、Evidence、权限或 Report；
4. 只有 InterviewAgentLoop 生产和消费；Planner/Assessor 不读取；
5. 一次 Loop 内可反复更新完整内存对象，不创建 Delta/Patch/Reducer/journal；
6. Java 只校验 Target/Gap/Evidence/Observation 引用来自本次 AgentContext；
7. ASK 时 Snapshot 与 Turn 原子保存；FINISH 不保存无消费者的额外 Snapshot；
8. 中间崩溃后从最近 Turn Snapshot 和新领域事实重跑。

## 6. ContextAssembler 与 Coverage

`CoverageProjector` 从事实产生中性 `CoverageView`：

~~~text
AgentContext
  sessionMode / maxTurns / askedTurns / remainingTurns
  allowedTargets[] / expectedDepth / depthCeiling
  targetCoverage[]
  openProbeGaps[] + source/evidence references
  recentTurns[]
  workingMemory
  allowedReadTools[]
~~~

ContextAssembler 一次批量读取事实，自动加载 Plan 固定 Skill。它不选择 active Target、不排序 Gap、不决定追问或结束。正式模式的历史召回只返回中性曝光/重验证目标；练习模式才允许用户 scope 内的完整诊断。Assessor 始终看不到历史评级、Semantic 画像或 Working Memory。

## 7. InterviewAgentLoop

~~~text
AgentDecision
  workingMemory
  action:
    ASK(targetId, sourceGapId?, question, decisionSummary, adoptedSourceRefs[])
    CALL_READ_TOOLS(calls[])
    FINISH(decisionSummary)
~~~

循环协议：

1. 模型读取 AgentContext、当前 Working Memory 和已有 Observations；
2. ToolCall 先经过 allowlist/schema/scope/provenance 校验；
3. 一个响应中的 Tool calls 按模型给出的顺序执行，Java 不重排；
4. Tool 成功、空结果、超时或错误都作为 Observation 返回模型；
5. 每次模型响应可更新内存中的 Working Memory；
6. ASK/FINISH 结束 Loop，最终决定才允许提交领域事实；
7. step/tool/deadline 只作为已有显式资源边界，不选择策略；耗尽时明确失败且数据库不推进；
8. `decisionSummary` 是简短业务理由，不是完整思维链。

## 8. Tool 分类

Tool 必须同时满足：是否调用由模型决定、关键参数依赖语义、结果回到模型并改变后续决定。

| 能力 | 分类 | 持久化 |
|---|---|---|
| 固定 Skill 加载 | ContextAssembler | 否 |
| `rubric_get(id)` | 普通 Service | 否 |
| `rubric_search(query, intent, levelHints)` | 真只读 Agent Tool | 只存最终采用的 entry/version |
| 题库语义搜索 | 真 Tool；当前无闭环则先删除，需求上线再恢复 | 只存采用题目 provenance |
| `memory_search` | 真 Tool；按 EVALUATION/PRACTICE 过滤 | 只存采用的 Episode/Exposure 引用 |
| Session/state/config/planner lookup | ContextAssembler/普通读取 | 否 |
| `code.trace` | 真只读 Agent Tool | 只存采用的 artifact provenance |
| sandbox submit | Application Command | SandboxExecution |

现有 ToolGateway 收缩为无状态只读 executor，只负责 allowlist、schema、tenant/scope、provenance、deadline 和 dispatch。删除 read-only ToolExecution、invocation idempotency、pending 和 recovery。Tool 输出视为不可信数据，不得提升为 system instruction。

## 9. Crash recovery 与并发

- 模型返回但 Turn 未提交：重跑，用户未看到草稿；
- Turn 已提交但响应丢失：读取并返回已存在 Turn；
- answer 已保存但最终事务未提交：从 answer 重跑 Assessor 和 Loop；
- 只读 Tool 重跑：允许，无业务副作用；
- sandbox 重跑：复用稳定业务键和同一 SandboxExecution。

两个请求并发提交同一回答时，correctness owner 是 answer 条件更新、Assessment/Episode/后继 Turn 唯一约束和一个 Session version。并发外部计算造成的重复 LLM 是性能浪费，不再用 Intent、Redis lock、WorkState revision 或 Patch 保护。

沙箱稳定键至少包含 `sessionId + turnIndex + problemId + sourceHash + runMode`。终态消费在一个事务内完成“锁定 terminal execution → 插入唯一 Evidence → 标记 consumedAt”；删除 ToolResultEvent 的 reserve/complete 两阶段窗口。PENDING 扫描器始终重投同一个 executionId。

## 10. 模块处置

### DELETE

- `core.intent` 全包；
- `InterviewWorkState`、`TargetWorkState`、`WorkPhase`、`WorkIssue`、`NextActionPolicy`、WorkState Patch/Reducer/Operation；
- `ActionIntentExecutor`、`ActionIntentPlanFactory`、`ActionIntentRecovery*`、`PersistentActionCoordinator`、`WorkStatePolicyPlanner`、`AssessmentWorkStatePlanner`；
- `persistence.intent`、`persistence.working` 及对应 scheduler/repository/codec；
- `ToolResultEvent`、`ToolResultFollowUp`、read-only `ToolExecution` 和 PendingToolResult 协议；
- `LoadSkillTool`、`SandboxSubmitTool`；当前 `RubricLookupTool` 的固定查询 Tool 身份；
- 固定单角色仍套用的 `AgentRoleRegistry/AgentRoleDefinition`；
- 当前无业务闭环的 question-bank index/MCP fallback；
- 对应表、列、枚举、Prompt 字段、API 伪状态和只锁定旧机制的测试。

### SIMPLIFY / KEEP / REDESIGN

- SIMPLIFY：Plan 只留不可变 scope/目标；Episode 删除 work revision；SemanticContribution 成为事实源；Application/Persistence 大服务按 use case 收缩。
- KEEP：Session、Turn、Plan、Assessment、ProbeGap、Evidence、Episode、QuestionExposure、权限、DB unique、一个乐观版本、固定 Skill、结构化输出、deadline、Tool allowlist、SandboxExecution/隔离/稳定幂等、确定性 Report。
- REDESIGN：`BoundedActionRuntime` → `InterviewAgentLoop`；workflow WorkState → 内存 WorkingMemory + Turn Snapshot；`ContextAssembler` → facts + Coverage + Memory；`ToolGateway` → 无状态只读 executor；`RubricLookupTool` → `RubricSearchTool`。

## 11. 迁移切片

### S0：固定真实副作用 owner

沙箱改稳定业务键；ToolResult 消费改为一个事务；修复终态 race。旧 Agent 主链暂时运行，但 sandbox 不再依赖 Intent UUID。

### S1：建立事实投影读链

ContextAssembler、维度 API 和报告改读 Session/Plan/Turn/Assessment/ProbeGap/Evidence，建立 CoverageProjector；停止新增 WorkState 消费者，暂不删表。

### S2：切换 ASK/FINISH

引入 WorkingMemory、AgentDecision、Validator 和 InterviewAgentLoop，先支持 ASK/FINISH。回答链改为 answer claim → Assessor → AgentLoop → 一个最终短事务；删除 ASK Intent、NextActionPolicy 和 WorkState 写入。切换后不双写。

### S3：启用第一个真 Tool

实现 `rubric_search` 与 Observation 回流；Skill 自动加载；sandbox 直接 application command。删除 read-only ToolExecution、Role Registry 和三个伪 Tool。

### S4：删除恢复协议与 schema

删除 Intent/WorkState/Patch/Event 类、repository、scheduler、迁移列/FK/表和前端伪状态；用新 Flyway 迁移，不改已应用历史迁移，不保留 legacy codec 或长期 feature flag。

### S5：收缩派生记忆

删除 Episode work revision；enrichment 只扫描缺少结果的 Episode；Semantic 默认按 Contribution 聚合。每个切片必须留下单一运行路径，不维护新旧状态机。

## 12. 必须通过的测试

1. 首题无 Tool 直接 ASK；
2. `rubric_search → Observation → ASK` 真实循环；
3. 多 Gap 时模型可选非首项，Java 只校验引用；
4. 模型可自由 follow-up/switch，Plan 顺序和 expectedDepth 不自动决定动作；
5. 非白名单 Tool、Plan 外 Target、伪造 Evidence 引用被拒并形成 Observation；
6. Tool timeout/error 明确返回模型；资源耗尽失败且不推进数据库；
7. Working Memory 经多个 Observation 更新，最终只随 Turn 保存一次；
8. Snapshot 不含领域事实副本、phase、revision 或 Intent；
9. answer 后、model 后、Turn commit 后三个崩溃点都能从事实恢复；
10. 并发同答只产生一个 Assessment、Episode 和后继 Turn；
11. 同一 ProbeGap 可被多个 Turn 继续验证；
12. sandbox retry/并发等效一次，终态与 Evidence 原子消费；
13. 正式 Assessor 看不到 Working Memory/历史评级，Report 不读取 Snapshot；
14. 删除 WorkState 后维度/Coverage API 仍与事实一致；
15. PostgreSQL Flyway 与 JPA schema 验证通过。

## 13. 完成标准

- 生产主链真实存在 ToolCall → Observation → Model → ASK；Gap、Target、追问/切换、可选 Tool 和提前结束由模型决定；
- Java 只保留本文列出的硬边界；ASK 和只读 Tool 没有持久执行状态；
- SandboxExecution 是副作用唯一执行事实源；Working Memory 只在 Loop 内更新并在 Turn 边界保存一次；
- 报告/API 只读领域事实；WorkState、Patch、Intent 及其恢复/schema/死测试已删除；
- 没有双写、legacy runtime 分支、silent fallback 或 mock success。
