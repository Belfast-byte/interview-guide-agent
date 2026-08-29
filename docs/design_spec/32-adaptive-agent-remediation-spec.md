# 自适应面试 Agent 代码治理与体验改进 Spec(2026-08-22 分析落地)

> 维护：Agent；上游设计决策以 `docs/design/` 为准。
>
> 状态：已完成（2026-08-22，执行记录与偏差见 [33-remediation-execution-plan.md](./33-remediation-execution-plan.md) §4）
>
> 权威输入：2026-08-22 全包代码分析（core/runtime/role/planning/assessment/tool/mcp/memory/persistence/algorithm/codeanalysis/observability/api 十四个子包逐一排查）、[30-improvement-spec-2026-08-16.md](./30-improvement-spec-2026-08-16.md)、[20-implementation-modules.md](./20-implementation-modules.md)
>
> 最后更新：2026-08-22
>
> **历史记录声明（2026-08-29）**：本文只记录 2026-08-22 当时完成的治理与提交，不再是当前 Agent 架构约束。T-2/T-3 中固定重写次数、截断、确定性维度策略、FINISH 门槛、Role Registry、Tool 持久幂等和 ToolResultEvent 恢复均不得继续据此实现；当前边界以 [36-agent-loop-working-memory-spec.md](./36-agent-loop-working-memory-spec.md) 为准。权限、证据真实性、沙箱安全和数据库并发约束继续有效。

## 1. 文档目的

把 2026-08-22 分析的发现转换为可独立实现、可独立验收、依赖明确的修复票（T-x）。每票给出：现状证据、方案、改动点（文件级）、测试要求、验收标准。

与 30 号 spec 的关系：

- T-3 对齐并取代 IM-2（评估驱动编排）中「recommendSwitchQuestion 消费 + L4 修复」部分；
- T-7 是 IM-5（答题异步化）的创建侧落地，先行实施；
- T-6 对齐 IM-11（上下文压缩）的首题链路部分；
- 30 号 spec 其余条目（IM-3/4/8/9/10/12）不在本 spec 范围。

## 2. 当时的执行约束（历史）

1. 外部调用不进事务、不吞错误、最小改动仍有效；“所有写库必须经过一个 `AdaptiveInterviewPersistenceService`”和“模型只建议、Java 决定策略”已失效。
2. 当前只保留权限、最大轮次、Plan 成员关系、Tool 安全、证据真实性和数据库完整性等硬边界。本文当时采用的静默降级、截断和固定重试次数不得作为新实现依据。
3. 每票完成定义 = `./gradlew :app:compileJava` 通过 + 相关测试全绿 + 验收标准逐条可演示；前端相关票额外要求 `cd frontend && pnpm run build` 通过。
4. 删除死代码时连同其专属测试一起删；放宽约束时注意反射契约测试（`PlanningContractTest`、`DepthAssessmentAgentTest:17-31`）与 SQL 正则契约测试（`AssessmentFoundationContractTest`）的连带修改。

## 3. 票总览与依赖

| ID | 票 | 优先级 | 依赖 | 主要文件域 |
|---|---|---|---|---|
| T-1 | 死代码清理与冗余收敛 | P0 | 无 | application/algorithm/codeanalysis/persistence/assessment(report,practice)/api |
| T-2 | 模型输出失败语义软化 + depth/role 包清理 | P0 | 无 | runtime/tool/role/assessment(depth,evidence) |
| T-3 | 自适应能力通道（提前完成维度/模型提案 FINISH/题目改写） | P1 | T-2 | core/planning/role/prompts |
| T-4 | 判题与代码分析异步链路可靠性 | P1 | T-1 | algorithm/codeanalysis/persistence.session/application(handler) |
| T-5 | 配额与证据公平性 | P2 | T-4 | algorithm/codeanalysis.trace |
| T-6 | 首题链路提速（prompt 瘦身/调用收敛） | P1 | T-2、T-3 | prompts/memory/context/role(planner) |
| T-7 | 首题异步创建 + FAILED 状态 + 前端轮询 | P1 | T-1~T-3 | application/api/core.session/frontend |

