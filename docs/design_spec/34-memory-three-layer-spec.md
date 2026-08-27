# 三层记忆技术规格 v4（Working / Episodic / Semantic）

> 维护：Agent；上游架构裁决以 [02-memory-design.md](../design/02-memory-design.md) 为准。
>
> 状态：**Approved v4 / 目标架构，尚未实施**
>
> 代码核对基线：`b309952`（2026-08-27）

v4 直接取代旧三层记忆规格。实现时不保留旧行为、双写、历史数据回填或运行时兼容分支；开发库允许重建，失效代码和表在对应模块交付时直接删除。

## 0. 范围与当前事实

本规格只解决四件事：会话内状态可恢复、历史经历可召回、长期画像分轨、正式评估与练习的读取隔离。不设计通用记忆平台，也不新增租户治理、复杂权限或可配置策略框架。

已核对当前 JPA 实体、Flyway 表和调用链：

| 当前事实 | v4 结论 |
|---|---|
| `agent_sessions` 有 JD/简历快照、状态、轮次和 `@Version`，没有会话模式或候选人级别 | 补齐会话初始化输入 |
| `agent_plans` 有维度、focus、skill 和轮次预算，没有期望深度与追问上限 | 扩展为不可变初始计划 |
| `WorkingMemorySnapshot` 每轮从 PostgreSQL 临时组装 | 替换为持久化 WorkState |
| `BoundedReActRuntime` 可直接执行模型返回的工具动作 | 改为代码先裁决并保存 Intent，再执行 |
| turn、assessment、evidence、probe gap 和 tool result 已形成事实链 | 继续作为 Episode 的来源 |
| 正式 Planner 当前读取历史 topic/claim，Interviewer 预先读取历史评级和标签 | 两条正式历史注入链删除 |
| `candidate_memory_episode_facts` 已按 answered turn 唯一，enrichment 有明确状态 | 保留并扩展 |
| ability counter/profile 只有单轨 TopicKey 画像 | 替换为正式能力与练习掌握度双轨 |
| `practice_records` 是面试后推荐记录，不是可交互的练习会话 | 不作为练习记忆来源 |

PostgreSQL 是唯一事实源。Redis 继续只用于现有异步 Stream 或缓存，重启后必须能从 PostgreSQL 恢复。

## 1. 会话模式与初始化

```text
MemoryOwner    = (tenantId, candidateId)
TopicKey       = (skillId, focusId)
SessionMode    = EVALUATION | PRACTICE
CandidateLevel = INTERN | CAMPUS | EXPERIENCED
```

`SessionMode` 创建后不可变。`agent_sessions` 增加 `mode`、`candidate_level` 和 `practice_scope_json`；JD、简历和 level 均保存本次创建时的快照。

- `EVALUATION` Planner 只读取本场 JD、简历、候选人级别和总预算，禁止读取任何候选人历史。
- `PRACTICE` 必须由用户明确选择 `practice_scope_json` 中的 TopicKey；Planner 只读取 scope 内 Semantic planning view，历史画像不能扩大 scope。
- 简历更新只影响之后创建的 session。旧 Episode 不改写，scope 外的 Semantic 不注入本次 Planner。
- Planner 只生成结构化计划提案；代码校验总预算、岗位深度和 TopicKey 后一次保存计划与 WorkState v1。

```text
CapabilityTarget
  targetId / TopicKey / dimensionOrder
  expectedDepth / depthCeiling
  turnBudget / followUpBudget / toolBudget
  evidenceObjectives[]: description / method(CANDIDATE_ANSWER | TOOL_FACT)
```

默认档位 `(expectedDepth, depthCeiling, maxFollowUps)` 为 `INTERN(L1,L2,1)`、`CAMPUS(L2,L3,2)`、`EXPERIENCED(L3,L4,3)`；JD 只决定 Topic 和本场证据目标，不能突破级别上限。工具预算等于 `TOOL_FACT` 目标数；历史薄弱点不能改变正式面试的模块、预算或深度。

## 2. Working Memory：本场怎么做

### 2.1 WorkState

```text
InterviewWorkState
  sessionId / revision
  phase: READY_TO_DECIDE | ACTION_PENDING | AWAITING_ANSWER | FINISHED
  targets[]: initial budget + remaining budget + currentDepth + target status
  activeTargetId / attentionFocus
  activeEvidenceRefs[]
  openIssues[]: issueId / targetId / evidenceMethod / anchor / missingPoint / status / closeReason
  awaitingAnswerTurnIndex? / awaitingIssueId? / activeActionIntentId?
issue status = OPEN | INVESTIGATING | RESOLVED | ABANDONED; target status = PENDING | ACTIVE | COMPLETED | EXHAUSTED
```

