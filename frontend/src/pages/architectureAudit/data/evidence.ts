import type { CodeEvidence } from '../auditTypes';

const APP = 'app/src/main/java/interview/guide/modules/interview/agent/adaptive';

export const CODE_EVIDENCE: readonly CodeEvidence[] = [
  // ── RC-01 可重算步骤当成 durable workflow ─────────────────────────────
  {
    id: 'E-01', rootCauseId: 'durable-workflow', severity: 'P0',
    title: 'ToolResultEvent 两阶段“恢复”制造永久丢投递窗口',
    refs: [
      `${APP}/persistence/session/AdaptiveInterviewPersistenceService.java:92`,
      `${APP}/persistence/session/AdaptiveInterviewPersistenceService.java:114`,
      `${APP}/persistence/algorithm/JpaAlgorithmResultReadyDeliveryStore.java:40`,
      'app/src/test/java/interview/guide/modules/interview/agent/adaptive/persistence/algorithm/JpaAlgorithmResultReadyDeliveryStoreIntegrationTest.java:58',
    ],
    finding:
      'reserve 与 complete 是两个独立事务；补偿扫描的 not exists 只判断“存在任意 Event”，不区分 RECEIVED / COMPLETED，集成测试显式锁定了这一行为。',
    impact: '进程在 reserve 后崩溃，事件永远停在 RECEIVED，扫描器也不会重投——复杂恢复协议反而制造了不可恢复状态。',
    fix: '以 SandboxExecution 为事实源；结果消费用一个原子 processed / unique evidence 写入，不再 reserve → complete。',
    tags: ['tool-result', 'crash-hole', 'transaction'],
  },
  {
    id: 'E-02', rootCauseId: 'durable-workflow', severity: 'P0', severityLabel: 'P0/P1',
    title: 'Generic ActionIntent 复制状态，FAILED 卡死且回答可分裂',
    refs: [
      `${APP}/application/ActionIntentExecutor.java:113`,
      `${APP}/api/AdaptiveInterviewResponse.java:13`,
      `${APP}/application/PersistentActionCoordinator.java:92`,
      `${APP}/application/AdaptiveInterviewApplicationService.java:363`,
    ],
    finding:
      'FAILED 状态拒绝正常 resume，只能显式创建新 Intent；标准响应不返回 intentId；resume 只校验 turnIndex，随后用本次请求的 answer 重建 Prompt / Tool 上下文。',
    impact: '用户无法自然触发 retry；已持久化回答 A 可被新请求 B 的 payload 驱动沙箱与 Prompt，恢复协议反而破坏事实一致性。',
    fix: '删除 ASK / read-only / sandbox generic Intent；从最近领域事实重算；沙箱只恢复 SandboxExecution。',
    tags: ['intent', 'recovery', 'source-of-truth'],
  },
  {
    id: 'E-03', rootCauseId: 'durable-workflow', severity: 'P1',
    title: 'WorkState 是第二套领域数据库，而且内部再次重复',
    refs: [
      `${APP}/persistence/working/AdaptiveWorkStateEntity.java:25`,
      `${APP}/persistence/working/AdaptiveWorkStateEntity.java:55`,
    ],
    finding:
      '一行同时存 revision、phase、activeIntent、完整 stateJson 和 JPA @Version；toDomain() 只解码 JSON，标量列没有任何查询消费者（Repository 是空接口）。',
    impact: 'Plan、Turn、Assessment、Gap、Intent 与 WorkState 必须持续同步；漂移面与冲突面同时扩大。',
    fix: '先删 Patch journal，再把 WorkState 改为按领域事实组装的 DecisionContext / Coverage。',
    tags: ['work-state', 'duplication', 'version'],
  },
  {
    id: 'E-04', rootCauseId: 'durable-workflow', severity: 'P1',
    title: 'WorkState Patch 不能 replay，不是事件溯源',
    refs: [`${APP}/persistence/working/WorkStatePersistenceService.java:40`],
    finding:
      '初始化 Patch 只含 SetFocus，不包含 targets、phase、budget 或 awaiting turn；Patch 表只有 save 和 source 幂等查重，没有任何 replay 逻辑。',
    impact: '支付事件溯源的复杂度，实际只得到一张昂贵的去重表。',
    fix: '删除 Patch journal；Coverage 由 Plan / Turn / Assessment / Gap 请求内组装。',
    tags: ['patch', 'event-sourcing', 'replay'],
  },
  {
    id: 'E-05', rootCauseId: 'durable-workflow', severity: 'P2',
    title: '只读 Tool 也一律落 agent_tool_calls 执行记录',
    refs: [
      `${APP}/persistence/intent/ActionIntentTransactionService.java:109`,
      `${APP}/application/ActionIntentExecutor.java:86`,
    ],
    finding: 'completeTool 对所有 Tool Intent（只读与否不区分）无条件写入 ToolExecution 记录。',
    impact: 'classpath 读取与 Repository 查询都支付了执行状态成本，却没有任何 observation-driven reasoning 消费者。',
    fix: 'read-only Tool 默认不持久化；仅保留有明确审计消费者的成功事实。',
    tags: ['tool-execution', 'persistence', 'read-only'],
  },

  // ── RC-02 同一 correctness 没有唯一 owner ────────────────────────────
  {
    id: 'E-06', rootCauseId: 'correctness-owner', severity: 'P0',
    title: '沙箱幂等键是随机 Intent UUID，不是业务键',
    refs: [
      `${APP}/application/ActionIntentPlanFactory.java:29`,
      `${APP}/persistence/intent/ActionIntentPersistenceService.java:75`,
      `${APP}/algorithm/judge/AlgorithmSubmissionService.java:28`,
    ],
    finding:
      'ASK / Tool 都用 UUID.randomUUID() 同时承担 intentId 与 idempotencyKey；retry 生成新 UUID；沙箱直接用它做 execution ID 与 executionExists 去重。',
    impact: '同一业务提交在 FAILED 后 retry 会绕过去重，可产生第二个外部沙箱执行；随机键只能防“同一 Intent 重放”，防不了“同一业务副作用重复”。',
    fix: '使用 sessionId + turnIndex + targetId + sourceHash + runMode 稳定业务键；retry 复用同一 SandboxExecution。',
    tags: ['idempotency', 'sandbox', 'retry'],
  },
  {
    id: 'E-07', rootCauseId: 'correctness-owner', severity: 'P0',
    title: 'Semantic projection 在加锁前读取贡献，确定丢更新',
    refs: [
      `${APP}/persistence/memory/SemanticMemoryPersistenceService.java:59`,
      `${APP}/persistence/memory/SemanticStateEntity.java:142`,
      `${APP}/persistence/memory/SemanticStateRepository.java:15`,
    ],
    finding:
      'recompute 先在锁外读取并聚合全部 contributions，之后才通过 findLocked（PESSIMISTIC_WRITE）锁 state 行；@Version 在悲观锁重读后总能通过，挡不住丢更新。',
    impact: '两个并发事务各自写入过期 aggregate：contribution 表有 C1+C2，materialized state 可能只包含其中一个，两个事实源漂移。',
    fix: '首选删除 materialized state、按读聚合；若有性能证据，先锁 scope 再读 contributions，并声明它只是 cache。',
    tags: ['semantic-memory', 'race', 'lost-update'],
  },
  {
    id: 'E-08', rootCauseId: 'correctness-owner', severity: 'P0',
    title: 'AnalysisJob 完成与超时没有并发 owner',
    refs: [
      `${APP}/codeanalysis/job/AnalysisJobEntity.java`,
      `${APP}/codeanalysis/job/CodeAnalysisPersistenceService.java:79`,
      `${APP}/codeanalysis/job/CodeAnalysisPersistenceService.java:212`,
    ],
    finding:
      'Entity 无 @Version、行锁或条件更新；complete 先读后写产物再置 COMPLETED；timeout 批量读后直接修改。isTerminal() 守卫只在单事务内存内生效。',
    impact: '可出现 TIMED_OUT Job 携带完整成功产物，或迟到结果翻转终态——状态与产物不一致。',
    fix: '行锁、@Version、条件更新三选一，只保留一个 correctness owner，不要叠加三套。',
    tags: ['analysis-job', 'race', 'terminal-state'],
  },
  {
    id: 'E-09', rootCauseId: 'correctness-owner', severity: 'P1',
    title: '同一回答并发提交：多个约束随机胜出，LLM 已烧两次',
    refs: [
      `${APP}/application/AdaptiveInterviewApplicationService.java:468`,
      `${APP}/application/AdaptiveInterviewApplicationService.java:510`,
    ],
    finding:
      '两个并发事务都通过事务外 assertCanAnswer 并各自调用 Assessment LLM；最终哪个约束（WorkState @Version / Patch unique / Assessment unique / Episode unique / Session @Version）胜出取决于 flush 顺序；乐观锁 catch 还不覆盖 prepareAction，unique 冲突可能泄漏成非统一异常。',
    impact: '数据库大概率能阻止第二套事实，但 loser 已经烧掉一次 LLM，且错误类型不确定。',
    fix: 'UPDATE turn SET answer = ? WHERE id = ? AND answer IS NULL 条件 claim；loser 统一映射为“会话已推进”；single-flight 只作为性能优化，不进 correctness 模型。',
    tags: ['answer', 'optimistic-lock', 'double-llm'],
  },
  {
    id: 'E-10', rootCauseId: 'correctness-owner', severity: 'P1',
    title: '评估链嵌套重试，最坏 2×2 次模型调用',
    refs: [
      'app/src/main/java/interview/guide/common/ai/StructuredOutputInvoker.java:59',
      `${APP}/assessment/depth/DepthAssessmentAgent.java:31`,
    ],
    finding:
      'StructuredOutputInvoker 自带最多 2 次尝试；DepthAssessmentAgent 捕获语义校验失败后又整体重调一次带重试的 generator。',
    impact: '一次回答评估最坏触发 4 次模型调用；retry owner 不唯一。',
    fix: 'StructuredOutputInvoker 只负责单次调用解析策略；语义 rewrite 由 Assessment Agent 承担且第二次不再获得完整解析 retry budget。',
    tags: ['retry', 'llm', 'cost'],
  },
  {
    id: 'E-11', rootCauseId: 'correctness-owner', severity: 'P1',
    title: 'CodeTrace 配额“先计数后调用”，没有原子 reservation',
    refs: [
      `${APP}/codeanalysis/trace/CodeTraceService.java:20`,
      `${APP}/codeanalysis/trace/CodeTracePersistenceService.java:25`,
    ],
    finding: '每场最多 3 次的配额是 countBySessionId 计数后执行外部调用、成功后再插记录，代码注释自述“先用后扣”。',
    impact: '并发下可突破配额；queryHash / createdAt 不提供配额 correctness。',
    fix: '改为原子 reservation / slot 机制，而不是 count 后外部调用。',
    tags: ['quota', 'race', 'code-trace'],
  },

  // ── RC-03 Tool API 包装普通业务编排 ──────────────────────────────────
  {
    id: 'E-12', rootCauseId: 'pseudo-tools', severity: 'P1',
    title: '所谓 ReAct Runtime 只有一次模型调用',
    refs: [`${APP}/runtime/BoundedActionRuntime.java:30`],
    finding:
      'ReActModelContext 以空 observations 创建，随后只调用一次 modelGateway.nextAction；不存在 Tool execution → observation → 再推理循环（仅输出校验失败时追加一条合成 rejection observation 重试）。',
    impact: '增加 ReActRequest / Result / Observation / Role / Intent 等概念，却没有对应能力；错误 Tool 调用也无法作为 observation 回给模型。',
    fix: '当前诚实降级为 InterviewerGenerator；真实动态检索上线时才实现最小内存 Tool loop。',
    tags: ['react', 'runtime', 'observation'],
  },
  {
    id: 'E-13', rootCauseId: 'pseudo-tools', severity: 'P2',
    title: '多 ToolCall 被静默截断，只保留第一个',
    refs: [`${APP}/role/AdaptiveAgentResponseMapper.java:70`],
    finding: 'Provider 返回多个 ToolCall 时只取 getFirst()，其余仅写 warn 日志后丢弃。',
    impact: '隐藏能力降级；独立只读查询也无法并行。',
    fix: '真实 Tool loop 落地时支持 0..N 次与无依赖并行；当前先删除并行假象。',
    tags: ['tool', 'parallel', 'silent-degradation'],
  },
  {
    id: 'E-14', rootCauseId: 'pseudo-tools', severity: 'P1',
    title: 'active Tool 3/3 都不是动态 Tool，唯一的真 Tool 已下线',
    refs: [
      `${APP}/role/AgentRoleRegistry.java:27`,
      `${APP}/tool/LoadSkillTool.java:49`,
      `${APP}/tool/RubricLookupTool.java:56`,
      `${APP}/tool/SandboxSubmitTool.java:55`,
    ],
    finding:
      'whitelist 仅含 load_skill / rubric_lookup / sandbox_submit：分别是固定 skillId 的 service 读取、普通 Repository 查询、参数必须与候选人提交逐项相等；唯一语义上需要模型决策的 question_bank_search 因 embedding 未就绪被明确下线。',
    impact: '当前生产主链成功的模型动态 Tool 调用数为 0；维护者会误判 Runtime 能力边界。',
    fix: '删除三个伪 Tool 身份与题库死接线；真实检索需求出现时再从最小 Agent loop 恢复。',
    tags: ['tool', 'whitelist', 'reachability'],
  },
  {
    id: 'E-15', rootCauseId: 'pseudo-tools', severity: 'P1',
    title: '沙箱调用由 Java 完整构造，模型没有选择权',
    refs: [
      `${APP}/application/AdaptiveInterviewApplicationService.java:588`,
      `${APP}/application/AdaptiveInterviewApplicationService.java:627`,
    ],
    finding: 'prepareSandboxAction / sandboxProposal 直接 new ToolCallAction，toolName、targetId、runMode 均由 Java 决定；Tool validate 又要求参数与候选人提交完全一致。',
    impact: '模型没有“是否调用”或“参数选择”能力，却承担 Tool 协议与持久化成本。',
    fix: 'Application 直接调用提交 Service；保留 SandboxExecution、隔离、Stream 与稳定幂等。',
    tags: ['sandbox', 'orchestration', 'pseudo-tool'],
  },
  {
    id: 'E-16', rootCauseId: 'pseudo-tools', severity: 'P2',
    title: 'ToolGateway 截断 JSON 制造无效输出',
    refs: [`${APP}/tool/ToolGateway.java:117`],
    finding: '超长结果直接 output.substring(0, max) + "[truncated]"，在 JSON 字符串中间截断再拼接裸标记。',
    impact: '产物必然是无效 JSON，下游任何解析都会失败。',
    fix: '随伪 Tool 一并删除；保留期改为结构化截断标记。',
    tags: ['gateway', 'json', 'truncation'],
  },

  // ── RC-04 Java 策略与 Prompt 双重控制 ────────────────────────────────
  {
    id: 'E-17', rootCauseId: 'dual-policy', severity: 'P1', severityLabel: 'P1/P2',
    title: 'Java 把语义策略写死',
    refs: [`${APP}/core/memory/NextActionPolicy.java:11`],
    finding:
      '达到 expectedDepth 且无 open issue 即结束 Target、answer issue 优先于 tool issue、findFirst 取第一个 gap、nextPendingTargetId 顺序切 Target。',
    impact: '模型无法决定最值得验证的 gap、是否继续深挖或切换目标——只剩措辞自主性。',
    fix: '模型提案语义动作；Java 只裁决预算、合法 target、权限、来源和终态。',
    tags: ['autonomy', 'policy', 'gap'],
  },
  {
    id: 'E-18', rootCauseId: 'dual-policy', severity: 'P1',
    title: '生产 Gap 全部硬编码为 CANDIDATE_ANSWER，CALL_TOOL 不可达',
    refs: [`${APP}/application/AssessmentWorkStatePlanner.java:92`],
    finding:
      'issue() 把 evidenceMethod 硬编码为 CANDIDATE_ANSWER；全仓只有测试手工构造 TOOL_FACT WorkIssue，因此 CALL_TOOL policy 分支生产不可达。',
    impact: 'Tool planning 状态（suggestedTools、toolBudget、TOOL_FACT objective）是为不存在的生产路径预留的假状态。',
    fix: '删除不可达分支与关联 UI；真实题库检索成为当前需求时再引入。',
    tags: ['policy', 'tool-fact', 'unreachable'],
  },
  {
    id: 'E-19', rootCauseId: 'dual-policy', severity: 'P1',
    title: 'Prompt 要求调用 Tool，ASK Intent 又拒绝 ToolCall',
    refs: [
      'app/src/main/resources/prompts/adaptive-agent-interviewer-system.st:5',
      `${APP}/application/ActionIntentExecutor.java:168`,
    ],
    finding:
      'system prompt 要求“每一步调用一个已注册工具”并规定必须先 sandbox_submit；但 ASK 执行器只接受单一问题，模型返回 ToolCall 会直接抛 AI_SERVICE_ERROR。',
    impact: '模型遵循系统提示反而可能让整轮失败；同一套规则在代码与 Prompt 中双重维护、互相漂移。',
    fix: '重写 Prompt 使其只描述模型真实拥有的决策空间；控制规则只留 Java 一处。',
    tags: ['prompt', 'contract-drift', 'intent'],
  },

  // ── RC-05 派生状态平台化 ─────────────────────────────────────────────
  {
    id: 'E-20', rootCauseId: 'derived-platform', severity: 'P0',
    title: 'Episode enrichment 模板写了字面量 <contextJson>',
    refs: [
      'app/src/main/resources/prompts/adaptive-agent-episode-enrichment-user.st:3',
      `${APP}/memory/SpringAiEpisodeEnrichmentGenerator.java:72`,
    ],
    finding:
      '模板内容是字面量 <contextJson>，而渲染走 Spring AI PromptTemplate（{var} 语法）——变量根本不会替换，模型收到的就是字面量；与仓库 StringTemplate 约定也不一致。',
    impact: '完整的四态恢复状态机可以“成功”运行，但 enrichment LLM 始终看不到权威 Episode 上下文。',
    fix: '先修复模板变量（真实 bug），再按 SIMPLIFY 收敛 enrichment 状态机。',
    tags: ['memory', 'prompt', 'bug'],
  },
  {
    id: 'E-21', rootCauseId: 'derived-platform', severity: 'P1',
    title: '内部 WorkState 投影泄漏为前端 API 契约',
    refs: [`${APP}/api/AdaptiveInterviewResponse.java:30`],
    finding: '响应直接把 workState().targets() 的内部 TargetWorkState / CapabilityTarget 映射为前端维度契约。',
    impact: '内部执行状态意外成为外部契约，删除 WorkState 前必须先替换读路径。',
    fix: '维度 API 改由 Plan / Turn / Assessment 事实投影输出。',
    tags: ['api', 'contract', 'work-state'],
  },
  {
    id: 'E-22', rootCauseId: 'derived-platform', severity: 'P1',
    title: 'ToolResultFollowUp 伪模型：永远为空，前端仍渲染',
    refs: [
      `${APP}/persistence/session/AdaptiveAgentToolResultEventEntity.java:52`,
      `${APP}/application/AdaptiveInterviewApplicationService.java:794`,
      'frontend/src/pages/AdaptiveInterviewPage.tsx:519',
    ],
    finding:
      'responseContent / decisionReason 从未赋值（无 setter，恒为 null）；handleToolResult 只写 WorkState Patch 后固定返回 Optional.empty()；前端却仍渲染该模型的空气泡。',
    impact: '一条从 DB 到前端的完整链路展示一个永远为空的“追问”。',
    fix: '删除伪 API / UI；沙箱结果需要问题时创建普通 Turn，只需事实时记录 Evidence。',
    tags: ['follow-up', 'frontend', 'dead-model'],
  },
  {
    id: 'E-23', rootCauseId: 'derived-platform', severity: 'P2', severityLabel: 'P2/P3',
    title: '超大编排服务与固定抽象掩盖真实依赖',
    refs: [
      `${APP}/application/AdaptiveInterviewApplicationService.java:90`,
      `${APP}/persistence/session/AdaptiveInterviewPersistenceService.java:61`,
      `${APP}/role/AgentRoleRegistry.java:11`,
    ],
    finding:
      'ApplicationService 933 行、18 个构造器依赖；PersistenceService 566 行、跨十余仓库；固定 Registry、单消费者 Factory 和 Dependencies / Repositories 参数袋只增加跳转而没增加能力。',
    impact: 'Controller → Application → Coordinator → Executor → TransactionService → Persistence 的调用链难以追踪。',
    fix: '随 Intent / WorkState 删除自然收缩编排层；删除无行为参数袋；外部边界端口（LLM、S3、Sandbox、HTTP）保留。',
    tags: ['abstraction', 'service', 'parameter-bag'],
  },
  {
    id: 'E-24', rootCauseId: 'derived-platform', severity: 'P3',
    title: '死状态、死字段、死配置',
    refs: [
      `${APP}/assessment/practice/PracticeStatus.java:7`,
      `${APP}/core/session/AdaptiveInterviewSession.java:21`,
    ],
    finding:
      'PracticeStatus 只有 PENDING；agent_turns.answered_at 实体从不映射；sandbox_executions.pending_rejudge / retry_count 无真实使用；agent_tool_calls.role 生产恒为 INTERVIEWER；runtime_version 恒为 adaptive-agent-v1；WorkIssueStatus.INVESTIGATING 无生产者。',
    impact: 'schema 与代码模型描述的不是同一个系统，误导后来者。',
    fix: '死 enum / 字段 / 配置通过新 migration 删除（不改历史 migration）；真实需求出现时以实际 producer / consumer 重新定义。',
    tags: ['dead-code', 'schema', 'enum'],
  },
];
