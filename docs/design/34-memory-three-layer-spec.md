# 记忆系统三层改造 Spec（Working / Episodic / Semantic）

> 状态：**Approved v3**（2026-08-23，阻断项已关闭，作为实现事实源）
>
> 权威输入：[01-platform-design.md](./01-platform-design.md) §7、[10-text-interview.md](./10-text-interview.md) §5、2026-08-23 事实校对与关闭裁决
>
> 最后更新：2026-08-23

## 0. 核心裁决

- 所有长期记忆按 `MemoryOwner(tenantId, candidateId)` 隔离。
- 主题稳定身份是 `TopicKey(skillId, focusId)`；`suggestedSkill` 映射为 `skillId`。
- `dimension`、`focus` 仅为当前计划的展示文本，不参与聚合、去重或迁移猜测。
- 同一计划禁止重复 `TopicKey`，由确定性代码校验。
- Working Memory 是 PG 事实组装出的不可变快照，第一期不引入 Redis。
- 每个已回答 turn 同步建立一条 `EpisodeFact`；LLM 只异步补充摘要和标签。
- `Assessment` 是能力等级的唯一事实源；Episode 引用它，不复制等级。
- Semantic 能力由 L0~L4 累计计数器确定，LLM 不参与能力定级。
- 历史记忆可影响出题，不能进入 Assessment Agent，也不能进入评级链路。

## 1. 范围与三层边界

| 层 | 职责 | 生命周期 | 权威来源 |
|---|---|---|---|
| Working | 当前轮决策所需上下文 | 单次编排调用 | session / plan / turn / assessment / probe gap |
| Episodic | 可追溯的历史一问一答事件 | 长期 | answered turn + assessment |
| Semantic | 跨场稳定能力与结构化行为统计 | 长期 | assessment counters + normalized tags |

不在范围：题库向量去重、企业资产记忆、自由文本画像参与评分、Redis Working Memory。

## 2. 稳定身份与所有权

```text
MemoryOwner = (tenantId, candidateId)
TopicKey    = (skillId, focusId)
```

- 所有 Episode、Counter、Profile、Tag 查询必须同时包含 owner。
- `focusId` 可跨 skill 重复，禁止单独作为主题身份。
- plan 创建时将 `suggestedSkill` 规范化为 `skillId`，并拒绝重复 `TopicKey`。
- tenant 为空沿用现有候选人归属语义，不用字符串哨兵值替代。

## 3. Working Memory

### 3.1 快照

每次生成下一题前构造：

```text
WorkingMemorySnapshot
  sessionId
  currentTurnIndex
  currentTopic: TopicKey
  selectedGap: ProbeGap?
  followUpDepth
  trigger: TurnTrigger
```

快照不可持久化、不可变、只由 application 层组装并传给 Planner/Interviewer。候选人声明不进入快照；Planner 继续单独使用现有 `CoveredTopic` 与 `UnverifiedClaim`。

### 3.2 ProbeGap

Assessment 产生的 gap 落入 `agent_assessment_probe_gaps`：

```text
id / assessment_id / gap_order / gap_code / description / created_at
```

- `(assessment_id, gap_order)` 唯一。
- 编排器按 `gap_order ASC, id ASC` 选择第一条尚未使用且属于当前主题的 gap。
- gap 是结构化追问依据；LLM 不能决定 trigger 或父子关系。

### 3.3 Turn provenance

`AdaptiveInterviewTurn` 新增：

```text
trigger_type: PLANNED | ASSESSMENT_GAP | TOOL_RESULT
parent_turn_index: nullable
source_assessment_id: nullable
source_tool_result_event_id: nullable
```

约束：

- `PLANNED` 不得有 source；`ASSESSMENT_GAP` 必须引用 assessment；`TOOL_RESULT` 必须引用 tool event。
- `parentTurnIndex < turnIndex`，且父 turn 必须属于同一 session。
- `followUpDepth` 从 parent 链确定性计算，根问题为 0。
- 只有产生候选人回答的 turn 才形成 Episode；纯工具执行不形成 Episode。

## 4. Episodic Memory

### 4.1 同步事实

每个 answered turn 在保存 turn 与 assessment 的同一个短事务中创建：

```text
candidate_memory_episode_facts
  id / tenant_id / candidate_id
  session_id / turn_index / assessment_id
  skill_id / focus_id
  enrichment_status / enrichment_error
  created_at / updated_at
```

- `(session_id, turn_index)` 唯一。
- 不复制 question、answer、trigger、parent、depth 或 assessment level；均从权威实体关联读取。
- `assessment_id` 指向可被异步判题原地修正的 Assessment。
- `answerSummary` 与 `outcome` 不属于同步事实；`outcome` 字段彻底删除。UI 通过父子 Episode 组合追问卡片。

### 4.2 异步 enrichment

```text
status = PENDING | PROCESSING | COMPLETED | FAILED | LEGACY_UNENRICHED
payload = answer_summary + normalized tags
```

流程：事务提交后唤醒 worker；LLM 调用在事务外；结果在独立短事务中替换写入。失败必须保存 `FAILED` 与错误，不得写空摘要或假成功。普通失败只允许显式重试；超时的 `PROCESSING` 可原子恢复为 `PENDING`。重复执行采用替换语义，结果幂等。

### 4.3 结构化标签

标签关系表记录 `episode_id / category / tag / source_type / source_id`。

- category：`ERROR_PATTERN | ANSWER_HABIT`。
- source：`ASSESSMENT_EVIDENCE | PROBE_GAP | TOOL_RESULT`，且必须属于该 Episode 的权威关系链。
- 非法单个标签丢弃并记录日志；合法标签仍可落库。