实施波次：Wave1 = T-1 ∥ T-2；Wave2 = T-3 ∥ T-4；Wave3 = T-5 ∥ T-6；Wave4 = T-7。

---

## T-1 死代码清理与冗余收敛（P0）

### 背景与证据

- `replanWithCodeAnalysis` 全链无生产入口（仅测试调用）：`application/AdaptiveInterviewApplicationService.java:356-404`、`persistence/session/AdaptiveInterviewPersistenceService.java:279-315`（`replaceInitialPlan`）、`AdaptiveAgentSessionEntity.java:134`、`codeanalysis/CodeAnalysisInterviewContextService.java:42-74`（`findPlanningForSession` 唯一调用方是 replan）。
- `AssessmentBackfill` 全链无入口：`assessment/backfill/AssessmentBackfillService.java`、`AssessmentBackfillStore.java`、`AssessmentBackfillTurn.java`、`persistence/assessment/JpaAssessmentBackfillStore.java`、`api/AssessmentBackfillResponse.java`（record 定义后无引用）；连带 `algorithm/evidence/AlgorithmAssessmentEvidenceService.java:23-27`（单参 `attachAvailable`）与 `memory/profile/CandidateAbilityProfileWriter.refresh` 只有 backfill 一个调用方。
- 冗余：轮次上限 12 两处硬编码（`planning/InterviewPlan.java:22`、`core/session/AdaptiveInterviewSession.java:23`）；「每维度取最终评估」两遍实现（`assessment/practice/PracticeRecommendationService.java:21-23,80-112` 与 `assessment/report/AssessmentReportService.java:21-23,73-90`）；判题摘要字符串两处手拼（`application/AdaptiveAlgorithmResultReadyHandler.java:39-47` 与 `persistence/algorithm/JpaAlgorithmEvidenceSource.java:96-98`）；`Map<k,k>` 伪 Map 当 Set（`JpaAlgorithmEvidenceSource.java:37-50`）；JSON 反序列化助手重复（`codeanalysis/CodeAnalysisInterviewContextService.java:112-122` 与 `codeanalysis/job/CodeAnalysisPersistenceService.java:248-254`）。
- 造轮子：`algorithm/problem/AlgorithmSourceStorage.java:53-59` 手写 MessageDigest，而 `common/util/Sha256.java` 已存在；题库 resultId 手拼 id 列表（`tool/QuestionBankSearchTool.java:43-47`、`tool/RubricLookupTool.java:57-61`）结果集大时超 `result_id` 列 500 长度。
- 过度防御：`AlgorithmSubmissionService.submit`（`algorithm/judge/AlgorithmSubmissionService.java:24-32`）与 `createPending`（`AlgorithmPersistenceService.java:83-91,202-220`）重复校验（2 次行锁 + 2 次配额 count），`codeanalysis/scenario/CodePatchSubmissionService.java:57-60` 同样；`AlgorithmInterviewController.java:100-105` 归属校验后 `AlgorithmSubmissionService.get`（`:60-66`）又查全行内存比对；`InterviewSkillService.buildEvaluationReferenceSectionSafe`（`modules/interview/skill/InterviewSkillService.java:351-361`）catch Exception 静默降级空串。

### 方案

1. 删除 backfill 与 replan 两条死链（含实体方法、DTO、连带单调用方方法），如 DB 列随之废弃可保留列不删（不做破坏性迁移）。
2. 轮次上限提取为 core 共享常量；「最终评估选择器」抽为 assessment 包内一处共用；判题摘要格式收口到一处（建议放 `JpaAlgorithmEvidenceSource` 或独立 formatter，handler 复用）。
3. 伪 Map 改 Set；JSON 助手合并；`AlgorithmSourceStorage` 换用 `common/util/Sha256`；resultId 改 Sha256 摘要。
4. 删除提交链路的外层预校验（保留事务内那次）；`AlgorithmSubmissionService.get` 并入带归属条件的单条查询；`buildEvaluationReferenceSectionSafe` 删除 Safe 语义——失败抛出或记 ERROR + 指标后继续（二选一，按「技能基线缺失是否影响评估有效性」裁决，倾向显式失败）。