WorkState 是当前运行状态的唯一写源。Plan 只保存初始化结果，不在运行中重复维护剩余预算和状态。

### 2.2 最小存储

| 表 | 职责 |
|---|---|
| `agent_sessions` | 模式和本场输入快照 |
| `agent_plans` | 不可变初始 Target、证据目标和预算 |
| `agent_work_states` | session 1:1；revision、phase、`state_json`、active intent、乐观锁 |
| `agent_work_state_patches` | source、base/result revision 和 typed operations JSON |
| `agent_action_intents` | ASK/CALL_TOOL 的执行与恢复状态 |

`state_json` 由明确的 Java record 序列化，不允许角色提交任意 JSON path。关系表不再为 issue、evidence ref 和预算各拆一张表；这些数据只按 session 整体读取，拆表没有实际查询收益。

### 2.3 Typed Patch

```text
WorkStatePatch
  patchId / sessionId / baseRevision / resultRevision
  sourceType: INITIALIZATION | ASSESSMENT | TOOL_RESULT | POLICY | ACTION_RESULT
  sourceId / operations[]

operations:
  ADD_EVIDENCE_REF
  OPEN_ISSUE / CLOSE_ISSUE
  UPDATE_TARGET_DEPTH
  SET_FOCUS
  CONSUME_BUDGET
  SWITCH_TARGET
  SET_PENDING_ACTION
  APPLY_ACTION_RESULT / COMPLETE_ANSWER
  FINISH_SESSION
```

- Agent 只能返回 typed proposal；application 将 proposal 映射为上述操作。
- Reducer 是纯 Java 代码：验证 `baseRevision`、状态迁移和预算，返回新的不可变 WorkState。
- application 在一个短事务中保存 Patch 和新 WorkState；`resultRevision = baseRevision + 1`。
- 同一 `(sessionId, sourceType, sourceId)` 只应用一次；revision 冲突直接失败并重新读取，不覆盖新状态。
- Assessor、Policy、ActionResult 分别形成独立 Patch；Assessor 提议 currentDepth、evidence 和 issue 变化，工具结果只能关闭客观事实 issue，不能单独证明候选人能力。

### 2.4 确定性下一动作

`NextActionPolicy` 只读取完整 WorkState，按固定顺序返回动作：

1. `AWAITING_ANSWER`：不产生新动作；`ACTION_PENDING`：恢复原 Intent。
2. 当前深度达到 `expectedDepth` 且没有必需的 open issue：完成 Target 并 `SWITCH_TARGET`。
3. 达到 `depthCeiling` 或追问预算用完：放弃未闭环 issue，将 Target 置为 `EXHAUSTED` 并切换。
4. 候选人回答类 issue 未闭环且有预算：`ASK`。
5. 工具事实类 issue 未闭环且有工具预算：`CALL_TOOL`。
6. Target 仍有轮次预算：`ASK`；全部 Target 终态：`FINISH`。

`SWITCH_TARGET` 和 `FINISH` 是本地 Patch，直接应用。只有会生成问题或调用工具的外部动作使用 ActionIntent。

### 2.5 ActionIntent

```text
ActionIntent
  intentId / sessionId / basedOnRevision
  type: ASK | CALL_TOOL
  targetId / issueId?
  payloadJson / idempotencyKey
  status: PLANNED | EXECUTING | SUCCEEDED | APPLIED | FAILED
  resultType? / resultRef? / error? / executionStartedAt? / timestamps / version
```

- ASK Intent 固化 TargetEnvelope；CALL_TOOL Intent 固化工具名和已校验参数。
- CALL_TOOL 的参数提案不执行工具。application 重读 `basedOnRevision`、校验工具白名单和预算后，保存 Intent 与 `SET_PENDING_ACTION` Patch。
- `SUCCEEDED` 表示最终 question 或 tool result 已落库；`APPLIED` 表示 ActionResult Patch 已更新 WorkState，两步分开，避免状态冲突丢失结果。
- 工具以 Intent 的 `idempotencyKey` 执行。恢复任务重用同一个 Intent 和 key；已存在 result 时只补 Patch，不重复产生副作用。
- ASK 草稿不会展示；最终 question 与 turn 落库后才标记 `SUCCEEDED`，随后 Patch 进入 `AWAITING_ANSWER`。
- 失败保存 `FAILED + error` 并保留失败现场；只有显式重试才以相同 payload 创建新 Intent，不吞错、不生成替代问题或假工具结果。

### 2.6 角色视图

