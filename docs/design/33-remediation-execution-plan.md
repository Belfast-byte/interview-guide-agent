# 32 号 Spec 修复执行计划（2026-08-22 可行性核验 + 逐票执行拆解）

> 状态：执行中
>
> 权威输入：[32-adaptive-agent-remediation-spec.md](./32-adaptive-agent-remediation-spec.md)（票定义唯一事实源）、2026-08-22 三路全包核验（对 32 号 spec 全部证据逐条比对当前代码）
>
> 本文档回答两个问题：**这些票现在还可行吗**（§1 差异表）、**每票具体怎么落地与验收**（§3）。票的背景与动机不再重复，以 32 号 spec 为准。

## 1. 可行性核验差异表（相对 32 号 spec 的证据快照）

结论：**七票全部可行，无方向性障碍**。以下为核验发现的偏差，执行时必须吸收。

| # | 发现 | 影响 |
|---|---|---|
| 1 | T-1 已在工作区完成约 90%（未提交，另一会话实施）：replan/backfill 死链、MAX_TURNS 常量、SandboxExecutionSummary、伪 Map→Set、CodeAnalysisJson、Sha256 复用、双重校验删除、scoped 归属查询、FinalAssessmentSelector 均已落地，死代码引用 grep 零残留 | T-1 只剩收尾：resultId 摘要 + project 孤儿链 |
| 2 | T-2 方案 5（预算耗尽兜底）已由提交 6faaec5 提前完成（BoundedReActRuntime 软化 + 3 个对应测试） | T-2 跳过方案 5 |
| 3 | T-3 三处 spec 未点名：① `AdaptiveInterviewPersistenceService:298-301` 硬守卫「维度未覆盖不能结束面试」先于会话裁决拒绝模型 FINISH；② FINISH 有三个代码产出点（session:52-54、appService:247-250、persistence 守卫）；③ interviewer prompt L10「不改写题意」、L24「结束由代码裁决」与新通道冲突 | T-3 必须替换持久层守卫并同步改 prompt |
| 4 | T-4：新增 `AlgorithmResultReadyDeliveryStore` + 调度器第三任务已覆盖 resultReady 丢失补偿；IE 重判悬挂路径（PENDING 态 + resetAfterWorkerFailure false + ACK）仍在 | T-4 方案 1-5 照做，补偿机制保留 |
| 5 | T-5：「超时不进证据池」一半已是现状——`JpaAlgorithmEvidenceSource` 候选查询已过滤 DONE/非IE/非superseded，TIMEOUT_QUEUED 本就不产生证据行 | T-5 真正要改的只有配额 count 条件、prompt 立场句、CodeTrace 先扣后用 |
| 6 | T-6：interviewer 网关已用 plain client 且关闭 advisor 自动注册；planner:86 用 `getChatClientOrDefault` 属实 | T-6 只需 planner 对齐 + 截断 + prompt |
| 7 | T-7：`agent_sessions_status_check`（V20260812:13-14）确认无 FAILED，需迁移（V20260809 旧表有 FAILED 先例）；仓库无共享线程池 bean，须新建；`persistenceService.create` 内联 `.start()`，需新增落 CREATED 骨架的方法；`reserveToolResultEvent` 已有 CREATED 守卫天然兼容异步创建 | T-7 按 spec 方案 + 新建 ThreadPoolTaskExecutor bean |
| 8 | 新孤儿链：replan 删除后 `PlanningRequest.project` → `ProjectPlanningContext` → `SpringAiPlanningAgent.PlanningModelInput.project` 恒 null，且被 `PlanningContractTest:18-20` 反射断言锁死 | 归入 T-1 收尾清理（连测试改） |
| 9 | `buildEvaluationReferenceSectionSafe` 有模块外调用方（`AnswerEvaluationService:57`、`VoiceInterviewEvaluationService:72`），不能删方法本体 | 偏差：adaptive 侧已改为直调非 Safe 版，方法保留给模块外调用方 |

## 2. 执行波次与提交约定