### 测试要求

- 删除 backfill/replan 专属测试；更新重复校验、归属查询相关测试。
- 为「最终评估选择器」共用实现保留等价单测（从两个 service 测试中收敛）。
- resultId 摘要：新增超长结果集不落库失败的用例。
- 全量 `./gradlew :app:test --no-daemon` 绿。

### 验收标准

- 全仓库 grep 无 `Backfill`、`replanWithCodeAnalysis`、`replaceInitialPlan`、`findPlanningForSession` 残留。
- 算法提交一次请求只发生一次行锁与一次配额 count（测试或日志可证）。

---

## T-2 模型输出失败语义软化 + depth/role 包清理（P0）

### 背景与证据

- 同类「模型可自愈错误」处理不对称：只有 `CodeQuestionProvenanceException` 注入 rejection observation 重试（`role/SpringAiAdaptiveAgentModelGateway.java:111-114`）；问题格式违规（`:268-288`，≤500 字符/恰好一个问号/单行）、题库逐字不匹配（`:308-321`）直接抛错整场失败。
- 证据 quote 逐字精确匹配（`assessment/evidence/AssessmentEvidenceValidator.java:27`），全半角/空白差异即整轮评估失败；ProbeGap 超 2 条硬拒（`assessment/depth/DepthAssessmentAgent.java:18-19,61-78`）。
- `ToolGateway` 结果 8000 字符超限即抛错（`tool/ToolGateway.java:125-127`），`load_skill` persona 最易触顶。
- ReAct 预算耗尽后模型坚持调工具 → 整场失败（`runtime/BoundedReActRuntime.java:71-88`）。
- 不可达校验：`DepthAssessmentAgent.java:51-62,75`（构造器 `List.copyOf` 已先抛 NPE）；catch-log-rethrow：`SpringAiAssessmentProposalGenerator.java:113-124`、`SpringAiPlanningAgent.java:104-117`（叠 initCause 重包装）、`SpringAiAdaptiveAgentModelGateway.java:98-105,124-133`、`memory/AbstractSpringAiMemoryGenerator.java:84-95`。
- 冗余：`DepthRubricEntry` 是 `DepthLevel` 的 1:1 拷贝（`assessment/depth/DepthRubricEntry.java:13-20`）；`AssessmentContext` 两工厂方法逐行重复（`:34,51`）；三个类各自手写「读 .st 资源 + PromptTemplate 拼接」（`SpringAiAssessmentProposalGenerator.java:60-70`、`SpringAiAdaptiveAgentModelGateway.java:81-88`、`SpringAiPlanningAgent.java:63-70`），且 `.st` 扩展名与 Spring AI `{placeholder}` 语法约定不符。
- `ToolGateway.java:161-177` 手写 JSON canonicalize + Sha256 幂等键守护的场景（单次运行去重 + 跨请求重试）已分别被 `BoundedReActRuntime.java:61-70` 和 `assertCanAnswer` 覆盖，且对 `1` vs `1.0` 不健壮。
- `DepthLevel.L0` 语义是「无证据」但 `DepthAssessmentAgent.java:52-53` 强制 evidenceQuotes 非空——自相矛盾。

### 方案

1. **失败语义统一**：模型输出校验失败（格式/provenance/quote/probeGap）一律走 rejection observation 让模型重写一次（复用 codeProvenance 既有模式），重写仍失败才抛 `BusinessException`。
2. **quote 匹配归一化**：去首尾空白 + 全半角统一 + 连续空白压缩后做子串匹配；单条证据不命中降级为丢弃该条（记 telemetry），不再整轮失败。
3. **ProbeGap 截断**：超 2 条截断而非拒绝；锚点匹配同 quote 归一化。
4. **ToolGateway 结果超限截断** + 追加「[truncated]」标注，不抛错；删除手写 canonicalize，幂等键直接用 `Sha256.hex(session+turn+tool+rawArgsJson)`，DB 唯一约束兜底。
5. **预算耗尽兜底**：注入「预算耗尽」observation 后强制一次无工具纯文本回复，仍调工具才失败。
6. 删除不可达校验与全部 catch-log-rethrow 装饰；合并 `DepthRubricEntry` 进 `DepthLevel`；`AssessmentContext` 工厂合并；抽 `PromptLoader`（放 common 或 role 包一处）统一模板加载，三个类复用。
7. 修复 L0 矛盾：L0 评级允许 evidenceQuotes 为空（校验逻辑按等级分支）。

