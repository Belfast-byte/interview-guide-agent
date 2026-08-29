# 三层记忆技术规格 v5（Working / Episodic / Semantic）

> 维护：Agent；上游设计以 [三层记忆设计](../design/02-memory-design.md) 和 [面试 Agent 的运行方式](../design/03-agent-loop-and-working-memory.md) 为准。
>
> 状态：目标规格；v4 的 WorkState/Patch/ActionIntent 方案已被取代。
>
> 最后更新：2026-08-29

v5 保留 v4 已证明有业务价值的 SessionMode、公平性隔离、Episode、QuestionExposure 和 Semantic 双轨语义，删除为恢复 Java workflow 中间步骤建立的第二套状态。Agent Loop 与代码迁移细节见 [36-agent-loop-working-memory-spec.md](./36-agent-loop-working-memory-spec.md)。

## 0. 范围与原则

三层记忆分别回答三个问题：

| 层 | 回答的问题 | correctness 事实源 |
|---|---|---|
| Working | Agent 此刻关注什么、暂时如何理解、下一步想验证什么 | 最新 Turn 携带的 Snapshot |
| Episodic | 过去具体发生过什么 | Turn/Assessment/ProbeGap/Evidence/Episode/QuestionExposure |
| Semantic | 多次经历长期说明了什么 | SemanticContribution；聚合结果可重算 |

PostgreSQL 保存领域事实和每轮最终 Snapshot。Redis 只用于已有异步 Stream。系统崩溃后从最近领域事实重新运行，不恢复某次 LLM 或只读 Tool 的中间步骤。

本规格不建设通用 Memory Platform，不保存完整思维链，不建立 WorkState、Patch journal、ActionIntent 或 Memory revision 协议。

## 1. 会话模式与初始化

~~~text
MemoryOwner    = (tenantId, candidateId)
TopicKey       = (skillId, focusId)
SessionMode    = EVALUATION | PRACTICE
CandidateLevel = INTERN | CAMPUS | EXPERIENCED
~~~

`SessionMode` 创建后不可变。Session 保存本次 JD、简历、level 和用户选择的 practice scope 快照。

- `EVALUATION` Planner 只读取本场输入，禁止读取候选人历史评级或 Semantic 画像。
- `PRACTICE` Planner 只读取用户明确 scope 内的 Semantic planning view；历史画像不能扩大 scope。
- Planner 生成 Target Plan；Java 只校验 TopicKey、用户 scope、depth ceiling 和全局 `maxTurns`。
- Plan 创建后不可变。Target 顺序、继续深挖、切换或结束由 Agent 运行时决定，不把 follow-up/tool budget 写成领域状态机。
- Plan 已确定的 Skill 由 ContextAssembler 自动加载，不由模型调用 `load_skill`。

## 2. Working Memory：当前正在想什么

### 2.1 与领域事实分离

Working Memory 是短期认知，不是业务数据库。它保存引用，不复制事实：

| 不复制的内容 | 事实源 |
|---|---|
| Target 名称、TopicKey、固定 Skill、depth ceiling | Plan |
| 已展示问题、回答、当前轮次 | Turn |
| 正式评级和置信度 | Assessment |
| Gap 正文、来源和关闭事实 | ProbeGap |
| quote、代码和沙箱结果 | Evidence / SandboxExecution |
| 剩余轮次和覆盖情况 | Plan + Turn + Assessment + Evidence 投影 |
| Session 生命周期 | Session |

Working Memory 不保存 `phase`、`revision`、remaining budget、`ACTION_PENDING`、active Intent、Tool status 或完整 CoT。

### 2.2 最小契约

~~~text
WorkingMemorySnapshot
  basedOnTurnIndex?
  activeTargetId?
  activeGapId?
  gapPriorities[]:
    gapId / reason
  hypotheses[]:
    statement
    status: ACTIVE | WEAKENED | REJECTED
    supportingEvidenceIds[]
    contradictingEvidenceIds[]
  nextProbeIntent?
  adoptedObservationRefs[]
~~~

约束：

1. Target、Gap、Evidence 和 Observation 引用必须来自本次 AgentContext；
2. hypothesis 是临时判断，不得成为 Assessment、Evidence、权限判断或报告结论；
3. Working Memory 可以在一次 Loop 内反复更新，不创建 Patch/Reducer/journal；
4. 最终 ASK 时，完整 Snapshot 与下一 Turn 在一个短事务保存；
5. FINISH 时不保存没有消费者的额外 Snapshot；
6. 第一题可以没有上一份 Snapshot；其他 Snapshot 引用损坏时明确报数据完整性错误。