错误模式枚举：

```text
MISSING_FAILURE_BOUNDARY
MISSING_CONCURRENCY_ANALYSIS
MISSING_CONSISTENCY_ANALYSIS
UNSUPPORTED_ASSUMPTION
CONFUSES_CONCEPTS
IGNORES_COMPLEXITY_COST
INCOMPLETE_CORRECTNESS_ARGUMENT
OVERGENERALIZES_SOLUTION
```

回答习惯枚举：

```text
CONCLUSION_WITHOUT_EVIDENCE
EXAMPLE_WITHOUT_METRICS
IMPLEMENTATION_WITHOUT_TRADEOFF
VAGUE_PERSONAL_OWNERSHIP
OVERLY_ABSOLUTE_LANGUAGE
STRUCTURED_REASONING
SELF_CORRECTS_AFTER_PROBE
EXPLICIT_BOUNDARY_ANALYSIS
```

## 5. Semantic Memory

### 5.1 能力计数器

`candidate_ability_counters` 按 owner + TopicKey 唯一，保存 `l0_count` 至 `l4_count` 与乐观锁版本。

```text
total    = l0 + l1 + l2 + l3 + l4
weighted = 0*l0 + 1*l1 + 2*l2 + 3*l3 + 4*l4

total == 0              -> 不生成 Profile
weighted >= 3 * total   -> PROFICIENT
weighted >= 2 * total   -> COMPETENT
otherwise               -> WEAK
```

不使用置信度加权、滑动窗口或 LLM 裁决。计数器任何时刻不得为负。

### 5.2 Profile 快照

完成会话时为本场涉及的每个 TopicKey 生成不可变 profile：

```text
owner / TopicKey / ability
l0..l4 count snapshot
source_session_id
revision_reason: SESSION_COMPLETED | ASSESSMENT_CORRECTED
superseded_at / created_at
```

新快照 supersede 同 owner + TopicKey 的旧 current 快照。Profile 不保存单一 `sourceAssessmentId`；贡献通过 Episode → Assessment 追溯。API 将 profile 与独立统计的标签计数组合返回。

### 5.3 异步判题补偿

Assessment 等级从 old 修正为 new 时，在一个短事务内：

1. `old_count - 1`、`new_count + 1`；旧计数不足则失败并暴露数据错误；
2. 保持 Episode 的 assessment 引用不变；
3. 清理旧 enrichment 标签并将 Episode 重置为 `PENDING`；
4. 若 session 已完成，生成 `ASSESSMENT_CORRECTED` profile 并 supersede 旧 current。

## 6. Prompt 公平性防火墙

- Planner：只保留现有 `CoveredTopic` / `UnverifiedClaim`，本期不接 Profile 或 Episode。
- Interviewer：只可接收 `EpisodePromptFact(skillId, focusId, DepthLevel, errorTags, answerHabitTags, createdAt)`。
- Assessment：禁止任何历史 Episode、Profile、Counter 或标签输入。
- 历史 question、answer、summary、rationale、evidence quote、DimensionBrief 均禁止进入 prompt。

Episode 选择规则：

1. 仅已完成历史 session，排除当前 session；
2. 同 TopicKey 优先，其次同 skill；不取其他主题；
3. 各组内 `createdAt DESC, id DESC`；
4. 按序追加 JSON 条目，最终不超过 2000 tokens；单条放不下则跳过并继续；
5. 现有全局 `maxInputTokens = 12_000` 校验仍为硬失败。

## 7. 写入时序

### 7.1 正常答题

事务外：评估回答 → 校验模型输出 → 构造 WorkingMemorySnapshot → 生成下一题。

短事务：完成 turn → 保存 assessment/evidence/gaps → 创建下一 turn provenance → 创建 EpisodeFact(PENDING) → 增加 Counter；若会话完成，同时生成 Profile 快照。

提交后：唤醒 Episode enrichment。唤醒失败可见且可重试，不回滚已提交事实。

### 7.2 DimensionBrief

DimensionBrief 继续服务当前 session 的维度导航；Episode/Profile 服务跨场记忆和候选人报告。二者并存，互不替代，brief 不进入历史 prompt。

## 8. 历史迁移

- 通过 `source_session_id + dimension_order` 回连旧 plan，取得 `skillId/focusId`；缺 plan 时迁移失败。
- 禁止根据 dimension/focus 展示文本猜 TopicKey。
- 为历史 answered turn 回填 EpisodeFact，状态为 `LEGACY_UNENRICHED`，不调用 LLM 补摘要或标签。
- 从历史 Assessment 一次性聚合 Counter。
- 旧 Profile 回填 TopicKey 后，为每个主题生成 counter-v1 current 快照并 supersede 旧 current。
- 迁移必须可重复执行，并以唯一约束证明幂等。

## 9. 验收不变量

1. 任一 answered turn 恰有一个 EpisodeFact，且能追到当前 Assessment。
2. 任一计数器等于该 owner + TopicKey 下有效 Assessment 的 L0~L4 分布。
3. Assessment 修正后 Counter/Profile/标签与新等级一致，计数不为负。
4. Assessment prompt 中不存在历史记忆；Interviewer 历史输入只含白名单字段。
5. 不同 tenant/candidate 的记忆无法交叉查询。
6. 相同业务请求重放不产生重复 Episode、Counter 增量、Tag 或 Profile current。
7. enrichment 失败明确可见，不伪装为成功。
8. 历史迁移缺少稳定映射时明确失败，不使用展示文本猜测。