### 测试要求

- 更新 `DepthAssessmentAgentTest`、`AssessmentEvidenceValidatorTest`、`ToolGatewayTest`、`BoundedReActRuntimeTest`：硬拒用例改为「重写一次后成功/降级」语义，新增「重写后仍失败才报错」用例。
- 新增：quote 全半角/空白差异归一化命中、单条证据降级丢弃、ProbeGap 3 条截断为 2、工具结果截断标注、预算耗尽无工具兜底、L0 空证据合法。
- 注意反射契约测试（`DepthAssessmentAgentTest:17-31`）与 SQL 正则契约测试的连带更新。

### 验收标准

- 模型输出任何单点格式问题不再直接导致整场面试失败（除重写后仍失败）。
- `ToolGateway` 无手写 canonicalize 代码；三个模型类不再各自读资源文件。

---

## T-3 自适应能力通道（P1，对齐取代 30 号 spec IM-2 对应部分）

### 背景与证据

- `DepthLevel.java:30` L4 写着「当前维度可提前完成」，但 `PlannedDimension.answer()`（`planning/PlannedDimension.java:26-29`）里 COMPLETED 只能由轮数用满触发——**代码无提前完成通道**，prompt 承诺是幻觉。
- 维度顺序固定、模型无从选择下一轮维度（`InterviewPlan.dimensionForTurn():70-79` 纯算术定位、`answer():90-93` 顺序推进）。
- 模型永远无权结束面试：`role/SpringAiAdaptiveAgentModelGateway.java:272-277` 非 ASK 即抛错；FINISH 唯一来源是轮次用尽的代码改写（`core/session/AdaptiveInterviewSession.java:52-54`，结束语也是硬编码文案）。
- 题库题必须逐字照搬（`SpringAiAdaptiveAgentModelGateway.java:308-321`），模型无法做上下文衔接改写。
- `recommendSwitchQuestion` 是只写不读的死字段（产出→落库，全仓库无消费）。

### 方案

1. **维度提前完成**：`InterviewPlan` 增加 `completeDimensionEarly` 能力——消费评估产出的 `recommendSwitchQuestion`（或 L4 结论），当前维度置 COMPLETED，剩余轮次回收进公共池（后续维度可分得）或释放给面试官深挖，由 `InterviewPlan.decide` 确定性裁决。
2. **模型可提案 FINISH**：网关放开 `AgentResponseType.FINISH` 产出，由 `AdaptiveInterviewSession` 裁决是否接受（建议门槛：已完成 ≥ 计划轮次 1/2 或模型附强制理由，具体阈值在实现时定并写明理由）；被接受的 FINISH 允许模型自带结束语，轮次用尽时的硬编码文案仅作兜底。
3. **题目改写放开**：题库 provenance 只校验 `sourceQuestionId` + `sourceDifficulty` 命中工具返回，content 允许改写；改写幅度不校验（prompt 里要求保持考点不变）。
4. 同步更新 interviewer/planner prompt 模板，让模型知道这三个能力存在（消除 L4 幻觉的同时让新通道可被发现）。

### 测试要求

- 更新 `InterviewPlanTest`（提前完成 + 轮次回收裁决用例）、`AdaptiveInterviewSessionTest`（FINISH 提案接受/拒绝）、网关测试（FINISH 不再抛错、改写 content 通过 provenance）。
- 新增端到端用例：候选人某维度连评 L4 → 维度提前关闭 → 轮次重新分配。
- `recommendSwitchQuestion` 字段从此有消费者，更新其契约测试。

### 验收标准

- L4 评估可触发维度提前完成并在后续轮次体现（集成测试可演示）。
- 模型可在满足门槛时主动结束面试，结束语由模型生成。

