# 三层记忆 v4 实施 Tickets

> 维护：Agent
>
> 基线：[34-memory-three-layer-spec.md](./34-memory-three-layer-spec.md) v4
>
> 状态：**Ready for implementation**

## 0. 执行规则

- 按 ticket 顺序实施；每个 ticket 都必须形成可运行、可测试、无闲置新代码的完整模块，并单独提交。
- 数据库只写目标 schema DDL；不写历史回填、双写、legacy 枚举或 runtime 兼容分支，开发库直接重建。
- 同一 ticket 中删除被替换的旧类、旧字段、旧 Prompt 和死测试，不把清理债务留给后续模块。
- 优先修改现有 domain、application、persistence 和测试，不建立通用记忆框架或无实际消费者的扩展点。
- LLM 和工具调用保持在事务外；失败直接暴露，不增加兜底题目、mock 结果或吞错逻辑。

## 1. 交付顺序

| Ticket | 模块 | 交付结果 |
|---|---|---|
| T01 | Session / Plan | 模式、级别、练习 scope 和有深度边界的 Target |
| T02 | Working | WorkState、Typed Patch、确定性下一动作和角色视图 |
| T03 | ActionIntent | ASK/CALL_TOOL 先落 Intent 并可恢复 |
| T04 | Episodic Fact | 不可变 Episode 与纠正关系 |
| T05 | Episodic Recall | 题目曝光、中性/完整召回、换场景去重 |
| T06 | Semantic / Practice | 正式能力与练习掌握度双轨闭环 |
| T07 | Cleanup / E2E | 删除旧链路并通过完整验收 |

## T01 会话模式、Target 与初始化隔离

**Goal**：创建 session 时固定正式/练习模式、候选人级别和计划边界，正式 Planner 不再读取历史。

- API/domain：新增 `SessionMode(EVALUATION,PRACTICE)`、`CandidateLevel(INTERN,CAMPUS,EXPERIENCED)`、`PracticeScope(TopicKey[])`；扩展 request、command、session 和 response。
- 计划：`PlannedDimension` 改为不可变 CapabilityTarget，增加 evidence objectives、expected/depth ceiling、turn/follow-up/tool budget。
- 深度：`InterviewLevelProfile` 固化 `INTERN(L1,L2,1)`、`CAMPUS(L2,L3,2)`、`EXPERIENCED(L3,L4,3)`；tool budget 等于 `TOOL_FACT` objective 数。
- 存储：`agent_sessions` 增加 mode/level/scope；`agent_plans` 增加初始 Target 的 depth、evidence objectives 和预算字段。
- 复用：`PlanningTaxonomy` 继续校验 TopicKey；EVALUATION Planner 只收本场 JD/resume/level，PRACTICE 先按明确 request scope 规划，T06 再接入 scope 内 Semantic planning view。
- 删除：Planner 的 `coveredTopics/unverifiedClaims` 输入与相关正式读取；所有 session 都按正式面试处理的隐式假设。
- 前端：创建表单增加模式和 level；练习模式复用现有 skill catalog 选择 TopicKey scope，正式模式不展示 scope。
- 测试：API round-trip、模式不可变、scope 组合、三个 level 边界、总预算、未知 Topic、正式 PlanningRequest 不含历史；前端 build 通过。
- 依赖：无。提交：`feat: initialize memory aware interview sessions`。

## T02 持久化 WorkState、Typed Patch 与确定性策略

**Goal**：用一个 PostgreSQL WorkState 聚合维护本场状态，所有变化经 Patch，代码确定下一动作。