### 2.3 存储

Snapshot 直接存入产生该问题的 `agent_turns.working_memory_snapshot_json`，不新增“当前状态”表。

- 最新 Turn 的 Snapshot 是下一次 Loop 的种子；
- 旧 Snapshot 只说明当时为什么关注某项，不参与当前业务状态判断；
- `(session_id, turn_index)` 唯一约束同时保证每个问题最多一份 Snapshot；
- 不创建 `agent_work_states`、`agent_work_state_patches`、`agent_action_intents` 或对应 revision/version。

### 2.4 Coverage 与 Working Memory

`CoverageView` 由 Plan、Turn、Assessment、ProbeGap 和 Evidence 按读投影：

~~~text
CoverageView
  askedTurns / remainingTurns
  allowedTargets[]
  targetCoverage[]
  openGaps[]
  evidenceRefs[]
~~~

Coverage 是中性事实视图；Working Memory 是模型对这些事实的当前注意力。ContextAssembler 可以优先加载 Snapshot 所引用的材料，但必须把全部合法 Target/open Gap 告知 Agent，不能替模型排序或隐藏选择。

### 2.5 Producer 与 Consumer

| 项目 | 定义 |
|---|---|
| Producer | InterviewAgentLoop 的模型响应 |
| Validator | 只校验引用存在、Target 属于 Plan、字段 schema 和大小边界 |
| Storage | 产生下一问题的 Turn JSON 字段 |
| Consumer | 下一次 InterviewAgentLoop |
| 不得消费 | Planner、Assessor、权限、Session 状态判断、最终 Report |

## 3. Episodic Memory：过去发生了什么

### 3.1 Episode 事实

每个已回答 Turn 在 Assessment、Evidence 和 ProbeGap 落库的同一短事务中创建一个 Episode：

~~~text
EpisodeFact
  episodeId / MemoryOwner / sessionId / sessionMode / turnIndex
  TopicKey / targetId
  turnId / assessmentId
  assistanceLevel: NONE | FOLLOW_UP | HINT | TOOL_ASSISTED
  closureStatus: RESOLVED | UNRESOLVED | ABANDONED
  correctsEpisodeId? / createdAt
~~~

Episode 通过稳定 ID 回到问题、回答、评级、Gap、Evidence 和 SandboxExecution，不复制结论，不保存 workRevisionBefore/After。后续纠正创建新 Turn/Episode，并用 `correctsEpisodeId` 回连。

摘要、标签和 embedding 是 enrichment，不是 Episode 事实。缺失时按“尚未 enrichment”扫描原始 Episode 重新计算，不建立多层 checkpoint 或恢复状态机。

### 3.2 QuestionExposure

问题真正展示时保存：

~~~text
QuestionExposure
  exposureId / MemoryOwner / sessionId / turnId
  TopicKey / evidenceObjective / probeDepth / difficulty
  questionText / scenarioFingerprint / wordingFingerprint
  sourceExposureId? / sourceEpisodeId? / embeddingDocumentId? / askedAt
~~~

同一知识点允许复测，题目文本不应简单复用。Exposure 是用户已看到问题的领域事实，因此即使尚未回答也保留。

### 3.3 召回 Tool

历史召回是否发生、查询哪个 Gap，由 Agent 决定；不强制“先生成 draft 再查询”的固定流程。

- `EVALUATION` 的 `memory_search` 只返回中性曝光、TopicKey、证据目标和“需要重新验证什么”，不返回旧答案、评级或标签；
- `PRACTICE` 在用户 scope 内可以返回完整 Episode 诊断；
- 召回结果作为 Observation 返回模型；
- 只有最终采用的 sourceExposureId/sourceEpisodeId 随 Turn 保存，查询执行本身不持久化。

## 4. Semantic Memory：长期可以相信什么

### 4.1 唯一事实源

~~~text
SemanticTrack = EVALUATED_CAPABILITY | PRACTICE_MASTERY
SemanticContribution
  contributionId / episodeId / MemoryOwner / TopicKey / track
  evaluationLevel? / practiceOutcome? / assistanceLevel? / createdAt
~~~