---

## T-4 判题与代码分析异步链路可靠性（P1）

### 背景与证据

- `reserveToolResultEvent`（`persistence/session/AdaptiveInterviewPersistenceService.java:90-101`）check-then-insert 竞态：并发投递同过 existsBy 再撞 `uk_agent_tool_result_event`，抛未包装的 `DataIntegrityViolationException`。
- 重复投递先去重前就烧 LLM：`AdaptiveAlgorithmResultReadyHandler.handle:49-56` 先 `reassessAlgorithmResult`（完整 LLM 重评 + 删插证据）再查预留。
- 状态竞态：调度器 `timeoutQueuedBefore/timeoutRunningBefore`（`AlgorithmPersistenceService.java:177-200`）无锁脏检查 vs 消费者 `findLockedById` 悲观锁，90s 边界互相覆盖；`SandboxExecutionEntity.apply:143-160` 不检查当前状态，迟到结果可把 TIMEOUT_QUEUED 翻成 DONE 但不产生追问（评估静默替换的不一致终态）。
- 三重重叠恢复：实体级 IE 自动重判（`SandboxExecutionEntity.java:144-149`）+ Stream 消息重投（`AbstractStreamConsumer.java:131-141` + `AlgorithmJudgeStreamConsumer.retryMessage:183-190`）+ 调度器超时降级对账（`AlgorithmQueueTimeoutScheduler` 三个任务），状态机语义交叠。
- 悬挂窗口：消费者内 IE 重判入队失败抛 IllegalStateException 后 `resetAfterWorkerFailure` 返回 false、消息被 ACK，执行悬挂到 90s 才降级（`AlgorithmJudgeStreamConsumer.java:126-128`）。
- 代码分析 Stream 仓库内无消费者，投递失败只标 failed 无重投（`CodeAnalysisSubmissionService.java:35-38`），靠 `CodeAnalysisTimeoutScheduler` 10 分钟超时兜底。

### 方案

1. 预留去重提前到 `AdaptiveAlgorithmResultReadyHandler.handle` 入口（任何 LLM 调用之前）；`reserveToolResultEvent` 删 existsBy 预检查，catch `DataIntegrityViolationException` 转「已存在」语义。
2. `SandboxExecutionEntity.apply` 加状态守卫：终态（DONE/TIMEOUT_QUEUED/FAILED）不接受的迟到结果直接忽略并记 warn + 指标。
3. 调度器超时扫描改条件更新（`UPDATE ... WHERE status = 'QUEUED' AND ...`）或与消费者同走悲观锁，消除覆盖窗口。
4. 恢复机制收敛为两层：Stream 消息级重投 + 调度器超时降级；删除实体级 IE 自动重判（或反之，二选一并写明理由——倾向删实体级，因其把状态打回 PENDING 与消息重投交叠）。
5. IE 重判入队失败路径补齐：消息不 ACK 走重试，而非依赖 90s 超时兜底。
6. 代码分析投递失败：补一次同步重投或直接置 FAILED 并明确文档化外部 worker 契约（二选一，最小实现）。

### 测试要求

- 新增并发用例：重复投递只重评一次、迟到结果被忽略、调度器与消费者同刻操作不产生覆盖。
- 更新 `AdaptiveInterviewConcurrencyIntegrationTest`、`AdaptiveAlgorithmResultReadyHandlerTest`。
- 删除/更新实体级 IE 重判对应测试。

### 验收标准

- 同一 resultId 重复投递不发生重复 LLM 重评（telemetry 计数可证）。
- 终态执行不被迟到结果改写。

---

## T-5 配额与证据公平性（P2）

### 背景与证据

- 沙箱配额按次数不按有效性：`AlgorithmPersistenceService.java:207` `countBySessionId` 包含已被 supersede 的历史提交与 IE/TIMEOUT 基础设施失败（配额值在 `AlgorithmInterviewProperties.java:16-17`，总 20/PATCH 2）；REST 直提与 agent 工具提交共享同一配额。
- 超时降级用 prompt 硬编码评审立场：`AdaptiveAlgorithmResultReadyHandler.java:38` 摘要写「do not treat this as negative evidence」——自然语言弥补工程问题，模型未必遵从。
- 代码追踪先扣后用：`CodeTracePersistenceService.java:18,24-34` reserve 在 trace 之前落库，追踪失败也耗配额，且对会话行悲观锁与答题主流程争锁。