与 32 号 spec §3/§4 一致：Wave1 = T-1 ∥ T-2（本仓库为接管收尾，串行提交）→ Wave2 = T-3 ∥ T-4 → Wave3 = T-5 ∥ T-6 → Wave4 = T-7。每票一个 commit（`<type>: 票号 简述`），提交前 `./gradlew :app:compileJava` + adaptive 测试全绿；T-7 另跑 `cd frontend && pnpm run build`。行号在 32 号 spec 中来自旧快照，**动手前一律重新定位**。

## 3. 逐票执行拆解

### T-1 收尾：死代码清理与冗余收敛

- **目标**：完成 32 号 spec T-1 验收——死代码零残留、冗余收敛、提交链路单次校验。
- **实现范围**：`tool/QuestionBankSearchTool`、`tool/RubricLookupTool`、`planning/PlanningRequest`、`role/SpringAiPlanningAgent`（project 孤儿链）、`PlanningContractTest`，以及工作区既有未提交改动的收编。
- **如何实现**：① resultId 改 `前缀:Sha256.hex(排序后 id 逗号串)`——定长、同结果集同摘要（uk_agent_tool_result_event 幂等语义不变）、永不超 result_id VARCHAR(500)；② 删 project 孤儿链（PlanningRequest.project、ProjectPlanningContext、PlanningModelInput.project），连改 PlanningContractTest 反射断言；③ 核验并行会话已做项与全量测试。
- **验收标准**：grep 无 Backfill/replanWithCodeAnalysis/replaceInitialPlan/findPlanningForSession/project 孤儿；任何结果集 resultId 定长；一次提交恰好一次行锁 + 一次配额 count。
- **测试标准**：新增超长结果集 resultId 落库成功用例；更新 PlanningContractTest；全量 `:app:test` 绿。

### T-2：模型输出失败语义软化 + depth/role 包清理

- **目标**：模型可自愈错误（格式/provenance/quote/probeGap/工具超限）统一为「rejection observation 重写一次 → 仍失败才 BusinessException」；depth/role 冗余清零。
- **实现范围**：`role/SpringAiAdaptiveAgentModelGateway`、`assessment/evidence/AssessmentEvidenceValidator`、`assessment/depth/*`、`tool/ToolGateway`、PromptLoader 接入收尾。
- **如何实现**：① 泛化既有 provenance 重试模式（网关 nextAction 内 try/catch + withRejection observation + 重调一次）到格式校验与题库校验；② quote 归一化匹配（trim + 全半角统一 + 连续空白压缩后 contains），单条不命中降级丢弃 + telemetry，全部不命中才失败；③ ProbeGap >2 截断、锚点同归一化；④ ToolGateway 超限截断 + `[truncated]` 标注，删手写 canonicalize，幂等键 `Sha256.hex(session+turn+tool+rawJson)`；⑤ 删不可达 null 校验与 4 处 catch-log-rethrow 装饰；⑥ DepthRubricEntry 并入 DepthLevel、AssessmentContext 工厂合并（连改反射契约测试）；⑦ L0 允许空证据（校验按等级分支）。跳过方案 5（已由 6faaec5 落地）。
- **验收标准**：任一单点格式问题不再直接整场失败（除重写后仍失败）；ToolGateway 无 canonicalize；三个模型类不各自读资源文件。
- **测试标准**：改 DepthAssessmentAgentTest（硬拒→截断/降级 + 契约）、AssessmentEvidenceValidatorTest（归一化命中、单条降级）、ToolGatewayTest（截断标注、删乱序幂等用例）、网关测试（重写成功 / 重写仍失败才抛）；新增 ProbeGap 3→2 截断、L0 空证据用例。

### T-3：自适应能力通道（依赖 T-2）