- Evaluation Episode 只产生正式能力 contribution；
- Practice Episode 只产生练习掌握度 contribution；
- Contribution 不修改，`(episodeId, track)` 唯一；
- 当前能力、掌握度、趋势和稳定模式默认从 contributions 聚合；
- 只有读取性能有测量证据时才增加 materialized SemanticState；它只是 cache，不是 correctness 事实源。

### 4.2 聚合边界

正式能力沿用统一 L0～L4 聚合规则；练习掌握度根据独立完成、辅助完成或未解决聚合。具体公式只有一处实现，报告与 API 复用同一 projector。

稳定错误模式至少需要两个不同 Episode 的同类来源。LLM 只提 enrichment 标签，代码验证来源后聚合；来源不足时标签只留在 Episode。

### 4.3 消费隔离

| 消费者 | Episode | Semantic |
|---|---|---|
| Evaluation Planner | 禁止 | 禁止 |
| Evaluation Interview Agent | 中性 `memory_search` | 禁止 |
| Evaluation Assessor | 禁止历史 | 禁止 |
| Practice Planner | 禁止 | 用户 scope 内 planning view |
| Practice Interview Agent | scope 内完整诊断 | 当前相关弱项 |
| Practice Assessor | 禁止历史评级 | 禁止 |
| 报告/推荐 | 可追溯来源 | 双轨聚合 |

正式评估只根据本场回答和真实执行事实，不能被 Working Memory、历史评级或 Semantic 画像提高或降低。

## 5. 写入与恢复

1. 初始化：事务外生成 Plan proposal；短事务保存 Session + immutable Plan。
2. 首题/下一题：读取事实和上一 Snapshot，运行 Agent Loop；短事务保存最终 Turn + Snapshot。
3. 回答：条件写入 answer；事务外 Assessment 和 Agent Loop；一个最终短事务保存 Assessment/Evidence/ProbeGap/Episode + 下一 Turn/Snapshot，或完成 Session。
4. 只读 Tool：请求内执行，Observation 回流模型；不落 Intent、Execution 或 Recovery。
5. 沙箱：Application 创建或复用 SandboxExecution；终态与唯一 Evidence/consumedAt 原子提交。
6. Semantic：Episode 同事务追加 contribution；读时聚合。Enrichment 从缺少结果的 Episode 重新计算。

崩溃恢复一律从最近领域事实重跑：

| 崩溃点 | 恢复 |
|---|---|
| 模型或只读 Tool 执行中 | 读取最新 Turn/Snapshot 重跑 |
| ASK 已生成、Turn 未提交 | 重跑；用户未看到草稿 |
| Turn 已提交、HTTP 响应丢失 | 返回已存在 Turn |
| answer 已保存、后继事实未提交 | 从 answer 重跑 Assessment + Agent Loop |
| Sandbox 投递或 worker 重试 | 复用稳定业务键和同一 executionId |

## 6. 场景

正式面试当前有 Redis fork/COW Gap。Working Memory 暂时判断候选人可能只记得 BGSAVE 名词，Agent 自主调用 `rubric_search` 查询 fork/COW 的能力区分，再调用中性 `memory_search` 发现过去问过相同措辞。两个 Observation 改变其工作假设，最终换成“持续写入时 BGSAVE 为什么导致内存突增”的问题。Turn 只保存采用的 Rubric/Exposure 引用和最终 Snapshot；两次只读查询没有执行实体。

练习模式由用户 scope 内 Semantic view 帮助 Planner 生成 Target。Agent 可读取完整历史 Episode 做提示和复测；每轮仍独立评估并写入 Practice contribution，不能覆盖正式能力。

## 7. 完成标准

1. 没有持久 WorkState、Typed Patch、ActionIntent 或只读 ToolExecution；
2. Working Memory 只含短期认知和事实引用，并与最终 Turn 一次保存；
3. Coverage 能完全由 Plan/Turn/Assessment/ProbeGap/Evidence 推导；
4. Agent 可以从全部合法 Gap/Target 中选择，Java 不固定优先级或顺序；
5. 崩溃后从最新事实重跑，不恢复中间模型步骤；
6. 每个已回答 Turn 恰有一个 Episode，已展示问题有 QuestionExposure；
7. Evaluation/Practice contributions 不跨轨，Semantic 聚合可从贡献重建；
8. 正式 Assessor 看不到 Working Memory、历史评级或 Semantic 画像；
9. memory/rubric 等只读查询失败明确返回 Agent，不静默 fallback；
10. PostgreSQL 测试覆盖 Turn/Episode/Contribution 唯一约束、并发一次推进和 Sandbox 稳定幂等。
