# 三层记忆实现 Tickets

> 维护：Agent；上游设计决策以 `docs/design/` 为准。
>
> 状态：**Superseded（2026-08-27）**
>
> 历史基线：34 号规格 v3。34 号现已由 v4 目标架构直接取代，以下票据仅保留审计记录，不得继续执行。

## T01 TopicKey 与计划唯一性

**Goal**：用 `(skillId, focusId)` 作为稳定主题身份，并拒绝同一计划内重复主题。
**怎么实现**：新增纯领域 `TopicKey`；在 plan 创建边界完成 `suggestedSkill → skillId` 映射和唯一性校验。
**测试验证**：`TopicKeyTest` 覆盖值语义；`InterviewPlanTest` 覆盖跨 skill 同 focus 合法、同 pair 重复失败。
**依赖**：无。

## T02 TurnTrigger 领域约束

**Goal**：由代码表达 `PLANNED / ASSESSMENT_GAP / TOOL_RESULT` 及合法 source 组合，并让评估追问精确指向具体 gap。
**怎么实现**：新增 trigger 枚举和值对象，`ASSESSMENT_GAP` 把 assessment ID 与 probe gap ID 作为不可拆分来源，构造时校验 source 互斥与必填规则。
**测试验证**：`TurnTriggerTest` 覆盖三种合法组合、Assessment/gap 任一非法及 source 混用。
**依赖**：无。

## T03 Turn provenance 持久化

**Goal**：turn 可追溯父 turn、来源 assessment + probe gap 或 tool event。
**怎么实现**：扩展 turn entity/domain/mapper/repository，增加 `source_probe_gap_id`；保存前验证 gap 属于 source assessment、未被其他 turn 使用，且父索引属于同 session 并更小。
**测试验证**：`AdaptiveInterviewTurnEntityTest` 验证 assessment/gap 字段往返；`AdaptiveInterviewPersistenceServiceTest` 验证非法父链失败；数据库迁移测试验证来源组合、归属和 gap 唯一消费约束。
**依赖**：T02。

## T04 ProbeGap 领域模型与表

**Goal**：Assessment gaps 成为可查询的 PG 事实。
**怎么实现**：新增 gap entity/repository/mapper，唯一键 `(assessment_id, gap_order)`，由评估落库事务批量保存。
**测试验证**：`AssessmentProbeGapRepositoryTest` 验证顺序、唯一约束和 assessment 隔离。
**依赖**：无。

## T05 ProbeGap 确定性选择

**Goal**：编排器稳定选择第一条可用 gap。
**怎么实现**：纯领域 selector 按 `gapOrder,id` 排序，以 turn provenance 的 `sourceProbeGapId` 过滤已使用 gap，再过滤非当前 TopicKey；不得按 assessment 整体过滤。
**测试验证**：`ProbeGapSelectorTest` 覆盖乱序、同一 Assessment 仅排除已用 gap、跨主题和无可选 gap。
**依赖**：T01、T04。

## T06 WorkingMemorySnapshot

**Goal**：下一题决策只接收不可变工作记忆快照，不引入 Redis。
**怎么实现**：新增 snapshot record 与 assembler；从 PG 聚合和裁决结果计算 trigger、selectedGap、parent 链深度。
**测试验证**：`WorkingMemoryAssemblerTest` 覆盖根问题、gap 追问、多级父链和 tool trigger。
**依赖**：T03、T05。

## T07 EpisodeFact 表与仓储

**Goal**：持久化每个 answered turn 的最小权威索引。
**怎么实现**：新增 EpisodeFact entity/domain/repository，唯一键 `(session_id, turn_index)`；不复制题答、等级和 provenance。
**测试验证**：`EpisodeFactRepositoryTest` 验证保存查询、owner 隔离和重复键失败。
**依赖**：T01。

## T08 EpisodeFact 同事务写入

**Goal**：完成 turn 时同步创建唯一 EpisodeFact(PENDING)。
**怎么实现**：接入 `AdaptiveInterviewPersistenceService` 的短事务；使用业务幂等键，不异步创建事实。
**测试验证**：`AdaptiveInterviewPersistenceServiceTest` 验证 turn/assessment/episode 原子性及请求重放不重复。
**依赖**：T07。

## T09 AbilityCounter 表与定级算法

**Goal**：建立 owner + TopicKey 的 L0~L4 累计计数与确定性三级能力。
**怎么实现**：新增 counter entity/repository 和纯领域 calculator；使用乐观锁，禁止负数。
**测试验证**：`AbilityCounterTest` 覆盖阈值边界、total=0、增减和负数失败；repository 测唯一键。
**依赖**：T01。

## T10 新 Assessment 增量计数

**Goal**：EpisodeFact 创建时恰好增加一次对应等级计数。
**怎么实现**：在同一 persistence 事务内 upsert counter，以 Episode 唯一创建结果控制是否增量。
**测试验证**：`AdaptiveInterviewPersistenceServiceTest` 验证五级增量、跨主题隔离和重放不重复计数。
**依赖**：T08、T09。

## T11 异步判题计数补偿

**Goal**：Assessment 等级替换后旧等级 -1、新等级 +1。
**怎么实现**：在判题结果短事务中锁定 counter 并补偿；旧计数不足直接失败。
**测试验证**：`AssessmentReconciliationServiceTest` 覆盖同级 no-op、跨级补偿、下溢失败和并发版本冲突。
**依赖**：T10。

## T12 Episode enrichment 状态机

