# 删复杂度优先架构审计

  审计范围覆盖实际自适应面试主链、算法沙箱、代码分析、三层 Memory、Tool Runtime、持久化、恢复任务、Prompt、数据库迁移和
  前端消费契约。结论以生产代码为准；测试和迁移只用于验证约束与可达性，没有用设计文档替代码辩护。

  ## 0. 当前真实执行链

  图例：[LLM] 模型调用，[DB-W] 落库，[WS] WorkState 修改，[TOOL] Tool API，[EXT] 外部副作用，[VISIBLE] 用户可见，
  [RECOVERY] 仅恢复框架使用。

  创建请求
    │
    ├─ [DB-W][VISIBLE] 创建 Session(CREATED) 骨架
    │
    └─ 异步创建任务
         │
         ├─ [LLM] PlanningAgent 生成维度/顺序/focus/skill/建议轮次
         ├─ [确定性] InterviewPlan.decide 重算轮次、深度、预算
         ├─ [DB-W] Plan
         ├─ [DB-W][WS][RECOVERY] WorkState(READY_TO_DECIDE)
         ├─ [确定性] WorkStatePolicyPlanner 强制 ASK
         ├─ [DB-W][WS][RECOVERY] ASK ActionIntent + ACTION_PENDING
         ├─ [LLM] Interviewer 生成问题；没有 Tool observation loop
         ├─ [只读] 历史题查重；重复时可能再调用一次 LLM
         ├─ [DB-W][VISIBLE] Session(IN_PROGRESS) + Turn(question) + QuestionExposure
         └─ [DB-W][WS][RECOVERY] Intent SUCCEEDED/APPLIED + AWAITING_ANSWER

  用户回答
    │
    ├─ [只读] Session/Turn/Plan/WorkState/ownership
    ├─ [LLM] Assessor 输出 depth/evidence/probe gaps
    ├─ [只读] Java 直接加载 Skill 评估基线
    ├─ [确定性] quote 原文校验
    ├─ [确定性][WS] AssessmentWorkStatePlanner 更新 depth/gap/evidence/budget
    └─ [确定性] NextActionPolicy 决定：
         ├─ FINISH
         ├─ SWITCH_TARGET
         ├─ ASK
         └─ CALL_TOOL（只读 Tool 生产路径事实上不可达）

  普通回答
    │
    ├─ [DB-W][VISIBLE] Answer + Assessment + Evidence + ProbeGap + Episode
    ├─ [DB-W][WS][RECOVERY] 下一 ASK Intent
    ├─ [LLM] Interviewer 只生成已经决定好的问题文本
    └─ [DB-W][VISIBLE] 下一 Turn

  代码回答
    │
    ├─ [确定性] Java 强制构造 sandbox_submit
    ├─ [DB-W] Answer + Assessment + Episode + Tool Intent
    ├─ [TOOL][EXT] 保存源码、创建 SandboxExecution、发送 Redis Stream
    ├─ [DB-W] ToolExecution/Intent/WorkState
    ├─ [确定性] 继续生成下一题
    └─ 沙箱异步完成
         ├─ [DB-W] SandboxExecution 终态
         ├─ [DB-W][RECOVERY] ToolResultEvent RECEIVED/COMPLETED
         └─ [WS] 写 Tool 结果 Patch；当前不调用 LLM，也不产生真实追问

  旁路 Memory
    │
    ├─ [DB-W] Episode
    ├─ [LLM] Episode enrichment 生成 tags/summary
    ├─ [DB-W] tags + contribution
    └─ [确定性][DB-W] 重算 SemanticState

  最终报告
    └─ [只读][确定性][VISIBLE] Session + Plan + Assessment + Evidence 聚合

  主要代码证据：

  - 创建骨架和异步生成首题：app/src/main/java/interview/guide/modules/interview/agent/adaptive/application/
    AdaptiveInterviewApplicationService.java:164、app/src/main/java/interview/guide/modules/interview/agent/adaptive/
    application/AdaptiveInterviewApplicationService.java:217。

  - Plan 由 LLM 提案、Java 重分配预算：app/src/main/java/interview/guide/modules/interview/agent/adaptive/application/
    AdaptiveInterviewApplicationService.java:253、app/src/main/java/interview/guide/modules/interview/agent/adaptive/
    planning/InterviewPlan.java:42。

  - Runtime 实际只有一次模型调用，初始 observation 固定为空：app/src/main/java/interview/guide/modules/interview/agent/
    adaptive/runtime/BoundedActionRuntime.java:30。

  - ASK 最终落为 Session、Turn、QuestionExposure：app/src/main/java/interview/guide/modules/interview/agent/adaptive/
    persistence/intent/ActionIntentTransactionService.java:85。

  - 回答、评估和 Java 策略入口：app/src/main/java/interview/guide/modules/interview/agent/adaptive/application/
    AdaptiveInterviewApplicationService.java:355。

  - Java 完全决定下一动作：app/src/main/java/interview/guide/modules/interview/agent/adaptive/core/memory/
    NextActionPolicy.java:11。

  - 代码回答强制构造沙箱调用：app/src/main/java/interview/guide/modules/interview/agent/adaptive/application/
    AdaptiveInterviewApplicationService.java:588。

  - 异步沙箱结果当前只写 Patch、固定返回空：app/src/main/java/interview/guide/modules/interview/agent/adaptive/
    application/AdaptiveInterviewApplicationService.java:794。

  - 报告没有 LLM，是确定性读取聚合：app/src/main/java/interview/guide/modules/interview/agent/adaptive/assessment/
    report/AssessmentReportService.java:13。

  用户可见事实主要是 Session 状态、currentTurn、Turn 问答、维度状态、沙箱结果和最终报告。ActionIntent、WorkPhase、
  activeActionIntentId、Patch revision、ToolResultEvent 状态和 enrichment PROCESSING 都是内部执行/恢复状态；其中
  WorkState 的 Target 投影又被 API 直接暴露，导致内部状态意外成为前端契约：app/src/main/java/interview/guide/modules/
  interview/agent/adaptive/api/AdaptiveInterviewResponse.java:30。

  ———

  ## 1. 总体判断

  当前项目最接近 C：状态机驱动的 LLM 应用，不是完整 Agent Runtime，也不是轻量 workflow。Planner 和 Assessor 确实有语义提
  案空间，但下一动作、目标切换、追问深度、Gap 顺序、Tool 选择和结束条件几乎都由 Java 预先裁决。所谓 ReAct Runtime 没有
  Tool execution → observation → 再推理的循环，实际是一次模型调用。四个内部 Tool 中，三个 active Tool 都是普通业务方法的
  Tool 包装，唯一语义上适合 Agent 动态选择的题库检索却被禁用，因此当前成功的模型动态 Tool 调用数是 0。系统为了恢复可重算
  的 LLM/只读查询步骤，引入了 ActionIntent、WorkState、Patch、ToolExecution、ToolResultEvent 和多套 revision/status。与
  此同时，真正有外部副作用的沙箱仍使用随机 IntentId 作为幂等键，真正需要并发控制的 SemanticState 和 AnalysisJob 反而存在
  竞态。最大的三个问题是：重复事实源、伪 Agent 化、Java 策略过度控制。合理部分主要是 Session/Turn 领域事实、沙箱隔离、稳
  定数据库约束、权限边界和最终报告的确定性聚合。

  ———

  ## 2. Top 10 复杂度问题

   优先级    P0
   问题      ToolResultEvent 的两阶段“恢复”制造永久丢投递窗口
   代码证据  reserve 与 complete 是两个事务：app/src/main/java/interview/guide/modules/interview/agent/adaptive/
             persistence/session/AdaptiveInterviewPersistenceService.java:92、app/src/main/java/interview/guide/modules/
             interview/agent/adaptive/persistence/session/AdaptiveInterviewPersistenceService.java:114；补偿查询排除任何
             已有 Event，包括 RECEIVED：app/src/main/java/interview/guide/modules/interview/agent/adaptive/persistence/
             algorithm/JpaAlgorithmResultReadyDeliveryStore.java:30；测试明确锁定该行为：app/src/test/java/interview/
             guide/modules/interview/agent/adaptive/persistence/algorithm/
             JpaAlgorithmResultReadyDeliveryStoreIntegrationTest.java:58
   当前代价  进程在 reserve 后崩溃，事件永远停在 RECEIVED，扫描器也不会重投
   建议      以 SandboxExecution 为事实源；结果消费用一个原子 processed/unique evidence 写入，不再 reserve→complete
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   优先级    P0
   问题      沙箱幂等键不是业务幂等键
   代码证据  ASK/Tool 都用随机 Intent UUID：app/src/main/java/interview/guide/modules/interview/agent/adaptive/
             application/ActionIntentPlanFactory.java:24；retry 再换 UUID：app/src/main/java/interview/guide/modules/
             interview/agent/adaptive/persistence/intent/ActionIntentPersistenceService.java:75；沙箱直接把它当
             execution ID：app/src/main/java/interview/guide/modules/interview/agent/adaptive/algorithm/judge/
             AlgorithmSubmissionService.java:28
   当前代价  同一业务提交显式 retry 可产生两个外部任务；随机键只能防同一个 Intent 重放
   建议      使用 sessionId + turnIndex + targetId + sourceHash + runMode 稳定键；retry 复用同一 SandboxExecution
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   优先级    P0
   问题      Semantic projection 存在确定的并发丢更新
   代码证据  contributions 在锁 state 之前读取和聚合：app/src/main/java/interview/guide/modules/interview/agent/
             adaptive/persistence/memory/SemanticMemoryPersistenceService.java:59；state 后续才加锁且另有 @Version：app/
             src/main/java/interview/guide/modules/interview/agent/adaptive/persistence/memory/
             SemanticStateEntity.java:142
   当前代价  contribution 表有 C1+C2，materialized state 可能只包含其中一个；两个事实源漂移
   建议      首选删除 materialized state，按读聚合；若有性能证据，锁 scope 后再读，并声明只是 cache
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   优先级    P0
   问题      AnalysisJob 完成与超时没有真正并发 owner
   代码证据  Entity 无 @Version：app/src/main/java/interview/guide/modules/interview/agent/adaptive/codeanalysis/job/
             AnalysisJobEntity.java:15；完成写产物后置 COMPLETED：app/src/main/java/interview/guide/modules/interview/
             agent/adaptive/codeanalysis/job/CodeAnalysisPersistenceService.java:79；超时批量读后直接修改：app/src/main/
             java/interview/guide/modules/interview/agent/adaptive/codeanalysis/job/
             CodeAnalysisPersistenceService.java:211
   当前代价  可出现 TIMED_OUT Job 携带完整成功产物，或迟到结果翻转终态
   建议      行锁、@Version、条件更新三选一；不要叠加三套
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   优先级    P0/P1
   问题      Generic ActionIntent 既重复状态又造成 FAILED 卡死、回答分裂
   代码证据  FAILED 只能显式新建 Intent：app/src/main/java/interview/guide/modules/interview/agent/adaptive/application/
             ActionIntentExecutor.java:104；响应不返回 intentId：app/src/main/java/interview/guide/modules/interview/
             agent/adaptive/api/AdaptiveInterviewResponse.java:13；resume 使用新请求 answer 重建上下文：app/src/main/
             java/interview/guide/modules/interview/agent/adaptive/application/PersistentActionCoordinator.java:92
   当前代价  用户无法调用 retry；新请求 B 可驱动已持久化回答 A 的沙箱/Prompt；恢复协议反而破坏事实一致性
   建议      删除 ASK/read-only/sandbox generic Intent；从最近领域事实重算；沙箱只恢复 SandboxExecution
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   优先级    P1
   问题      WorkState 是第二套领域数据库，而且内部再次重复
   代码证据  一行同时存 revision、phase、activeIntent、完整 JSON、JPA version；恢复只读 JSON：app/src/main/java/
             interview/guide/modules/interview/agent/adaptive/persistence/working/AdaptiveWorkStateEntity.java:21；初始
             化 Patch 只含 SetFocus，根本不能 replay：app/src/main/java/interview/guide/modules/interview/agent/
             adaptive/persistence/working/WorkStatePersistenceService.java:35
   当前代价  Plan、Turn、Assessment、Gap、Intent 与 WorkState 必须持续同步；Patch 不是事件溯源，只是昂贵去重表
   建议      先删 Patch journal，再把 WorkState 改为按领域事实组装的 DecisionContext/Coverage
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   优先级    P1
   问题      ReAct/Tool Runtime 是名义 Agent，实际无循环
   代码证据  Runtime 只调用模型一次且 observations 为空：app/src/main/java/interview/guide/modules/interview/agent/
             adaptive/runtime/BoundedActionRuntime.java:30；多 ToolCall 静默只取第一个：app/src/main/java/interview/
             guide/modules/interview/agent/adaptive/role/AdaptiveAgentResponseMapper.java:70
   当前代价  增加 ReActRequest/Result/Observation/Role/Intent 等概念，却没有对应能力；错误 Tool 也不能作为 observation
             回给模型
   建议      当前先改回普通 InterviewerGenerator；只有真正启用动态检索时才实现简单内存 Tool loop
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   优先级    P1/P2
   问题      Java 已把 Agent 策略写死
   代码证据  深度达到 expected 即结束、answer issue 优先、tool issue 次之、取第一个 gap、顺序切 Target：app/src/main/
             java/interview/guide/modules/interview/agent/adaptive/core/memory/NextActionPolicy.java:21；Assessment gap
             全部硬编码为 CANDIDATE_ANSWER：app/src/main/java/interview/guide/modules/interview/agent/adaptive/
             application/AssessmentWorkStatePlanner.java:92
   当前代价  模型只剩“根据 Java 参数写一句问题”，无法根据语义决定最值得验证的 gap 或是否继续深挖
   建议      Java 保留合法性/预算裁决；让模型提出 gap、continue/switch、只读 Tool 策略
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   优先级    P1/P2
   问题      Memory 平台化并持久化可重算推理
   代码证据  WorkState 1,380 行；Episode enrichment 约 938 行、31 个相关类；enrichment Prompt 甚至没有插入上下文：app/
             src/main/resources/prompts/adaptive-agent-episode-enrichment-user.st:1、app/src/main/java/interview/guide/
             modules/interview/agent/adaptive/memory/AbstractSpringAiMemoryGenerator.java:56
   当前代价  为非权威 summary/tags 建四态、claim、stale recovery、手工 retry；当前 LLM 实际看不到权威上下文
   建议      修复真实错误；保留 Episode/tags 闭环，删除 PROCESSING checkpoint、未消费字段和 derived state
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   优先级    P2/P3
   问题      平台抽象、死配置和超大编排服务掩盖真实依赖
   代码证据  ApplicationService 933 行、18 个依赖：app/src/main/java/interview/guide/modules/interview/agent/adaptive/
             application/AdaptiveInterviewApplicationService.java:90；PersistenceService 566 行、跨十余仓库：app/src/
             main/java/interview/guide/modules/interview/agent/adaptive/persistence/session/
             AdaptiveInterviewPersistenceService.java:61；固定 Role Registry：app/src/main/java/interview/guide/modules/
             interview/agent/adaptive/role/AgentRoleRegistry.java:11
   当前代价  Controller→Application→Coordinator→Executor→TransactionService→Persistence 的调用链难追踪；大量参数袋和单实
             现转发
   建议      删除无行为参数袋/Registry/Factory；随 Intent/WorkState 删除自然收缩编排层，而不是再加 Facade

  ———

  ## 3. 重复 Source of Truth

   事实            当前等待哪一题回答
   当前存储于      Session.currentTurn；最新无 answer 的 Turn；WorkPhase.AWAITING_ANSWER；awaitingAnswerTurnIndex
   建议唯一事实源  最新 Turn + Session 状态
   可以删除/降级   WorkPhase、awaitingAnswerTurnIndex；currentTurn 可暂作投影
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   事实            当前有动作执行中
   当前存储于      ActionIntent status；active_session_id；DB unique；WorkState ACTION_PENDING；activeActionIntentId
   建议唯一事实源  generic Intent 删除后不再需要；外部任务以 SandboxExecution 为源
   可以删除/降级   WorkState phase/pointer、active-session 协议
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   事实            会话已结束
   当前存储于      Session COMPLETED；WorkState FINISHED；所有 Target 终态
   建议唯一事实源  Session
   可以删除/降级   WorkState FINISHED；Target 终态按报告/事实投影
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   事实            Target 定义
   当前存储于      Plan 的 dimension/focus/depth/budget/tools；WorkState 内嵌完整 CapabilityTarget
   建议唯一事实源  Plan
   可以删除/降级   WorkState 中的 Target 定义副本
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   事实            当前 Focus
   当前存储于      Plan.focus；WorkState.attentionFocus；初始化 SetFocus Patch
   建议唯一事实源  Plan
   可以删除/降级   attentionFocus、SetFocus
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   事实            Evidence 已存在
   当前存储于      assessment evidence 表；WorkState.activeEvidenceRefs；AddEvidenceRef Patch
   建议唯一事实源  Evidence 表
   可以删除/降级   WorkEvidenceRef、AddEvidenceRef。它们没有 Prompt、Report、Policy 消费者
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   事实            Probe gap 是否处理
   当前存储于      ProbeGap 表；WorkIssue；awaitingIssueId；Turn.sourceProbeGapId；synthetic issue ID
   建议唯一事实源  ProbeGap + Turn provenance
   可以删除/降级   WorkIssue 生命周期、awaitingIssueId、synthetic ID
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   事实            当前深度/Coverage/预算
   当前存储于      Plan 目标；Assessment depth；Turn 数；WorkState currentDepth/remainingBudget/status
   建议唯一事实源  Plan + Turn + Assessment 的内存投影
   可以删除/降级   持久化 Coverage snapshot、budget revision
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   事实            WorkState 本身
   当前存储于      标量 revision/phase/activeIntent；完整 JSON；JPA version；Patch base/result revision
   建议唯一事实源  不持久化；过渡期最多保留一种表示
   可以删除/降级   投影列、JSON 二选一；最终全部由事实组装
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   事实            “沙箱正在运行”
   当前存储于      Tool Intent EXECUTING；ToolExecution PENDING；WorkState ACTION_PENDING；SandboxExecution RUNNING
   建议唯一事实源  SandboxExecution
   可以删除/降级   Intent/ToolExecution/WorkState 对执行状态的复制
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   事实            Tool result 已消费
   当前存储于      SandboxExecution 终态；ToolResultEvent status；WorkState Patch source unique
   建议唯一事实源  SandboxExecution + 一个原子消费标记或唯一 Evidence
   可以删除/降级   ToolResultEvent 两阶段状态、第二次 Patch 去重
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   事实            候选人长期能力
   当前存储于      Assessment/Episode/Tags；SemanticContribution；SemanticState
   建议唯一事实源  不可变事实或 contribution
   可以删除/降级   SemanticState 仅可作为可重建 cache
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   事实            问题已向用户展示
   当前存储于      Turn.question；QuestionExposure
   建议唯一事实源  Turn
   可以删除/降级   Exposure 只可作为检索索引，不应成为事实源
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   事实            Episode 内容
   当前存储于      Turn/Assessment/Plan 中已有 owner、session、turn、topic、depth；Episode 再复制
   建议唯一事实源  Turn/Assessment/Plan
   可以删除/降级   workRevisionBefore/After、correctsEpisodeId、未消费 answerSummary、重复 unique

  最严重的内部重复是 app/src/main/java/interview/guide/modules/interview/agent/adaptive/persistence/working/
  AdaptiveWorkStateEntity.java:25：toDomain() 只解码 JSON，但表仍保存同值标量和另一套 @Version。这些标量又没有查询消费
  者。

  ———

  ## 4. 过度校验、并发和幂等审计

  分类：

  - A：有独立 failure scenario 的必要防线。
  - B：用户体验、成本或可观察性优化。
  - C：重复 correctness 保护。
  - D：没有当前价值的冗余。

  ### 过度校验矩阵

   Business invariant                  当前保护机制                真正必要 / correctness      重复机制
                                                                   owner
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━
   用户只能访问自己的 Session          AuthenticationPrincipal     两者都是 A：认证身份与数    无
                                       ；candidate/tenant owned    据库行级 ownership 解决
                                       query                       不同边界
  ──────────────────────────────────  ──────────────────────────  ──────────────────────────  ──────────────────────────
   同一 turn 只能有一个问题            Turn unique (session_id,    Turn unique（A）+           ASK Intent、WorkState
                                       turn_index)；Session        Session 单一推进版本        phase/index（C）
                                       @Version；ASK Intent；      （A）
                                       WorkState awaiting
  ──────────────────────────────────  ──────────────────────────  ──────────────────────────  ──────────────────────────
   同一回答只能推进一次                应用层 assertCanAnswer；    事务内条件 claim/Session    应用层检查是 B；
                                       事务内再次检查；            version + Assessment        WorkState revision/
                                       WorkState revision；        turn unique                 version/Patch 与重复
                                       WorkState @Version；                                    Episode unique 是 C
                                       Patch unique；Assessment
                                       unique；Episode 两个
                                       unique；Session @Version
  ──────────────────────────────────  ──────────────────────────  ──────────────────────────  ──────────────────────────
   同一 gap 只能生成一题               WorkIssue；                 Turn FK + unique（A）       WorkIssue lifecycle、
                                       awaitingIssueId；Turn                                   synthetic ID（C）
                                       provenance；FK；unique
                                       source_probe_gap_id
  ──────────────────────────────────  ──────────────────────────  ──────────────────────────  ──────────────────────────
   同一会话只能有一个 active Intent    exists 查询；               若保留 Intent，DB           exists 是 B；WorkState
                                       active_session unique；     unique（A）                 pointer/revision（C）
                                       Intent status；WorkState
                                       pointer/phase；
                                       basedOnRevision
  ──────────────────────────────────  ──────────────────────────  ──────────────────────────  ──────────────────────────
   Evidence 必须来自原回答             Prompt 要求；Assessor       一个最终 exact-match        Prompt 是模型 contract；
                                       semantic validation；最     validator（A）              但“重试后静默丢无效
                                       终 quote exact match                                    quote”会让规则失真，不是
                                                                                               有效 defense
  ──────────────────────────────────  ──────────────────────────  ──────────────────────────  ──────────────────────────
   Tool 参数合法                       Coordinator validate；      typed Application           其他两次是 C/D
                                       Gateway validate；          command 或 Gateway 系统
                                       Tool.execute 再解析/        边界一次（A）
                                       validate
  ──────────────────────────────────  ──────────────────────────  ──────────────────────────  ──────────────────────────
   沙箱结果不能被 timeout 覆盖         行级悲观锁；锁后            行锁 + terminal             DB enum check 只保护形
                                       terminal guard；状态        guard（A）                  状，不重复
                                       enum
  ──────────────────────────────────  ──────────────────────────  ──────────────────────────  ──────────────────────────
   沙箱提交不能重复副作用              随机 Intent key；           稳定业务键 +                随机 Intent key（D），存
                                       executionExists；           SandboxExecution 原子       在性预查仅是 B
                                       SandboxExecution PK；       claim（A）
                                       Stream consumer claim
  ──────────────────────────────────  ──────────────────────────  ──────────────────────────  ──────────────────────────
   Tool result 只处理一次              Event unique；reserve；     Sandbox result ID 对应唯    Patch source
                                       WorkState Patch unique；    一消费记录（A）             unique（C）；reserve/
                                       补偿扫描                                                complete 拆分当前有 bug
  ──────────────────────────────────  ──────────────────────────  ──────────────────────────  ──────────────────────────
   Enrichment 单 worker                PROCESSING；悲观锁；        只保护重复 LLM 成本，不     最多属于 B；三套机制是 C
                                       @Version；stale             保护不可逆副作用
                                       scheduler
  ──────────────────────────────────  ──────────────────────────  ──────────────────────────  ──────────────────────────
   Code analysis 终态不可翻转          Java isTerminal()           当前没有并发 owner          必须补一个原子机制
  ──────────────────────────────────  ──────────────────────────  ──────────────────────────  ──────────────────────────
   每场 CodeTrace 最多 3 次            countBySessionId 后执行     当前没有并发 owner          queryHash/createdAt 不提
                                       外部调用，再插记录                                      供配额 correctness

  ### 同一回答并发提交模拟

  T1：读取 Session IN_PROGRESS/currentTurn=N、WorkState revision=R
  T2：读取相同状态

  T1：调用 Assessment LLM
  T2：调用 Assessment LLM

  T1：进入 prepareAction/recordDecision，事务内再次 assertCanAnswer
  T2：也通过同一检查，因为 Turn 没有行锁或 @Version

  T1：写 Turn.answer、Assessment、Episode、WorkState R→R+1
  T2：写相同 Turn.answer、Assessment、Episode、WorkState R→R+1

  T1：提交成功
  T2：可能先撞 WorkState @Version、Patch unique、Assessment unique、
      Episode unique 或 Session @Version，取决于 flush 顺序

  当前数据库大概率只允许一个事务最终成功，但没有一个清晰的 correctness owner；第二个请求还已经烧掉一次 LLM。应用只在
  persistDecision 捕获乐观锁，而 prepareAction 在该 catch 之外：app/src/main/java/interview/guide/modules/interview/
  agent/adaptive/application/AdaptiveInterviewApplicationService.java:468、app/src/main/java/interview/guide/modules/
  interview/agent/adaptive/application/AdaptiveInterviewApplicationService.java:510。因此 unique 冲突还可能泄漏成非统一
  数据库异常。

  最小模型是：

  - 对 Turn answer 做 answer IS NULL 条件更新，或给 Turn 一个版本；
  - 保留 Assessment (session, turnIndex) unique；
  - 保留一个 Session progression version；
  - 把 loser 统一映射为“会话已推进”。

  为了省一次重复 LLM 增加 session single-flight 可以做，但那是性能优化，不是 correctness，不应进入核心状态模型。

  ### 幂等键审计

   Key/约束            稳定性                         保护对象                    判断
  ━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   Turn (sessionId,    稳定业务事实                   用户可见问题                KEEP
   turnIndex)
  ──────────────────  ─────────────────────────────  ──────────────────────────  ───────────────────────────────────────
   Assessment          稳定业务事实                   一次回答评估                KEEP
   (sessionId,
   turnIndex)
  ──────────────────  ─────────────────────────────  ──────────────────────────  ───────────────────────────────────────
   Turn                稳定来源                       gap 只追问一次              KEEP
   sourceProbeGapId
   unique
  ──────────────────  ─────────────────────────────  ──────────────────────────  ───────────────────────────────────────
   ActionIntent        随机执行 ID                    ASK、只读 Tool、沙箱        DELETE；不提供业务幂等
   UUID/
   idempotencyKey
  ──────────────────  ─────────────────────────────  ──────────────────────────  ───────────────────────────────────────
   ToolGateway 默认    session/turn/tool/arguments    Tool invocation             算法比随机 Intent 好，但 read-only
   hash                                                                           Tool 不需要；沙箱还应加入 sourceHash/
                                                                                  runMode
  ──────────────────  ─────────────────────────────  ──────────────────────────  ───────────────────────────────────────
   ToolResult          稳定外部结果                   异步消费                    KEEP 语义，删除两阶段状态机
   (toolName,result
   Id)
  ──────────────────  ─────────────────────────────  ──────────────────────────  ───────────────────────────────────────
   WorkState Patch     多数稳定                       workflow patch              DELETE；领域表已有 unique
   (sourceType,sour
   ceId)
  ──────────────────  ─────────────────────────────  ──────────────────────────  ───────────────────────────────────────
   Episode             两套稳定唯一键                 历史事实                    保留一个即可
   (session,turn)
   与 turnId
  ──────────────────  ─────────────────────────────  ──────────────────────────  ───────────────────────────────────────
   Semantic            稳定派生来源                   长期聚合                    如果保留 contribution，KEEP
   contribution
   (episode,track)
  ──────────────────  ─────────────────────────────  ──────────────────────────  ───────────────────────────────────────
   read-only Tool      没有副作用                     classpath/DB/vector read    不需要幂等基础设施
   invocation ID

  ### Retry / Recovery owner

  当前评估链有嵌套重试：StructuredOutputInvoker 自带最多两次解析重试，而 DepthAssessmentAgent 捕获语义错误后再次调用完整
  generator，最坏形成 2×2 次模型调用：app/src/main/java/interview/guide/common/ai/StructuredOutputInvoker.java:59、app/
  src/main/java/interview/guide/modules/interview/agent/adaptive/assessment/depth/DepthAssessmentAgent.java:31。

  唯一 owner 应是：

   失败类型                   唯一 owner
  ━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   JSON/schema 解析错误       StructuredOutputInvoker
  ─────────────────────────  ─────────────────────────────────────────────────────────
   语义 validation rewrite    Assessment Agent，但第二次不再获得完整解析 retry budget
  ─────────────────────────  ─────────────────────────────────────────────────────────
   LLM timeout                当前请求失败；从最近领域事实重跑，不持久化半步
  ─────────────────────────  ─────────────────────────────────────────────────────────
   只读 Tool 网络错误         若有真实 Agent loop，作为 observation；否则直接显式失败
  ─────────────────────────  ─────────────────────────────────────────────────────────
   Sandbox worker/网络失败    Redis Stream consumer 对同一 SandboxExecution 重试
  ─────────────────────────  ─────────────────────────────────────────────────────────
   DB optimistic conflict     不重跑 LLM；返回最新状态/冲突
  ─────────────────────────  ─────────────────────────────────────────────────────────
   用户请求重放               Turn/Assessment/Sandbox 的稳定业务键
  ─────────────────────────  ─────────────────────────────────────────────────────────
   结果已落库但通知未送达     SandboxExecution 扫描器；不能被 RECEIVED Event 永久挡住

  ———

  ## 5. Tool 审计

  当前只有四个 AdaptiveAgentTool 实现。

   Tool                 load_skill
   是否真正 Agent Tool  否。skill 已由 Plan 的 suggestedSkill 确定
   是否应自动调用       是，ContextAssembler/Application 自动加载
   是否需要持久化       否；classpath/配置读取可重算
   是否可并行           与其他只读查询可并行，但没有必要交给模型
   建议                 B：降级为普通 Skill lookup，删除 Tool 身份
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   Tool                 rubric_lookup
   是否真正 Agent Tool  当前否。Interviewer 不评分，Assessor 已直接加载 Skill reference
   是否应自动调用       当前可以删除；若确有出题知识需求，按稳定 TopicKey 自动加载
   是否需要持久化       否
   是否可并行           可与题库检索并行
   建议                 C：删除 Tool；不要用自由文本 dimension 重新查询评分规则
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   Tool                 question_bank_search
   是否真正 Agent Tool  理论上是 A：是否搜索、query、difficulty 依赖语义
   是否应自动调用       不应固定自动调用
   是否需要持久化       否；只持久化最终采用题目的 provenance
   是否可并行           是，独立查询可并行
   建议                 当前已下线且无闭环，删除 adaptive Tool 接线；只有成为真实当前需求时恢复
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   Tool                 sandbox_submit
   是否真正 Agent Tool  否。Java 已决定必须调用，参数必须逐项等于用户提交
   是否应自动调用       是，Application 直接提交
   是否需要持久化       是，但只持久化 SandboxExecution
   是否可并行           不与普通只读 Tool 混合并行；独立异步执行
   建议                 B：删除 Agent Tool 身份，保留 SandboxExecution、隔离、稳定幂等

  证据：

  - active whitelist 只有 load_skill/rubric_lookup/sandbox_submit，题库明确下线：app/src/main/java/interview/guide/
    modules/interview/agent/adaptive/role/AgentRoleRegistry.java:22。

  - load_skill 只是 InterviewSkillService.getSkill：app/src/main/java/interview/guide/modules/interview/agent/adaptive/
    tool/LoadSkillTool.java:48。

  - rubric_lookup 是普通 Repository 查询：app/src/main/java/interview/guide/modules/interview/agent/adaptive/tool/
    RubricLookupTool.java:55。

  - sandbox 参数必须与候选人提交完全一致：app/src/main/java/interview/guide/modules/interview/agent/adaptive/tool/
    SandboxSubmitTool.java:54。

  - CALL_TOOL 只有存在 TOOL_FACT WorkIssue 才可达，但唯一生产 Gap 的代码硬编码 CANDIDATE_ANSWER。因此测试中的 TOOL_FACT
    是人工构造状态，不是生产链。

  - Gateway 把序列化 JSON 直接截断并追加字符串，会制造无效 JSON：app/src/main/java/interview/guide/modules/interview/
    agent/adaptive/tool/ToolGateway.java:106。

  - 每个 Tool 无论有无副作用都落 agent_tool_calls：app/src/main/java/interview/guide/modules/interview/agent/adaptive/
    persistence/intent/ActionIntentTransactionService.java:108。

  结论：active Tool 3/3 都属于“为了 Agent 化而 Agent 化”；唯一合理的动态 Tool 1/1 当前不可用。

  ———

  ## 6. 状态机审计

   状态机              分类                      判断
  ━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   AdaptiveSessionS    领域状态机                KEEP。CREATED/IN_PROGRESS/COMPLETED/FAILED 用户可见，并控制是否能继续
   tatus                                         回答
  ──────────────────  ────────────────────────  ────────────────────────────────────────────────────────────────────────
   WorkPhase           重复 workflow 状态        DELETE/DERIVE。可由 Session、最新 Turn、外部执行事实推导
  ──────────────────  ────────────────────────  ────────────────────────────────────────────────────────────────────────
   TargetWorkStatus    Coverage 投影             DERIVE。不应独立承担 correctness
  ──────────────────  ────────────────────────  ────────────────────────────────────────────────────────────────────────
   WorkIssueStatus     ProbeGap 的第二状态机     SIMPLIFY/DELETE。INVESTIGATING 无生产者；OPEN/RESOLVED 可由 Gap→Turn
                                                 provenance 推导
  ──────────────────  ────────────────────────  ────────────────────────────────────────────────────────────────────────
   ActionIntentStat    可重算步骤执行状态        DELETE generic 状态机；仅副作用执行需要独立实体
   us
  ──────────────────  ────────────────────────  ────────────────────────────────────────────────────────────────────────
   SandboxExecution    外部副作用状态机          KEEP。PENDING/RUNNING/DONE/TIMEOUT 等有真实执行语义
   Status
  ──────────────────  ────────────────────────  ────────────────────────────────────────────────────────────────────────
   ToolExecutionOut    Tool wrapper 状态         read-only Tool DELETE；沙箱由 SandboxExecution 替代
   come
  ──────────────────  ────────────────────────  ────────────────────────────────────────────────────────────────────────
   ToolResultEventS    投递状态机                DELETE/SIMPLIFY 为单一原子消费事实；当前两阶段有 crash hole
   tatus
  ──────────────────  ────────────────────────  ────────────────────────────────────────────────────────────────────────
   EpisodeEnrichmen    可重算 LLM 推理状态       SIMPLIFY。不需要 PROCESSING checkpoint + stale recovery + @Version
   tStatus
  ──────────────────  ────────────────────────  ────────────────────────────────────────────────────────────────────────
   AnalysisJobStatu    外部异步任务状态          KEEP，但补一个原子终态 owner
   s
  ──────────────────  ────────────────────────  ────────────────────────────────────────────────────────────────────────
   PracticeStatus      伪状态机                  DELETE。后端只有 PENDING，生产者也只写 PENDING：app/src/main/java/
                                                 interview/guide/modules/interview/agent/adaptive/assessment/practice/
                                                 PracticeStatus.java:1
  ──────────────────  ────────────────────────  ────────────────────────────────────────────────────────────────────────
   TransferStatus      聚合结果分类              不是 lifecycle；从 semantic facts 计算
  ──────────────────  ────────────────────────  ────────────────────────────────────────────────────────────────────────
   TurnTriggerType.    未形成 Turn 的来源类型    DELETE。实际 Tool result handler 不创建 Turn
   TOOL_RESULT
  ──────────────────  ────────────────────────  ────────────────────────────────────────────────────────────────────────
   EpisodeAssistanc    未来状态                  DELETE。当前 producer 只生成 NONE/FOLLOW_UP/TOOL_ASSISTED
   eLevel.HINT

  WorkState Patch 不能从零重放出 targets、phase、budget 和 awaiting turn，因此不属于 event sourcing。它只是拥有 revision
  和 source unique 的操作日志：app/src/main/java/interview/guide/modules/interview/agent/adaptive/persistence/working/
  WorkStatePersistenceService.java:35。

  ### ActionIntent 专项结论

  - Generic ActionIntent：DELETE
  - ASK Intent：DELETE
  - read-only Tool Intent：DELETE
  - sandbox Tool Intent：DELETE，交给 SandboxExecution
  - ToolExecution：SIMPLIFY；仅在确有审计消费者时保留成功结果，不保留 PENDING workflow
  - SandboxExecution：KEEP
  - ActionIntent recovery scheduler：DELETE
  - activeActionIntentId/ACTION_PENDING/SetPendingAction/RetryPendingAction：DELETE
  - ToolResultEvent：替换为 SandboxExecution 上的原子消费事实或唯一 Evidence

  删除后失去的是“恢复到某一次模型生成/只读查询的精确中间步骤”，不是领域 correctness。系统仍可从最新 Turn/Answer/
  Assessment 重新生成；真正不可重复的沙箱由自己的实体恢复。

  ———

  ## 7. Memory 审计

   Memory           Working Memory
   Producer         Plan/Assessment 有 LLM 提案；所有 phase/budget/issue transition 由 Java 生成
   Storage          WorkState JSON + 标量列 + Patch journal
   Consumer         NextActionPolicy、Prompt view、Coordinator、维度 API、Episode revision
   Decision impact  很高：直接决定 ASK/CALL_TOOL/SWITCH/FINISH
   判断             它不是 cognitive memory，而是持久化 workflow state；应改名/降级为按需 DecisionContext/Coverage
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   Memory           Episodic Memory
   Producer         每次回答后 Java 确定性创建 Episode；enrichment LLM 生成 tags/summary
   Storage          Episode、Tag、QuestionExposure
   Consumer         历史题去重、practice coaching、semantic 聚合、Memory API
   Decision impact  真实闭环：会影响后续题目和练习 Prompt
   判断             KEEP 核心事实，SIMPLIFY enrichment
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   Memory           Semantic Memory
   Producer         ContributionFactory 和 Aggregator 基本确定性；stable pattern 来自 tags
   Storage          Contribution + materialized SemanticState
   Consumer         Practice planning、coaching、Memory API
   Decision impact  只影响 PRACTICE 模式
   判断             闭环真实，但双重派生持久化过度；state 当前还有丢更新

  ### Working Memory

  生产者实际上是 Java：

  - InterviewPlan.initialWorkState 初始化预算、focus、active target：app/src/main/java/interview/guide/modules/
    interview/agent/adaptive/planning/InterviewPlan.java:124。

  - AssessmentWorkStatePlanner 把 LLM assessment 转成 Patch：app/src/main/java/interview/guide/modules/interview/agent/
    adaptive/application/AssessmentWorkStatePlanner.java:28。

  - NextActionPolicy 消费它并决定控制流：app/src/main/java/interview/guide/modules/interview/agent/adaptive/core/memory/
    NextActionPolicy.java:11。

  它不是“Agent 短期认知”，而是 InterviewControlContext。其中 focus、evidence、gap、budget、active intent 都已经存在于其
  他事实中。

  ### Episodic Memory

  它确实有生产—存储—检索—消费闭环，不能一刀删除。但 Episode 复制了大量可连接字段；enrichment 的 answerSummary 没有实际消
  费者，correctsEpisodeId 没有 producer，workRevisionBefore/After 只服务状态框架。更严重的是当前模板写了字面量
  <contextJson>，因此 enrichment LLM 没拿到权威上下文：app/src/main/resources/prompts/adaptive-agent-episode-enrichment-
  user.st:1。

  ### Semantic Memory

  真正事实源是 contributions 或更底层 Episode/Assessment/Tags。candidate_semantic_states 是确定性投影，却被当作事实表维
  护，并引入 revision、@Version 和并发漂移。默认应按读聚合；只有测出性能问题才保留可重建 cache。

  ———

  ## 8. Agent autonomy 审计

  ### 应由代码决定

  - Session/Turn 唯一性和状态合法性。
  - 已结束 Session 禁止继续回答。
  - 最大轮次硬上限。
  - Tool allowlist、权限和 tenant/candidate ownership。
  - evidence quote、代码 anchor、题库 provenance 必须命中真实产物。
  - 沙箱源码绑定、隔离、稳定幂等、终态转换。
  - 最终提交动作必须能原子落为 Turn/Assessment。
  - LLM 只能提案，代码拥有最终合法性裁决。

  ### 当前被代码决定，但更适合让 Agent 提案

  - 两个 probe gap 中哪个更值得验证。
  - 当前回答达到 expectedDepth 后，是否仍值得用剩余预算继续深挖。
  - 追问、切换 Target，还是查一个只读信息源。
  - 题库 query、difficulty、检索条件。
  - 多个独立只读 Tool 的选择、顺序和并行。
  - Target 切换优先级。
  - answer evidence 与 tool evidence 谁更值得先补。

  ### 当前自主空间

   决策                                              当前 owner                       自主性
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━
   初始维度、顺序、focus、skill、建议轮次            Planner LLM 提案，Java重算       中等
  ────────────────────────────────────────────────  ───────────────────────────────  ────────────────
   depth/confidence/rationale/evidence/probe gaps    Assessor LLM 提案，Java校验      中等
  ────────────────────────────────────────────────  ───────────────────────────────  ────────────────
   问题自然语言、项目 claim/scenario                 Interviewer LLM                  中等内容自主性
  ────────────────────────────────────────────────  ───────────────────────────────  ────────────────
   ASK/CALL_TOOL/FINISH                              Java                             0
  ────────────────────────────────────────────────  ───────────────────────────────  ────────────────
   追问还是主问题                                    Java                             接近 0
  ────────────────────────────────────────────────  ───────────────────────────────  ────────────────
   选择哪个 Gap                                      Java 取第一个                    很低
  ────────────────────────────────────────────────  ───────────────────────────────  ────────────────
   达到 expectedDepth 后是否继续                     Java立即结束 Target              0
  ────────────────────────────────────────────────  ───────────────────────────────  ────────────────
   Target 切换顺序                                   Java取第一个 PENDING             0
  ────────────────────────────────────────────────  ───────────────────────────────  ────────────────
   Tool 是否调用、参数、顺序、并行                   当前成功路径均由 Java或不可达    0

  准确描述是：

  > 控制面自主性接近零，语义内容自主性中等。当前主链是“Planner/Java 算完动作 → Interviewer 生成自然语言”，Agent autonomy
  > 已经退化为模板生成器。

  Prompt 还与这种控制方式互相冲突：Prompt 要求模型按需调用 Tool，但 ASK Intent 收到 ToolCall 会直接失败；代码强制
  sandbox，Prompt又重复要求必须 sandbox；代码说维度切换由 Java决定，Prompt仍描述一套 Tool/WorkState 策略：app/src/main/
  resources/prompts/adaptive-agent-interviewer-system.st:1。

  ———

  ## 9. 可以直接删除的东西

  ### 删除 Generic ActionIntent 协议

  当前职责：为 ASK、只读 Tool、沙箱 Tool 持久化 PLANNED→EXECUTING→SUCCEEDED→APPLIED，并提供 scheduler/retry。

  为什么冗余：ASK 尚未写为 Turn 前只是可重算草稿；read-only Tool 可重跑；沙箱已有独立 SandboxExecution。它还复制了
  WorkState phase/pointer 和 ToolExecution status。

  删除后：从最近的 Session/Turn/Assessment 重新组装上下文并生成下一题。

  替代机制：Turn 是 ASK 事实；SandboxExecution 是副作用事实；Session/Assessment unique 控制推进。

  风险：失去精确恢复某次中间 LLM 输出、Intent 审计和避免一次重复计算。无独立领域 correctness 风险。

  ### 删除 WorkState Patch journal

  当前职责：保存 typed operation、source、base/result revision，并做 source 去重。

  为什么冗余：初始化 Patch 无法重建初始状态，Repository 没有 replay API；相同来源已由 Turn、Assessment、Gap、Sandbox 业
  务 unique 约束。

  删除后：先保留内存 reducer，输入改为 Plan/Turn/Assessment/Gap facts；再删除持久 WorkState。

  替代机制：Coverage/DecisionContext 按事实组装。

  风险：需要先替换当前维度 API 对 WorkState targets 的读取。除此之外无独立 correctness 风险。

  ### 删除 load_skill Tool

  当前职责：让模型调用一个已经由 Plan 确定的 skillId。

  为什么冗余：Application 在评估路径已经直接调用 skillService.buildEvaluationReferenceSection：app/src/main/java/
  interview/guide/modules/interview/agent/adaptive/application/AdaptiveInterviewApplicationService.java:406。

  删除后：ContextAssembler 自动注入冻结 persona。

  替代机制：现有 InterviewSkillService。

  风险：无独立 correctness 风险。

  ### 删除 rubric_lookup Tool

  当前职责：按模型提供的自由文本 dimension 查询评分量规。

  为什么冗余：Interviewer 不负责评分；Assessor 已有固定 skill reference，且 CALL_TOOL 生产路径不可达。

  删除后：当前主链能力不丢失。

  替代机制：需要知识基线时按 Plan 的稳定 TopicKey 直接装配。

  风险：只可能影响未经证明的出题质量，不影响 correctness。

  ### 删除 sandbox_submit 的 Agent Tool 身份

  当前职责：包装 Java 已经完全确定的 sandbox command。

  为什么冗余：是否调用、target、runMode、源码都已由用户提交和 Java 决定。

  删除后：Application 直接调用提交 Service。

  替代机制：保留 SandboxExecution、Stream consumer、行锁、超时和结果扫描。

  风险：无 Agent 能力损失；模型本来没有选择权。

  ### 删除 ToolResultFollowUp 伪模型

  当前职责：把 COMPLETED ToolResultEvent 映射成“追问”。

  为什么冗余：responseContent 和 decisionReason 从未赋值，handler 固定返回 empty，而前端仍渲染空内容：app/src/main/java/
  interview/guide/modules/interview/agent/adaptive/persistence/session/AdaptiveAgentToolResultEventEntity.java:52、
  frontend/src/pages/AdaptiveInterviewPage.tsx:519。

  删除后：如果沙箱结果需要生成问题，就创建普通 Turn；如果只用于证据，就记录 Evidence。

  替代机制：Turn 或 Evidence，二选一。

  风险：必须保留 resultId 的原子消费去重；不能直接删除后裸处理。

  ### 删除不可达的 Tool planning 状态

  当前职责：suggestedTools、TOOL_FACT objective、toolBudget、CALL_TOOL policy 分支和 UI 展示。

  为什么冗余：生产代码不会创建 TOOL_FACT WorkIssue，只有测试手工构造。

  删除后：Plan 只保留真实使用的维度、focus、skill、turn/depth budget。

  替代机制：未来题库检索成为当前需求时，从真实 AgentLoop 引入，不提前保留假状态。

  风险：丢失的是未上线设想，不是当前能力。

  ### 删除 ReAct 残留

  当前职责：ReActRequest/Result/ModelContext/ToolObservation 为单次调用包装 Agent 术语。

  为什么冗余：不存在 observation loop；accepted ToolObservation 没有成功生产路径。

  删除后：当前实现可以诚实地叫 InterviewerRequest/InterviewerGenerator。

  替代机制：普通一次 LLM 调用；真实动态 Tool 出现后再写最小内存 loop。

  风险：无运行能力损失。

  ### 删除派生 Memory 执行状态

  当前职责：Episode enrichment PROCESSING、claim、stale recovery、手工 retry；SemanticState revision/materialization。

  为什么冗余：LLM enrichment 可重算，没有不可逆副作用；SemanticState 可由 contribution 聚合。

  删除后：保留 Episode、validated tags 和最后错误；单一 scheduler 扫未完成项即可。Semantic 按读聚合。

  替代机制：最近可靠事实重算。

  风险：可能重复一次 enrichment LLM，属于成本而非 correctness。

  ### 删除死状态、字段和配置

  高置信项：

  - WorkIssueStatus.INVESTIGATING
  - PracticeStatus
  - EpisodeAssistanceLevel.HINT
  - TurnTriggerType.TOOL_RESULT
  - Episode.correctsEpisodeId
  - Episode workRevisionBefore/workRevisionAfter
  - 未消费 answerSummary
  - agent_turns.answered_at，实体从不映射
  - sandbox_executions.pending_rejudge/retry_count，真实 retry 在 Redis 消息
  - agent_tool_calls.role，生产请求恒为 INTERVIEWER
  - runtime_version，当前恒为 adaptive-agent-v1 且无版本分支
  - claimDeadline 和不存在资源的 claim prompt 配置
  - AdaptiveMemoryFacts 单字段 wrapper

  这些没有当前独立 correctness 风险。数据库字段应通过新 migration 删除，不改历史 migration。

  ### 删除纯参数袋和同层单实现转发

  优先删除/合并：

  - EpisodeEnrichmentServiceDependencies
  - EpisodeEnrichmentGeneratorDependencies
  - EpisodeEnrichmentRepositories
  - SemanticMemoryRepositories
  - AgentRoleRegistry + AgentRoleDefinition
  - QuestionIdentityFactory
  - SemanticContributionFactory
  - AdaptiveInterviewCreationTaskRunner
  - PracticeMemoryOwnerSource + JpaPracticeMemoryOwnerSource
  - AlgorithmEvidenceSource + JpaAlgorithmEvidenceSource

  外部边界接口不应因单实现而删：AgentModelGateway、PlanningAgent、SandboxWorker、S3/VectorStore/HTTP ports 仍有隔离外部
  依赖的真实价值。

  ———

  ## 10. 简化后的目标架构

  不引入新框架，只保留当前已有业务概念。

  创建面试
    Session(CREATED)
        │
        ├─ Planner LLM
        ├─ Java 校验硬约束
        └─ 短事务：Plan + Session
                │
                ▼
           ContextAssembler
                │
                ▼
         简单 AgentLoop
         ├─ LLM 提出 ASK 或只读 Tool 调用
         ├─ 只读 Tool 在内存执行，不落执行状态
         ├─ 无依赖查询可并行
         └─ 最终 ASK
                │
                ▼
         短事务：Turn + Session(IN_PROGRESS)

  回答
    短事务：条件 claim Turn.answer
                │
                ▼
         事务外 Assessor LLM
                │
                ▼
   Evidence / ProbeGap / Coverage 投影
                │
                ▼
         简单 AgentLoop
         ├─ 模型提出 gap/continue/switch/ASK
         └─ Java 校验目标、预算、来源、硬上限
                │
                ▼
   短事务：Assessment + Evidence + 下一 Turn / Session完成

  代码提交
    稳定业务键
        │
        ▼
   SandboxExecution(PENDING)
        │
        ├─ Redis worker
        ├─ 沙箱隔离执行
        ├─ 行锁/条件更新终态
        └─ 原子写入 Evidence 或 consumedAt
                │
                ▼
         下一轮从事实重新组装 Context

  最终报告
    Session + Plan + Turn + Assessment + Evidence
        │
        └─ 确定性 Report

  最小职责：

  - Session：生命周期、最大轮次、一个并发版本。
  - Turn：用户已经看到的问题、回答和 provenance。
  - Planner：LLM 提案；Java只校验 catalog、范围和硬上限。
  - ContextAssembler：从领域事实组装一次性上下文；自动加载固定 Skill。
  - AgentLoop：仅在有真实动态只读 Tool 时执行 0..N 次；否则就是一次 interviewer 调用。
  - ToolExecutor：只读 Tool 不持久化；副作用 Tool 不走 generic executor。
  - SandboxExecution：沙箱唯一执行事实源。
  - Evidence/Coverage：Evidence 持久化；Coverage 默认按 Plan/Turn/Assessment 推导。
  - Report：保持当前确定性聚合方式。

  当前相比这个最小模型多出的机制及判断：

   额外机制                            最小模型解决不了的问题         判断
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ASK ActionIntent                    避免崩溃后重复生成一次问题     当前不值得；Turn 提交前可重算
  ──────────────────────────────────  ─────────────────────────────  ──────────────────────────────────────────────
   read-only ToolExecution             精确审计某次查询               无消费者，不是 correctness
  ──────────────────────────────────  ─────────────────────────────  ──────────────────────────────────────────────
   persistent WorkState + Patch        恢复 Java workflow 中间步骤    可由领域事实推导；删除
  ──────────────────────────────────  ─────────────────────────────  ──────────────────────────────────────────────
   ToolResultEvent 两阶段              结果投递幂等                   问题真实，但实现比单一原子标记更复杂且有 bug
  ──────────────────────────────────  ─────────────────────────────  ──────────────────────────────────────────────
   SandboxExecution                    外部副作用不可随意重放         必要，保留
  ──────────────────────────────────  ─────────────────────────────  ──────────────────────────────────────────────
   Episode enrichment recovery         避免重复 LLM、保证最终 tags    可简化为未完成扫描
  ──────────────────────────────────  ─────────────────────────────  ──────────────────────────────────────────────
   SemanticState                       降低读取聚合成本               没有性能证据，且当前有竞态
  ──────────────────────────────────  ─────────────────────────────  ──────────────────────────────────────────────
   Role Registry                       多角色 Tool runtime            实际只有 INTERVIEWER；PLANNER 不经过它
  ──────────────────────────────────  ─────────────────────────────  ──────────────────────────────────────────────
   Question-bank index/MCP fallback    未来动态题库 Tool              当前 Tool 下线，属于平台化早于需求
  ──────────────────────────────────  ─────────────────────────────  ──────────────────────────────────────────────
   多套 revision/version               并发推进                       一个领域版本 + DB unique 足够

  ———

  ## 11. 改造优先级

  ### P0：先修真实错误，不做大重构

  改：

  - 修复 enrichment Prompt 的 {contextJson}。
  - ToolResult 消费改成一个原子事务，消除 RECEIVED 永久卡死。
  - 沙箱改用稳定业务幂等键；PENDING execution 可由现有扫描器用同一 ID 重投。
  - SemanticState 删除或改为锁后读取。
  - AnalysisJob 使用一种原子终态机制。
  - 修复 ActionIntent 保留期间的 answer A/B 不一致和不可发现 FAILED Intent。
  - CodeTrace 配额改为原子 reservation/slot，而不是 count 后外部调用。

  必须新增测试：

  - reserve 后进程崩溃仍能再次投递。
  - 同一沙箱业务重试/并发只创建一个 execution。
  - 两个 session 并发贡献后 semantic 聚合包含二者。
  - Analysis complete 与 timeout 并发，状态与产物一致。
  - enrichment 真实 Prompt 渲染包含权威上下文。
  - 同一回答不同 payload 重放不会混用。

  ### P1：删除后显著降复杂度

  改：

  - 删除 ASK/read-only/sandbox generic ActionIntent。
  - 删除 WorkState Patch journal。
  - 删除 WorkState 内部 JSON/标量重复。
  - 用事实 projector 替代 WorkPhase、activeIntent、awaitingIssue。
  - 删除 ToolResultFollowUp 伪 API/UI。
  - sandbox Application 直接调用提交 Service。
  - 删除 read-only ToolExecution 持久化。

  必须保留/新增测试：

  - 从“回答和 Assessment 已落库、下一题未创建”状态可重新生成。
  - 下一 Turn 已存在时重放不重复创建。
  - 同一回答并发只产生一个 Assessment、Episode、后继 Turn。
  - Sandbox timeout/result 终态守卫。
  - 删除 WorkState 后维度 API 输出与领域事实一致。

  ### P2：调整 Agent autonomy

  改：

  - Java继续提供合法 Target、剩余预算、硬上限。
  - 模型提案选择 gap、继续深挖、切换或动态只读检索。
  - 只有题库检索成为当前产品需求时才启用最小内存 Tool loop。
  - Tool failure 作为 observation；最终只持久化采用的 ASK/provenance。

  测试：

  - tool call → observation → final ASK 的真实端到端测试。
  - 多个 gap 时模型提案受 Java allowlist/budget 裁决。
  - 无依赖只读 Tool 不因执行顺序改变结果。
  - 模型不能突破 turn cap、非法 target 或伪造 provenance。

  ### P3：代码味道清理

  改：

  - 删除参数袋、固定 Registry、单消费者 Factory 和同层 Adapter。
  - 随 Intent/WorkState 删除拆分 933 行 ApplicationService 和 566 行 PersistenceService。
  - 清理死 enum、字段、配置和迁移列。
  - 统一 Skill meta、SKILL.md、Prompt 中的 taxonomy 与“一次一个问题”契约。

  测试：

  - Tool allowlist 安全边界测试必须保留。
  - candidate/tenant ownership 测试必须保留。
  - Flyway PostgreSQL migration 和 JPA schema 验证。
  - 报告包含沙箱 evidence 的集成测试。

  ### P4：仅在有测量证据后考虑

  - session single-flight，用于节省重复 LLM，不作为 correctness。
  - SemanticState cache。
  - question-bank vector index 和 MCP remote-first。
  - read-only Tool 并行。
  - 更复杂的 enrichment delivery。

  这些都不应继续混入当前核心一致性模型。

  ———

  ## 12. 五个问题的直接答案

  ### 1. 为 crash recovery 付出了多少不必要复杂度？

  很高。Adaptive 生产代码约 23,322 行，其中：

  - ActionIntent/Coordinator/Recovery 直接约 1,380 行；
  - WorkState/Reducer/Patch/Persistence 约 1,380 行；
  - Episode enrichment 状态与恢复骨架约 938 行。

  三组共约 3,698 行，占 adaptive 生产代码约 15.9%，尚未计 Tool execution/event、迁移和相关测试。其中只有
  SandboxExecution、AnalysisJob 等真实外部任务需要持久执行状态；ASK、只读 Tool、WorkState Patch 和大部分 enrichment
  checkpoint 都可从最近领域事实重算。

  ### 2. 有多少地方是在“为了 Agent 化而 Agent 化”？

  四个内部 Tool 中：

  - load_skill：伪 Tool；
  - rubric_lookup：伪 Tool；
  - sandbox_submit：伪 Tool；
  - question_bank_search：语义上是真 Tool，但当前下线。

  因此按 active Tool 算是 3/3，按全部 Tool 算是 3 个伪 Tool + 1 个未形成闭环的真 Tool。此外，ReAct、Role Registry、
  ToolObservation、TOOL_FACT budget、generic ToolExecution 都在支付 Agent Runtime 的结构成本，却没有真实 Agent loop。

  ### 3. 当前 Agent 的真实自主决策空间有多大？

  控制面接近零。ASK/CALL_TOOL/FINISH、gap 选择、Target 切换、达到 expectedDepth 后是否继续、工具顺序和并行全部由 Java决
  定。模型真正拥有的是初始计划语义、回答评估提案和问题自然语言，属于中等内容自主性。准确说，这是“有多个 LLM 语义模块的确
  定性 workflow”，不是自主控制型 Agent。

  ### 4. 删除 30%～50% orchestration/state/validation 代码会丢失什么？

  真正会丢失的只有：

  - 某一次未提交 LLM/只读查询中间结果的精确审计轨迹；
  - 崩溃后从某个 Intent 步骤继续，而不是从最新领域事实重算；
  - 部分避免重复 LLM 的性能优化；
  - 尚未上线的题库动态 Tool 设想；
  - 一些 materialized projection 的读取速度。

  不会丢失：

  - Session/Turn 问答；
  - Plan、Assessment、Evidence、ProbeGap；
  - 沙箱执行、隔离、超时和结果；
  - 稳定业务幂等；
  - 权限与数据完整性；
  - Episodic memory 的真实历史闭环；
  - 最终报告。

  前提是先用 Turn/Assessment/SandboxExecution 的稳定事实替换当前 API 投影和结果消费点，而不是直接删表不改读路径。

  ### 5. 哪些设计会让有经验的面试官觉得在解决不存在的问题？

  最容易被追问的是：

  - 为什么加载一个已经确定的 Skill 还要让 LLM function call？
  - 为什么候选人代码提交的参数全部确定，仍包装成 Agent Tool？
  - 为什么叫 ReAct Runtime，却没有 Tool→Observation→LLM 循环？
  - 为什么可重算的 ASK 和 Repository 查询需要 Intent、Execution、Revision、Recovery Scheduler？
  - 为什么 WorkState 同时保存 JSON、投影列、逻辑 revision、Patch 和 JPA version？
  - 为什么 Patch 历史不能重放，却按事件溯源的复杂度建设？
  - 为什么题库 Tool 已下线，向量索引、MCP fallback、状态和测试仍在运行？
  - 为什么为避免重复一次 LLM 建完整恢复协议，却让真正的沙箱幂等键使用随机 UUID？
  - 为什么多层状态/锁/unique 没有阻止重复 LLM，却仍留下 SemanticState 和 AnalysisJob 的真实竞态？
  - 为什么 ToolResultEvent 声称生成追问，实际永远返回空，前端还展示该模型？

  最终判断：应该保留的是少量硬业务约束和外部副作用事实；当前最值得删除的是“恢复 Java workflow 中间步骤”的整套协议。删除
  这些机制不会让系统变得不可靠，反而会减少事实漂移、恢复死角和并发失败面。