- **目标**：打通维度提前完成、模型提案 FINISH、题目改写三个通道；recommendSwitchQuestion 有消费者。
- **实现范围**：`planning/InterviewPlan`/`PlannedDimension`、`core/session/AdaptiveInterviewSession`、网关、`AdaptiveInterviewPersistenceService`、application 编排、prompts 模板。
- **如何实现**：① `InterviewPlan.completeDimensionEarly(dimensionId)`：置 COMPLETED、回收轮次贪心补给后续维度（Σallocated ≤ maxTurns 不变量保持），appService 在评估落库后消费 recommendSwitchQuestion（true 或 L4 结论）触发；② 网关放开 FINISH 产出，门槛裁决（确定性）放 `AdaptiveInterviewSession.apply`：currentTurn ≥ ⌈maxTurns/2⌉ 接受并用模型结束语，不达门槛由 application 层复用 rejection observation 让模型改回 ASK，坚持 FINISH 才失败；替换 persistence:298-301 硬守卫；轮次用尽硬编码文案仅兜底；③ 题库 provenance 只校验 sourceQuestionId + sourceDifficulty 命中，content 放开改写；④ 同步改 interviewer prompt L10/L24、planner prompt、assessment prompt（说明建议会被代码消费）。
- **验收标准**：L4 → 维度提前 COMPLETED → 轮次重分配可演示；满足门槛的模型 FINISH 被接受且结束语来自模型；recommendSwitchQuestion 全链有读取方。
- **测试标准**：InterviewPlanTest 增提前完成 + 回收分配用例；AdaptiveInterviewSessionTest 增门槛接受/拒绝用例；网关测试 FINISH 不抛错、改写 content 通过 provenance；端到端连评 L4 用例；更新 persistence 守卫与 prompt 契约测试。

### T-4：判题与代码分析异步链路可靠性（依赖 T-1）

- **目标**：去竞态；恢复机制收敛为「Stream 重投 + 调度器降级」两层。
- **实现范围**：`AdaptiveAlgorithmResultReadyHandler`、`AdaptiveInterviewPersistenceService.reserveToolResultEvent`、`algorithm/judge/*`（AlgorithmPersistenceService、AlgorithmJudgeStreamConsumer、SandboxExecutionEntity、AlgorithmQueueTimeoutScheduler）、`codeanalysis/job/CodeAnalysisSubmissionService`。
- **如何实现**：① reserve 提前到 handle 入口（任何 LLM 调用之前）；删 existsBy 预检查，catch DataIntegrityViolationException → 「已存在」返回 false；② SandboxExecutionEntity.apply 加终态守卫（DONE/TIMEOUT_QUEUED/FAILED 忽略迟到结果 + warn + 指标）；③ 超时扫描改条件 UPDATE（WHERE status=QUEUED/RUNNING）消除与悲观锁消费者的覆盖窗口；④ 删实体级 IE 自动重判（理由：状态打回 PENDING 与 Stream 消息重投交叠，恢复收敛两层）；⑤ IE 重判入队失败不再 ACK 悬挂：重试仍败 → markInfrastructureFailure 立即降级；⑥ 代码分析投递失败补一次同步重投，仍败置 FAILED（最小实现）。
- **验收标准**：同 resultId 重复投递零 LLM 重评（mock 断言 reassess 未调）；终态执行不被迟到结果改写；无实体级 IE 重判代码。
- **测试标准**：并发用例（重复投递只评一次、迟到忽略、调度器与消费者同刻不覆盖）；更新 AdaptiveInterviewConcurrencyIntegrationTest、AdaptiveAlgorithmResultReadyHandlerTest；删实体级 IE 重判用例。

### T-5：配额与证据公平性（依赖 T-4）

- **目标**：平台故障不消耗候选人配额；超时事件立场客观化。
- **实现范围**：`SandboxExecutionRepository`、`AlgorithmPersistenceService`、`AdaptiveAlgorithmResultReadyHandler`、`codeanalysis/trace/*`。
- **如何实现**：① 配额 count 改口径 status=DONE 且 verdict<>IE 且 supersededBy is null（与证据候选查询同口径）；② TIMEOUT 摘要删「do not treat this as negative evidence」立场句，客观陈述判题不可用（证据池排除已是现状）；③ CodeTrace 改先用后扣：trace 成功后落库额度，消除先扣后用与答题主流程的锁争用浪费。
- **验收标准**：superseded/IE/TIMEOUT 不计额可演示；trace 失败不耗配额。
- **测试标准**：配额排除 + 恰好用尽边界用例；trace 失败不落额度用例；摘要文案断言更新。

### T-6：首题链路提速（依赖 T-2、T-3）