**Goal**：明确管理 PENDING/PROCESSING/COMPLETED/FAILED/LEGACY_UNENRICHED。
**怎么实现**：纯领域状态迁移；记录 error；超时 PROCESSING 可回 PENDING，FAILED 仅显式重试。
**测试验证**：`EpisodeEnrichmentStateTest` 穷举合法与非法迁移、错误保留和重试语义。
**依赖**：T07。

## T13 标签枚举与来源校验

**Goal**：只保存 spec 白名单标签及属于 Episode 的权威 source。
**怎么实现**：新增 tag entity/repository、两个枚举和 source validator；非法单标签记录并丢弃。
**测试验证**：`EpisodeTagValidatorTest` 覆盖三类 source、跨 Episode 拒绝、混合合法/非法标签。
**依赖**：T04、T07。

## T14 Enrichment worker

**Goal**：事务外调用 LLM，短事务幂等替换摘要和标签，失败显式落库。
**怎么实现**：提交后生产任务；worker claim 状态、调用结构化输出、验证标签并 replace；不制造空成功。
**测试验证**：`EpisodeEnrichmentServiceTest` 覆盖成功、LLM 异常、重复消费、非法标签和事务外调用。
**依赖**：T12、T13。

## T15 Assessment 修正触发再 enrichment

**Goal**：异步判题修正后清除旧标签并重新进入 PENDING。
**怎么实现**：计数补偿事务内删除旧 tag contribution、清空摘要状态并登记 after-commit 任务。
**测试验证**：`AssessmentReconciliationServiceTest` 验证 Episode 引用不变、旧标签消失及单次重排队。
**依赖**：T11、T14。

## T16 Profile 快照模型

**Goal**：按 owner + TopicKey 保存不可变能力快照与 supersede 链。
**怎么实现**：迁移旧 profile 身份和来源字段，加入 count snapshot、revision reason、current 唯一语义。
**测试验证**：`CandidateAbilityProfileRepositoryTest` 验证 current 唯一、历史保留、owner 隔离和完整计数快照。
**依赖**：T09。

## T17 会话完成生成 Profile

**Goal**：完成 session 时为涉及主题生成 SESSION_COMPLETED 快照。
**怎么实现**：在完成事务中批量读取 counters，跳过 total=0，生成新 current 并 supersede 旧快照。
**测试验证**：`AdaptiveInterviewPersistenceServiceTest` 覆盖多主题、重复完成幂等、阈值结果和 supersede。
**依赖**：T10、T16。

## T18 Assessment 修正生成 Profile

**Goal**：已完成 session 的等级修正产生 ASSESSMENT_CORRECTED 快照。
**怎么实现**：补偿计数后仅对已完成 session 生成修订快照；进行中 session 不生成。
**测试验证**：`AssessmentReconciliationServiceTest` 覆盖完成/进行中分支、revision reason 和 supersede。
**依赖**：T11、T16、T17。

## T19 EpisodePromptFact 选择器

**Goal**：按同 TopicKey、同 skill、时间顺序选择白名单历史事实，最多 2000 tokens。
**怎么实现**：仓储只查 completed 且排除当前 session；纯选择器稳定排序、逐项计量、超长单项跳过。
**测试验证**：`EpisodePromptSelectorTest` 覆盖优先级、稳定排序、当前场排除、边界 token 和 skip-continue。
**依赖**：T13、T14。

## T20 Interviewer Prompt 接入与 Assessment 隔离

**Goal**：Interviewer 只收到 EpisodePromptFact，Assessment 完全不接历史记忆。
**怎么实现**：新增专用 prompt DTO/template section；保留全局 12000 token 硬校验；不复用持久化 DTO。
**测试验证**：prompt contract 测试断言白名单字段存在，question/answer/summary/rationale/evidence/brief 不存在；Assessment request 无历史字段。
**依赖**：T19。

## T21 候选人记忆查询 API

**Goal**：返回 Topic profile、等级分布、标签计数和 Episode 链，且严格 owner 隔离。
**怎么实现**：application query service 组合 profile/tag/episode；Controller 返回 `Result<Response>`，不暴露 entity。
**测试验证**：`CandidateMemoryControllerTest` 覆盖正常、空结果、跨 tenant/candidate 隔离与稳定排序。
**依赖**：T14、T17、T18。

## T22 候选人记忆 UI

**Goal**：展示能力主题、计数、标签及由 parentTurnIndex 组合的追问链。
**怎么实现**：API/types 集中定义；页面组件复用现有设计语言，明确显示 enrichment FAILED/未补全。
**测试验证**：组件测试覆盖链组合和状态展示；`cd frontend && pnpm run build` 通过。
**依赖**：T21。

## T23 历史数据迁移

**Goal**：确定性回填 TopicKey、LEGACY_UNENRICHED Episode、Counter 和 counter-v1 Profile。
**怎么实现**：只通过 session + dimensionOrder 回连 plan；缺映射失败；以唯一约束保证可重跑，不调用 LLM。
**测试验证**：migration 集成测试覆盖完整回填、重复执行、缺 plan 失败、同名展示文本不被误映射。
**依赖**：T07、T09、T16。

## T24 三层记忆不变量与发布门禁

**Goal**：以端到端证据证明 spec §9 八条不变量。
**怎么实现**：增加 persistence/application 集成测试与 prompt 快照检查，清除被替代的旧兼容路径。
**测试验证**：`timeout 60s ./gradlew :app:test --no-daemon --console=plain` 与 `cd frontend && pnpm run build`；逐条记录 §9 证据。
**依赖**：T01-T23。

## 交付顺序

```text
T01,T02,T04
  -> T03,T05,T07,T09
  -> T06,T08,T10,T12,T13,T16,T23
  -> T11,T14,T17
  -> T15,T18,T19
  -> T20,T21
  -> T22
  -> T24
```