### 方案

1. 配额计数只算有效执行：排除 superseded、IE、TIMEOUT_QUEUED（改 count 查询条件）。
2. TIMEOUT_QUEUED 摘要去立场化：客观陈述「判题不可用」；评估侧通过 EvidenceType 语义保证超时事件不作为负面证据（不进证据池或标记 neutral），不依赖 prompt 祈祷。
3. CodeTrace 改为先用后扣（成功后落库）或失败时释放额度，消除与答题主流程的锁争用。

### 测试要求

- 配额计数：superseded/IE/TIMEOUT 不计额的用例；边界恰好用尽的用例。
- 证据：超时事件不进入评估证据池的用例。
- CodeTrace：失败不耗配额的用例。

### 验收标准

- 平台故障不再消耗候选人配额（测试可演示）。

---

## T-6 首题链路提速（P1）

### 背景与证据

- 创建链路 3~5 次串行 LLM 调用，每次注入完整 JD+简历全文（`SpringAiAdaptiveAgentModelGateway.java:242` serializeContext、`SpringAiPlanningAgent.java:75` inputJson）。
- planner 的 ChatClient 带默认工具 advisor（`LlmProviderRegistry.java:167-181`，tool-call-enabled 默认 true），可能产生隐藏额外 LLM 往返。
- prompt 引导首轮先调工具再出题（`adaptive-agent-interviewer-system.st:9-10`、`adaptive-agent-planner-system.st:11` suggestedTools 带 question_bank_search），首轮几乎必然 2~3 步 ReAct。
- planner 结构化解析失败最多重试 1 次（双倍耗时）；`question_bank_search` 内嵌远程 embedding 调用。

### 方案

1. planner 改用无工具 advisor 的 plain client（与 interviewer 的 `getPlainChatClient` 对齐），消除隐藏往返。
2. prompt 瘦身：planner 与 interviewer 上下文中的 JD/简历注入摘要而非全文（摘要在请求侧一次性生成或直接截断，最小实现为截断到固定长度 + 标注）；对齐 30 号 spec IM-11 的创建侧部分，不做全量上下文压缩框架。
3. interviewer prompt 引导：首轮无充分理由直接出题，不强制先调 `load_skill`/`question_bank_search`；planner 的 suggestedTools 不再默认带 question_bank_search。
4. 目标：创建链路典型耗时压到原来的 1/3~1/2（2 次 LLM 往返以内）。

### 测试要求

- 更新 prompt 契约/组装测试（`ContextAssemblerTest`、网关序列化测试）。
- 新增：planner client 不携带工具 callback 的装配测试。

### 验收标准

- 创建链路 LLM 往返次数 ≤ 2（planner 1 + interviewer 1，工具调用为可选项）；日志/telemetry 可证。

---

## T-7 首题异步创建 + FAILED 状态 + 前端轮询（P1，30 号 spec IM-5 创建侧先行）

### 背景与证据

- 创建全串行同步，理论最坏 ≈120s（planner 60s + ReAct 60s），而前端 create 超时仅 45s（`frontend/src/api/adaptiveInterview.ts:14`）——慢到一定程度用户看到的是报错而非首题。
- 前端：`AdaptiveInterviewPage.tsx:149-171` 同步 await create 后 navigate；`AdaptiveSessionStatus` 已有 `'CREATED'` 未消费（`types/adaptiveInterview.ts:1`）；已有 2s 轮询先例（`:119-147`）。
- `AdaptiveInterviewResponse.from`（`api/AdaptiveInterviewResponse.java:21-25`）`turns().getLast()` 空 turns 会抛异常。
- 状态机 CREATED→IN_PROGRESS→COMPLETED，无 FAILED（`core/session/AdaptiveInterviewSession.java`）；注意 `agent_sessions` status 列的 DB CHECK 约束（查 db/migration）。