- domain：新增不可变 `InterviewWorkState`、TargetState、WorkIssue、EvidenceRef、Budget、phase/status 和 sealed `WorkStateOperation`。
- reducer：实现初始化、depth/evidence/issue、预算、切换、pending action、action result、answer complete 和 finish 操作；整份 Patch 失败则状态不变。
- 存储：新增 `agent_work_states`、`agent_work_state_patches`；`state_json` 使用明确 record codec，唯一来源事件，revision 每次 +1。
- 策略：`NextActionPolicy` 固定“等待/恢复 → 达标切换 → 上限耗尽 → 回答 issue → 工具 issue → 主问题 → 结束”的顺序。
- 集成：Plan 同事务创建 WorkState v1；Assessor proposal 映射为 Patch；SWITCH/FINISH 由 POLICY Patch 执行。
- 角色视图：新增 PlanningView、InterviewerView、AssessmentView、ToolRouterView；只传当前角色需要的字段。
- 删除：`WorkingMemorySnapshot` 全链、plan `completed_turns/status` 运行写入与字段、临时下一状态组装、Interviewer 出题前 `EpisodePromptFact` 注入和角色通用 MemoryReader。
- 测试：reducer 全操作、策略全分支、不可变性、来源幂等、revision 冲突、级别上限、历史变化不改变 AssessmentView、PostgreSQL 唯一约束。
- 依赖：T01。提交：`feat: drive interviews from persistent work state`。

## T03 ActionIntent 执行与恢复

**Goal**：ASK/CALL_TOOL 在外部执行前有持久意图，结果落库和 WorkState 应用可分别恢复。

- domain/storage：新增 ActionIntent、payload/status 状态机和 `agent_action_intents`；同 session 一个未完成 Intent，idempotency key 唯一，实体使用 `@Version`。
- 事务：Intent + `SET_PENDING_ACTION` Patch、result + `SUCCEEDED`、ActionResult Patch + `APPLIED` 分为三个短事务。
- ASK：Intent 固化 TargetEnvelope；问题草稿不展示，final question/turn 落库后才进入 `SUCCEEDED` 和 `AWAITING_ANSWER`。
- CALL_TOOL：先取得无副作用参数提案，再重读 revision、保存 Intent、经 ToolGateway 用同一 idempotency key 执行。
- 恢复：处理 PLANNED、超时 EXECUTING、SUCCEEDED；FAILED 保留现场，只接受显式 retry 创建新 Intent。
- 重构：`BoundedReActRuntime` 不再执行模型自由 ToolCall；保留的有界调用只生成问题或工具参数。
- 删除：Intent 前创建 turn/执行工具、结果缺失时新建 invocation、fallback question 和工具自动循环。
- 测试：Intent 先于调用、合法状态迁移、三段崩溃恢复、ASK 不重复展示、工具等效一次、失败显式。
- 依赖：T02。提交：`feat: persist and recover interview actions`。

## T04 不可变 Episode 事实

**Goal**：每个 answered turn 形成一条长期可审计 Episode，之后的事件不覆盖过去事实。

- 模型：扩展 EpisodeFact 的 mode、target、work revision、assistance、closure、correctsEpisodeId。
- 写入：answer、assessment/evidence/gap、Assessment Patch 和 Episode 在同一短事务；相同 turn 只创建一次，T06 在同一入口追加 Semantic contribution。
- 事实链：Episode 继续引用 turn/assessment/evidence/gap/tool result，不复制评级文本。
- 纠正：候选人纠正创建新 turn/Episode 并回连；晚到 ToolResult 形成新事实与 Patch，只影响后续动作。
- 删除：`replaceAssessment`、已发布 turn 的 `replaceQuestion`、ability reconciliation 补偿路径及其测试。
- 测试：一答一 Episode、纠正回连、assistance/closure、owner 隔离、晚到工具不改旧事实、事务回滚。
- 依赖：T03。提交：`feat: keep interview episodes immutable`。

## T05 题目曝光、双视图召回与换场景

**Goal**：Interviewer 出题后召回历史，重复时换场景；正式和练习读取严格不同的 Episode 字段。