| 角色 | 只允许看到 |
|---|---|
| Planner | 本次初始化输入；练习模式额外含 scope 内 Semantic planning view |
| Interviewer/Coach | 当前 Target、issue、本场相关题答和允许工具 |
| Assessor | 当前问题、当前回答、量规和本轮工具事实 |
| Tool Router | 已裁决 CALL_TOOL、参数约束、issue 和剩余工具预算 |

角色使用独立 DTO/port。禁止把 Entity、完整 WorkState 或通用 MemoryReader 直接注入角色。

## 3. Episodic Memory：过去发生了什么

### 3.1 Episode 事实

每个已回答 turn 在 assessment、evidence、gap 和 tool result 落库的同一短事务中创建一个 Episode：

```text
EpisodeFact
  episodeId / MemoryOwner / sessionId / sessionMode / turnIndex
  TopicKey / targetId
  turnId / assessmentId / workRevisionBefore / workRevisionAfter
  assistanceLevel: NONE | FOLLOW_UP | HINT | TOOL_ASSISTED
  closureStatus: RESOLVED | UNRESOLVED | ABANDONED
  correctsEpisodeId? / createdAt
```

question、answer、评级、evidence、gap 和 tool result 通过稳定 ID 回到原始事实，不复制一份可漂移的结论。现有原地替换 Assessment 和已发布 question 的行为删除；后续纠正必须创建新 turn/Episode，并用 `correctsEpisodeId` 回连旧 Episode。

摘要、错误模式、回答习惯和 embedding 是 enrichment，不是 Episode 事实。它们可以异步更新并显式记录失败，但正式评估角色不能读取。

### 3.2 出题曝光与召回视图

新增 `agent_question_exposures`，在问题真正展示时保存：

```text
QuestionExposure
  exposureId / MemoryOwner / sessionId / turnId / episodeId?
  TopicKey / evidenceObjective / probeDepth / difficulty
  questionText / scenarioFingerprint / wordingFingerprint
  sourceExposureId? / sourceEpisodeId? / embeddingDocumentId? / askedAt
```

同一知识点允许复测，题目文本不允许简单复用。召回提供两个明确视图：

- `EvaluationRecallView`：question、场景 fingerprint、TopicKey、证据目标、难度和相似度；若历史正式 Episode 未闭环，只输出“需要重新验证什么”，不输出旧答案、评级或标签。
- `PracticeDiagnosticView`：完整题答、评级、证据、gap、工具、辅助级别和闭环状态。

正式 Interviewer 必须先生成 draft，再按同 MemoryOwner + TopicKey 召回。Practice Episode 可以参与题目去重，但不能成为正式弱项重验依据。

### 3.3 换场景流程

```text
生成 QuestionDraft
  → 校验本场 TargetEnvelope
  → 召回相似 QuestionExposure / 中性未闭环提示
  → 不重复则发布
  → 重复则保持知识点、难度、证据目标，改场景、约束或验证方式
  → 再次校验和召回
  → 保存 QuestionExposure + turn，完成 ASK Intent
```

只换措辞仍判定为重复。重写不能增加深度和预算；无法在本轮既有 deadline 内得到合格题目时明确失败。

向量召回复用 PostgreSQL `vector_store`，通过 metadata 的 document type 和 exposure ID 区分候选人题目；`agent_question_index` 继续只服务题库。

## 4. Semantic Memory：长期可以相信什么

### 4.1 双轨模型

```text
SemanticTrack = EVALUATED_CAPABILITY | PRACTICE_MASTERY
SemanticContribution
  contributionId / episodeId / MemoryOwner / TopicKey / track
  evaluationLevel? / practiceOutcome? / assistanceLevel? / createdAt
SemanticState
  MemoryOwner / TopicKey / track / revision
  statistics / abilityOrMastery / stablePatterns
  transferStatus? / confirmedByEpisodeId? / updatedAt / version
```

- Evaluation Episode 只产生 `EVALUATED_CAPABILITY` contribution。
- Practice Episode 只产生 `PRACTICE_MASTERY` contribution。
- contribution 不修改；SemanticState 是可重算的当前投影。
- 只新增 `candidate_semantic_contributions` 和 `candidate_semantic_states` 两张表，不建立快照、outbox、policy version 或通用知识图谱。

### 4.2 确定性聚合

正式能力沿用现有公式：

```text
weighted = 0*L0 + 1*L1 + 2*L2 + 3*L3 + 4*L4
无样本                  → 不生成状态
weighted >= 3 * total   → PROFICIENT
weighted >= 2 * total   → COMPETENT
otherwise               → WEAK
```

练习掌握度取同 TopicKey 最新一次有效练习：无辅助完成为 `INDEPENDENT`，追问/提示/工具后完成为 `ASSISTED`，未完成为 `UNRESOLVED`；同时保留各 assistance level 的累计次数。