### 方案

1. POST create：校验 → 短事务落 CREATED 骨架 → 提交后台任务（复用/新建显式配置的 ThreadPoolExecutor，禁止 Executors.newXxx）→ 立即返回（status=CREATED，空 turns）。
2. 后台任务执行原重链路（规划 + ReAct 首题），完成后短事务落 plan+turn1 并推进 IN_PROGRESS；任何异常置 FAILED（带错误信息落库），绝不悬挂 CREATED。
3. core 状态机加 FAILED 终态（CREATED→FAILED 合法；FAILED 不可答题）；DB 有 CHECK 则加迁移。
4. `AdaptiveInterviewResponse.from` 支持空 turns（currentQuestion 为 null）。
5. 前端：create 响应即 navigate；CREATED/空 turns 时显示「正在规划并生成首题」并按 2s 模式轮询 loadSession；FAILED 展示错误（复用 ErrorBanner）；types 加 `'FAILED'`；改掉 SetupView「失败不留半成品」文案（`AdaptiveInterviewPage.tsx:603`）。

### 测试要求

- 后端新增：创建后立即 GET 返回 CREATED 空 turns；后台完成后 GET 返回 IN_PROGRESS 带首题；后台失败流转 FAILED 且错误信息可读。
- 更新受异步化影响的编排单测/集成测试（`AdaptiveInterviewApplicationServiceTest`、`AdaptiveInterviewFlowIntegrationTest`）——测试可直调后台完成方法保持同步语义。
- 前端 `pnpm run build` 通过。

### 验收标准

- create 请求 1s 内返回；首题就绪前前端有明确等待态；失败场景不悬挂、有错误展示。

---

## 4. 移交执行指南（给其他 Agent）

### 4.1 前置条件

- 基线：分支 `dsh/implements-probeGaps`，提交 `6faaec5`（认证/归属改造）之后全量测试绿。接手前先跑 `./gradlew :app:test --no-daemon` 确认基线。
- 本 spec 中的行号来自 2026-08-22 分析快照，基线提交已重排部分文件，**动手前先定位确认，不要相信行号**。
- 测试 JVM 堆已调为 2g（`app/build.gradle`），OOM 不是测试失败，不要再「修」它。

### 4.2 执行规则

1. 严格按 §3 波次执行：Wave1 = T-1 ∥ T-2 → Wave2 = T-3 ∥ T-4 → Wave3 = T-5 ∥ T-6 → Wave4 = T-7。同波次文件域不相交可并行；跨波次有文件依赖，禁止提前。
2. 每票一个 commit，消息格式 `<type>: 票号 简述`（如 `refactor: T-1 清理死代码与冗余`）；提交前相关测试必须绿。
3. 每票开工前重读：本票整节、§2 全局约束、`AGENTS.md`、`.claude/rules/backend.md`、`.claude/rules/interview-agent.md`。
4. 不越界改其他票的文件域；发现方案与代码现状冲突（如某项已被修掉）时按现状做最小合理实现，并在 commit message 或 PR 描述中说明偏差。
5. 删死代码连测试一起删；放宽约束注意反射契约测试与 SQL 正则契约测试的连带更新（§2-4）。

### 4.3 每票验证命令

```bash
./gradlew :app:compileJava
./gradlew :app:test --no-daemon --tests "interview.guide.modules.interview.agent.adaptive.**"
# 改了模块外文件时另跑对应测试；T-7 额外：
cd frontend && pnpm run build
```

### 4.4 接手 Agent 提示词模板

> 仓库 /home/noshiro/interview-guide-agent，分支 dsh/implements-probeGaps。实施 docs/design_spec/32-adaptive-agent-remediation-spec.md 的票 T-x：先通读该票整节与 §2、§4，再读 AGENTS.md 与 .claude/rules/ 下相关细则。行号可能漂移，先定位确认。最小改动，不越界其他票的文件域，完成后按 §4.3 验证并提交一个 commit。汇报改动文件清单、方案偏差、测试结果与验收标准自检。