- 模型/storage：新增 QuestionExposure、QuestionIdentity 和 `agent_question_exposures`；发布 question、exposure、turn 同事务，未回答题也可召回。
- 视图：`EvaluationRecallView` 只含中性题目/场景/目标/相似度和未闭环验证点；`PracticeDiagnosticView` 包含完整题答、评级、evidence、gap、tool 和 assistance。
- 向量：复用 `vector_store`，metadata 写 document type + exposure id；按 MemoryOwner + TopicKey 回表过滤，不复用 `agent_question_index`。
- 去重：`QuestionNoveltyPolicy` 在 draft 后运行；重复时保持 TopicKey、深度、难度和 evidence objective，必须改变场景、约束或验证方式后再次召回。
- 集成：final question + exposure + turn 完成 ASK Intent；正式未闭环提示只在本场已选 TopicKey 内生效，不改变计划与预算。
- 删除：正式 prompt 的 depthLevel/errorTags/answerHabitTags 历史链和所有已发布 question 原地替换入口。
- 测试：未回答曝光、双视图字段、ACCEPT/REWRITE、只换措辞拒绝、TargetEnvelope 不变、owner/topic 隔离、deadline 失败显式。
- 依赖：T04。提交：`feat: recall and rewrite repeated questions`。

## T06 Semantic 双轨与练习消费

**Goal**：正式评估和练习分别生产长期状态，只有练习实时消费长期画像和完整 Episode。

- 模型/storage：新增 SemanticTrack、Contribution、State、Ability/Mastery/Transfer 和两张表；Episode + track 唯一，state 按 MemoryOwner + TopicKey + track 唯一。
- 聚合：Evaluation 只写正式轨，Practice 只写练习轨；正式沿用 L0～L4 公式，练习按最新 assistance 结果得到 INDEPENDENT/ASSISTED/UNRESOLVED。
- 模式：同标签至少 `MIN_PATTERN_EPISODES` 个来源才进入 stable pattern；LLM 只提 enrichment 标签，不决定 ability/mastery。
- 练习读取：Planner 只看 request scope 内 Semantic planning view；Target 固定后 Coach/Interviewer 才读 PracticeDiagnosticView；Practice Assessor 仍只看本轮。
- transfer：新练习贡献置 NOT_REEVALUATED；后续同 TopicKey 正式 Episode 按练习目标深度更新 CONFIRMED/REGRESSED，正式能力不被练习覆盖。
- API/前端：候选人画像查询和页面直接投影双轨 SemanticState，不保留旧 response 双格式。
- 删除：ability counter/profile/snapshot/reconciliation、CandidateMemoryService 正式选题入口和旧单轨表访问。
- 测试：双轨隔离、能力公式、辅助分级、贡献幂等、pattern 门槛、scope 不扩张、正式无 Semantic、transfer 只由正式 Episode 更新。
- 依赖：T05。提交：`feat: aggregate and consume dual track memory`。

## T07 删除旧路径与端到端验收

**Goal**：代码库只剩 v4 记忆路径，并用 Redis persistence 场景证明完整闭环。

- 删除：无消费者的旧 topic/claim/profile/counter repository/service/table、失效 Prompt 字段、旧 response、死测试和空包。
- Schema：确认目标表是 sessions/plans/work states/patches/intents/episodes/exposures/semantic contributions/states；无 backfill、legacy、双写或 runtime 分支。
- 正式场景：本次计划选中 Redis persistence → draft 命中旧曝光 → 中性换场景 → 本轮独立评估 → 正式 contribution。
- 练习场景：scope 内 Semantic 选 fork/COW → 完整 Episode 定向追问/提示/复测 → assistance 入库 → 练习轨更新。
- 恢复场景：在 Intent 执行前、结果落库后、Patch 应用前分别中断，重启后没有重复问题或工具副作用。
- 验证：60 秒内运行后端全量测试；`rg` 证明旧类型、旧表和正式历史注入引用为零；工作树不含本任务未提交文件。
- 依赖：T06。提交：`refactor: remove obsolete memory implementation`。

## 2. 规格完成标准映射

| 34 号完成标准 | Tickets |
|---|---|
| WorkState + Patch + revision | T02 |
| Intent 先执行与恢复 | T03 |
| answered Episode + unanswered exposure | T04～T05 |
| 正式 Planner/Assessor 历史隔离 | T01～T02、T06 |
| draft 后中性召回与 TargetEnvelope | T05 |
| 练习完整诊断但本轮独立评级 | T05～T06 |
| Semantic 双轨不互相覆盖 | T06 |
| MemoryOwner/scope 与工具证据边界 | T02、T05～T06 |
| reducer/policy/聚合/召回与 PG 约束测试 | T02～T07 |
| 无迁移兼容和旧实现 | T07 |