练习状态更新后 `transferStatus=NOT_REEVALUATED`。之后同 TopicKey 的正式 Episode 达到练习目标深度，更新为 `CONFIRMED`，否则为 `REGRESSED`；正式能力本身只由正式 contribution 计算。

稳定错误模式和回答习惯来自至少 `MIN_PATTERN_EPISODES=2` 个不同 Episode 的同类 enrichment；LLM 只提标签，代码按标签和来源计数。没有足够来源时只保留 Episode 标签，不写 Semantic stable pattern。

### 4.3 消费边界

| 消费者 | Episode | Semantic |
|---|---|---|
| Evaluation Planner | 禁止 | 禁止 |
| Evaluation Interviewer | draft 后的中性召回 | 禁止 |
| Evaluation Assessor | 禁止历史 | 禁止 |
| Practice Planner | 禁止 | scope 内 planning view |
| Practice Coach/Interviewer | Target 固定后的完整诊断 | 当前相关弱项 |
| Practice Assessor | 禁止历史评级 | 禁止 |
| 报告/推荐 | 可追溯来源 | 双轨趋势 |

两种模式都自动生产 Episode 和 Semantic。这里的“Agent 维护记忆”是：角色提出结构化结果，application 通过确定性 reducer 和聚合器持续落库；不是让模型直接读写数据库或自由改画像。

## 5. 写入与恢复

1. 初始化：事务外生成 Plan proposal；短事务保存 session、plan 和 WorkState v1。
2. 回答：事务外评估；短事务保存 answer、assessment/evidence/gap、Assessment Patch、Episode 和 Semantic contribution。
3. 决策：读取 WorkState 并运行 policy；本地动作直接 Patch，ASK/CALL_TOOL 先保存 Intent 和 Pending Patch。
4. 外部结果：事务外生成问题或调用工具；短事务保存 final question/tool result 并置 Intent `SUCCEEDED`。
5. 应用结果：独立短事务应用 ActionResult Patch 并置 `APPLIED`。
6. Target 或会话结束：按新增 contributions 重算受影响的 SemanticState；enrichment 和向量索引继续异步执行。

恢复只处理持久化 Intent：

| 状态 | 恢复动作 |
|---|---|
| `PLANNED` | 执行原 Intent |
| 超时 `EXECUTING` | 使用同一 idempotency key 重新执行或读取已有结果 |
| `SUCCEEDED` | 按 resultRef 补 ActionResult Patch |
| `APPLIED` | 不处理 |
| `FAILED` | 暴露错误，由显式重试创建新 Intent |

## 6. Redis 持久化机制场景

正式评估根据本次 JD 和校招量规规划出 `Redis / persistence`，当前未闭环 issue 是 RDB fork/COW。Interviewer 先草拟“解释 BGSAVE 的 fork 过程”，再召回历史曝光，发现相同问题考过且当时未闭环。系统不改模块、预算和深度，只返回中性重验证目标，并把题目改为：“实例持续写入时执行 BGSAVE，内存为何突增，哪些条件会放大该现象？”Assessor 只看本场回答；本轮新增 Evaluation Episode 和正式能力 contribution。

练习模式由 scope 内 Semantic planning view 选择 fork/COW。Target 固定后，Coach 才读取历史 Episode 的旧回答、评级和 gap，进行连续追问、提示演示和无提示复测。每轮 assistance level 写入 Episode，更新练习掌握度；正式能力不变，直到之后的独立正式评估确认能力迁移。

## 7. 完成标准与切换原则

1. WorkState 可从 PostgreSQL 恢复，所有变化都有 typed Patch，revision 单调递增。
2. ASK/CALL_TOOL 在外部执行前已有 Intent；重启后不会生成第二个问题或重复工具副作用。
3. 每个已回答 turn 恰有一个 Episode；已展示未回答的问题也能参与去重。
4. 正式 Planner 请求中没有历史；正式 Assessor 在两种模式下都看不到历史评级。
5. 正式 Interviewer 只在 draft 后获得中性召回，重写保持 TargetEnvelope。
6. 练习可使用完整 Episode 定向训练，但本轮评级只依据本轮事实。
7. Evaluation/Practice contributions 不跨轨，练习结果不覆盖正式能力。
8. 所有长期查询带 MemoryOwner 和 scope；工具结果不能单独标记能力满足。
9. 单元测试覆盖 reducer、policy、聚合和召回投影；PostgreSQL 集成测试覆盖 revision、幂等和唯一约束。
10. 不做历史数据迁移和兼容：删除旧 Working snapshot 读链、正式历史注入链、单轨 profile/counter 实现及失效表；不保留 legacy 枚举、双写或 runtime 分支。