- **目标**：创建链路 LLM 往返 ≤ 2。
- **实现范围**：`SpringAiPlanningAgent`、`memory/ContextAssembler`、interviewer/planner 两个 prompt 模板。
- **如何实现**：① planner 改用既有 `getPlainChatClient`（registry 不动）；② ContextAssembler 的 planner()/interviewer() 对 jd/resume 固定长度截断 +「已截断」标注（配置 maxChars，最小实现）；③ interviewer prompt 首轮无充分理由直接出题、planner suggestedTools 不默认 question_bank_search。
- **验收标准**：创建链路 ≤ 2 次 LLM 往返（测试断言 chat 调用次数）；planner client 无工具 callback。
- **测试标准**：新增 planner 装配测试（同步改 SpringAiPlanningAgentTest 的 mock）；ContextAssemblerTest 截断用例；网关序列化测试更新。

### T-7：首题异步创建 + FAILED 状态 + 前端轮询（依赖 T-1~T-3）

- **目标**：create 1s 内返回；首题后台生成；失败置 FAILED 不悬挂；前端有明确等待态。
- **实现范围**：appService、`AdaptiveSessionStatus`/`AdaptiveInterviewSession`、`AdaptiveInterviewPersistenceService`、`AdaptiveInterviewResponse`、新 DB 迁移、新线程池配置、frontend（api/types/pages）。
- **如何实现**：① create：校验 → 短事务落 CREATED 骨架（新方法）→ 提交新建显式 `ThreadPoolTaskExecutor` bean（配置化 core/max/有界队列/优雅关闭，禁 Executors.newXxx）→ 立即返回；② 后台跑原 planner + ReAct 链，成功 → 短事务落 plan+turn1+IN_PROGRESS，异常 → FAILED + 可读错误；③ 迁移给 agent_sessions_status_check 加 'FAILED'（参照 V20260809 先例）；core 状态机 CREATED→FAILED 合法、FAILED 拒答；④ `AdaptiveInterviewResponse.from` 空 turns → currentQuestion=null；⑤ 前端 create 即 navigate，CREATED/空 turns 显示「正在规划并生成首题」+ 复用 2s 轮询 loadSession，FAILED → ErrorBanner，types 加 'FAILED'，删「不留半成品」文案。
- **验收标准**：create 1s 内返回；完成 → IN_PROGRESS 带首题；失败 → FAILED 可读错误不悬挂；前端 build 过。
- **测试标准**：后端三场景（创建即 CREATED 空 turns / 完成 / 失败，测试直调后台方法保持同步）；改写 AdaptiveInterviewApplicationServiceTest「失败不留会话」断言与 AdaptiveInterviewFlowIntegrationTest；`cd frontend && pnpm run build`。

## 4. 执行记录

- 2026-08-22：接管工作区（另一会话已完成 T-1 约 90% + T-2 主体），按本文档执行完毕。
- Wave1：`30792a6` T-1 死代码清理与冗余收敛（含 resultId 摘要、project 孤儿链连带清理）；`86048fd` T-2 模型输出失败语义软化与 depth/role 清理。
- Wave2：`13222fc` T-3 自适应能力通道；`68e83a8` T-4 判题与代码分析异步链路可靠性。
- Wave3：`661f573` T-5 配额与证据公平性；`e1815fa` T-6 首题链路提速。
- Wave4：`32b13b2` T-7 首题异步创建 + FAILED + 前端轮询。

实施偏差汇总（详见各 commit message）：

- T-1：`buildEvaluationReferenceSectionSafe` 因模块外调用方保留方法本体，adaptive 侧改直调非 Safe 版；SpringAiPlanningAgent 的 PromptLoader 接入属 T-2，因与 project 链清理同文件随 T-1 先行提交。
- T-2：方案 5（预算耗尽兜底）已由 6faaec5 提前落地，跳过。
- T-4：为使 H2 建表与 Flyway schema 一致，`AdaptiveAgentToolResultEventEntity` 补声明 uk_agent_tool_result_event 唯一约束。
- T-7：T-1 曾删除的 `PlanningRequest.project` 孤儿链在 T-3 前已清理，无额外冲突；测试经注入同步 TaskRunner 保持同步语义。
