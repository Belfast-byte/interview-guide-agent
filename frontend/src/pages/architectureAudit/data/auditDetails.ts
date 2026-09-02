import type {
  ArchitectureLane,
  DecisionItem,
  FactSourceRow,
  Hotspot,
  MatrixItem,
  RoadmapPhase,
  StateMachineRow,
  ValidationRow,
} from '../auditTypes';

export const HOTSPOTS: readonly Hotspot[] = [
  {
    label: 'ActionIntent / Coordinator / Recovery',
    lines: 1380,
    disposition: '为可重算步骤建的持久恢复协议',
    decision: 'DELETE',
    kind: 'accidental',
  },
  {
    label: 'WorkState / Reducer / Patch / Persistence',
    lines: 1339,
    disposition: '复制 Plan / Turn / Assessment / Gap 事实',
    decision: 'DELETE',
    kind: 'accidental',
  },
  {
    label: 'ApplicationService（编排集中）',
    lines: 933,
    disposition: '18 个构造器依赖；复杂协议导致的编排集中',
    decision: 'SIMPLIFY',
    kind: 'mixed',
  },
  {
    label: 'Episode enrichment 状态与恢复',
    lines: 811,
    disposition: '输出有价值，四态执行框架过重',
    decision: 'SIMPLIFY',
    kind: 'mixed',
  },
  {
    label: 'PersistenceService（会话持久化）',
    lines: 566,
    disposition: '跨十余仓库；承载 crash hole 所在的两阶段消费',
    decision: 'SIMPLIFY',
    kind: 'mixed',
  },
];

export const DECISIONS: readonly DecisionItem[] = [
  // DELETE
  {
    id: 'D-01', rootCauseId: 'durable-workflow', title: 'Generic ActionIntent（ASK / read-only / sandbox）',
    decision: 'DELETE', stage: 'P1',
    reason: 'ASK 未写为 Turn 前只是可重算草稿；只读查询可重跑；沙箱已有 SandboxExecution。',
    replacement: 'Turn 是 ASK 事实；SandboxExecution 是副作用事实；Session / Assessment unique 控制推进。',
    risk: '失去某次未提交 LLM 输出的精确审计与 Intent 级审计；无独立 correctness 风险。',
  },
  {
    id: 'D-02', rootCauseId: 'durable-workflow', title: 'WorkState Patch journal 与重复指针',
    decision: 'DELETE', stage: 'P1',
    reason: '初始化 Patch 无法 replay；相同来源已由 Turn / Assessment / Gap / Sandbox 业务 unique 约束。',
    replacement: 'Coverage / DecisionContext 按事实组装。',
    risk: '需先替换维度 API 对 WorkState targets 的读取（见 E-21）。',
  },
  {
    id: 'D-03', rootCauseId: 'durable-workflow', title: 'ACTION_PENDING / activeActionIntentId / recovery scheduler',
    decision: 'DELETE', stage: 'P1',
    reason: '它们是 generic Intent 的执行指针，不承载独立业务事实。',
    replacement: '普通计算不存 pending；外部任务以 SandboxExecution 扫描恢复。',
    risk: '无独立 correctness 风险。',
  },
  {
    id: 'D-04', rootCauseId: 'pseudo-tools', title: 'load_skill / rubric_lookup 的 Tool 身份',
    decision: 'DELETE', stage: 'P1',
    reason: 'skillId 已由 Plan 确定；Interviewer 不评分，Assessor 已直接加载固定 Skill reference。',
    replacement: 'ContextAssembler 自动装配；确有知识需求时按稳定 TopicKey 加载。',
    risk: '无独立 correctness 风险。',
  },
  {
    id: 'D-05', rootCauseId: 'pseudo-tools', title: 'sandbox_submit 的 Agent Tool 身份',
    decision: 'DELETE', stage: 'P1',
    reason: '是否调用、target、runMode、源码均已由候选人提交和 Java 决定。',
    replacement: 'Application 直接调用提交 Service。',
    risk: '必须保留 SandboxExecution、隔离、Stream、稳定幂等和终态守卫。',
  },
  {
    id: 'D-06', rootCauseId: 'pseudo-tools', title: 'ReAct 残留与 question_bank_search 死接线',
    decision: 'DELETE', stage: 'P1',
    reason: '不存在 observation loop；TOOL_FACT / toolBudget / CALL_TOOL 分支生产不可达。',
    replacement: '诚实命名 InterviewerGenerator；真实动态检索上线时再写最小内存 loop。',
    risk: '丢失的是未上线设想，不是当前能力。',
  },
  {
    id: 'D-07', rootCauseId: 'derived-platform', title: 'ToolResultFollowUp 伪 API / UI',
    decision: 'DELETE', stage: 'P1',
    reason: 'responseContent 恒为 null，handler 固定返回 empty，前端渲染空气泡。',
    replacement: '需要问题时创建普通 Turn；只需事实时记录 Evidence。',
    risk: '必须先保留 resultId 的原子消费去重，不能裸删。',
  },
  {
    id: 'D-08', rootCauseId: 'derived-platform', title: '死 enum / 字段 / 配置',
    decision: 'DELETE', stage: 'P3',
    reason: 'PracticeStatus、HINT、TOOL_RESULT、correctsEpisodeId、answerSummary、answered_at 等无生产者或无消费者。',
    replacement: '真实需求出现时以实际 producer / consumer 重新定义。',
    risk: '删除字段先核对外部 API 与存量数据；通过新 migration，不改历史 migration。',
  },
  {
    id: 'D-09', rootCauseId: 'derived-platform', title: '纯参数袋与单实现转发',
    decision: 'DELETE', stage: 'P3',
    reason: 'EpisodeEnrichment*Dependencies、SemanticMemoryRepositories、AgentRoleRegistry、QuestionIdentityFactory 等只转发不增加能力。',
    replacement: '构造器注入与现有 Spring 配置；固定配置归所属 service。',
    risk: '外部边界接口（AgentModelGateway、SandboxWorker、S3 / VectorStore / HTTP ports）必须保留。',
  },
  // SIMPLIFY
  {
    id: 'D-10', rootCauseId: 'durable-workflow', title: 'Persistent WorkState → 请求内 DecisionContext / Coverage',
    decision: 'SIMPLIFY', stage: 'P1',
    reason: '它是 workflow state，不是 cognitive memory；focus / evidence / gap / budget 都已存在于其他事实。',
    replacement: '请求内由 Plan / Turn / Assessment / Gap 组装。',
    risk: '需验证投影与维度 API 输出一致。',
  },
  {
    id: 'D-11', rootCauseId: 'derived-platform', title: 'Episode enrichment → 未完成扫描 + validated tags',
    decision: 'SIMPLIFY', stage: 'P2',
    reason: 'tags 有真实消费者，但 LLM enrichment 可重算、没有不可逆副作用。',
    replacement: '单一 scheduler 扫未完成项 + lastError；删除 PROCESSING checkpoint / claim / stale recovery。',
    risk: '极低概率重复一次 enrichment LLM，属于成本而非 correctness。',
  },
  {
    id: 'D-12', rootCauseId: 'derived-platform', title: 'Materialized SemanticState → 按读聚合',
    decision: 'SIMPLIFY', stage: 'P0',
    reason: '可由 contributions 确定性聚合，且当前物化实现存在并发丢更新。',
    replacement: '默认按读聚合；测出性能问题时作为可重建 cache（先锁 scope 再读）。',
    risk: '需观测候选人 Memory 查询延迟。',
  },
  {
    id: 'D-13', rootCauseId: 'correctness-owner', title: 'ToolResult delivery → 单一原子消费事实',
    decision: 'SIMPLIFY', stage: 'P0',
    reason: '五态两阶段投递有 crash hole；真正有价值的只是“resultId 已消费”。',
    replacement: 'resultId 唯一约束在一个短事务中完成消费，或 SandboxExecution 上原子标记 processed。',
    risk: '需覆盖 reserve 后崩溃重投测试。',
  },
  {
    id: 'D-14', rootCauseId: 'pseudo-tools', title: 'ToolExecution → 仅保留有审计消费者的成功事实',
    decision: 'SIMPLIFY', stage: 'P2',
    reason: '只读调用没有审计消费者，落库只是成本。',
    replacement: 'read-only 不存；sandbox 用 SandboxExecution。',
    risk: '确认无报表依赖 agent_tool_calls 后再删列。',
  },
  // KEEP
  {
    id: 'D-15', rootCauseId: 'correctness-owner', title: 'Session 生命周期与单一并发版本',
    decision: 'KEEP', stage: 'P0',
    reason: '用户可见事实，控制是否能继续回答；一个推进版本足够。',
    replacement: '保留 Session status + @Version。',
    risk: '不可删除——这是业务必要复杂度。',
  },
  {
    id: 'D-16', rootCauseId: 'correctness-owner', title: 'Turn / Assessment / ProbeGap 业务唯一约束',
    decision: 'KEEP', stage: 'P0',
    reason: '保护用户可见问答、一次评估、gap 只追问一次。',
    replacement: 'Turn (sessionId, turnIndex) unique + answer 条件 claim + Assessment unique + Turn provenance unique。',
    risk: '不可删除。',
  },
  {
    id: 'D-17', rootCauseId: 'correctness-owner', title: '权限与 ownership 校验',
    decision: 'KEEP', stage: 'P0',
    reason: '认证身份与行级 ownership 解决不同边界，两者都是必要防线。',
    replacement: '维持现状。',
    risk: '不可删除。',
  },
  {
    id: 'D-18', rootCauseId: 'correctness-owner', title: 'evidence quote / provenance 原文命中',
    decision: 'KEEP', stage: 'P0',
    reason: '证据必须逐字命中回答原文或真实分析产物，这是评估可信度的根基。',
    replacement: '保留最终 exact-match validator；Prompt 只是模型契约，不是 correctness owner。',
    risk: '不可删除。',
  },
  {
    id: 'D-19', rootCauseId: 'correctness-owner', title: 'SandboxExecution、隔离与终态守卫',
    decision: 'KEEP', stage: 'P0',
    reason: '外部任务不可任意重放，终态与真实产物需要恢复；行锁 + terminal guard 是正确实现。',
    replacement: '保留并作为沙箱唯一执行事实源。',
    risk: '不可降级为普通内存 Tool。',
  },
  {
    id: 'D-20', rootCauseId: 'correctness-owner', title: 'AnalysisJob 外部任务事实',
    decision: 'KEEP', stage: 'P0',
    reason: '真实异步外部分析任务，终态不可翻转。',
    replacement: '补一个原子终态 owner（行锁 / @Version / 条件更新三选一）。',
    risk: '保留事实，修并发。',
  },
  {
    id: 'D-21', rootCauseId: 'derived-platform', title: 'Episodic Memory 历史题去重与 practice coaching 闭环',
    decision: 'KEEP', stage: 'P1',
    reason: '真实 producer → storage → retrieval → consumer 闭环，影响后续题目与练习 Prompt。',
    replacement: '保留 Episode / Tag / QuestionExposure 核心事实。',
    risk: '不可一刀切删除。',
  },
  {
    id: 'D-22', rootCauseId: 'dual-policy', title: '确定性最终报告',
    decision: 'KEEP', stage: 'P0',
    reason: '无 LLM 的确定性读取聚合是正确设计。',
    replacement: '维持现状。',
    risk: '无。',
  },
  // REDESIGN
  {
    id: 'D-23', rootCauseId: 'correctness-owner', title: '回答并发：Turn 条件 claim + Session version',
    decision: 'REDESIGN', stage: 'P0',
    reason: '当前多个约束随机胜出且 loser 已烧掉一次 LLM。',
    replacement: 'answer IS NULL 条件更新先 claim，loser 统一返回“会话已推进”。',
    risk: '需覆盖真实双线程竞争测试。',
  },
  {
    id: 'D-24', rootCauseId: 'correctness-owner', title: '沙箱幂等：稳定业务键',
    decision: 'REDESIGN', stage: 'P0',
    reason: '随机 Intent UUID 防不住跨 retry 的重复外部副作用。',
    replacement: 'sessionId + turnIndex + targetId + sourceHash + runMode；retry 复用同一 execution。',
    risk: '需迁移存量 execution 键。',
  },
  {
    id: 'D-25', rootCauseId: 'dual-policy', title: 'Agent control：模型提案策略，Java 裁决硬约束',
    decision: 'REDESIGN', stage: 'P2',
    reason: 'Java 已把语义策略算完，模型只有措辞自主性。',
    replacement: '模型提案 gap / continue / switch / 只读检索；Java 保留合法 Target、预算、来源、权限、终态。',
    risk: '不能放松权限、预算、来源和终态约束。',
  },
  {
    id: 'D-26', rootCauseId: 'pseudo-tools', title: 'Tool loop：真实动态只读 Tool 上线时内存 0..N 轮',
    decision: 'REDESIGN', stage: 'P2',
    reason: '当前无真实动态 Tool，loop 是空转框架。',
    replacement: '题库检索成为当前需求时引入；Tool failure 作为 observation。',
    risk: '端到端 Tool → observation → ASK 测试先行。',
  },
  {
    id: 'D-27', rootCauseId: 'durable-workflow', title: '异步结果：SandboxExecution 直接驱动 Evidence 或普通 Turn',
    decision: 'REDESIGN', stage: 'P1',
    reason: '当前只写 Patch 且 follow-up 永远为空。',
    replacement: '终态原子写入 Evidence 或 consumedAt；下一轮从事实重新组装 Context。',
    risk: '与 ToolResult 原子消费（D-13）一起改。',
  },
];

export const MATRIX_ITEMS: readonly MatrixItem[] = [
  { id: 'M-1', label: 'ToolResult 原子消费', impact: 96, cost: 25, stage: 'P0', decision: 'REDESIGN' },
  { id: 'M-2', label: '稳定 sandbox 业务键', impact: 94, cost: 40, stage: 'P0', decision: 'REDESIGN' },
  { id: 'M-3', label: 'Semantic / AnalysisJob 原子 owner', impact: 90, cost: 45, stage: 'P0', decision: 'REDESIGN' },
  { id: 'M-4', label: '回答并发 Turn 条件 claim', impact: 88, cost: 45, stage: 'P0', decision: 'REDESIGN' },
  { id: 'M-5', label: '修复 enrichment Prompt context', impact: 78, cost: 6, stage: 'P0', decision: 'REDESIGN' },
  { id: 'M-6', label: 'CodeTrace 原子配额', impact: 60, cost: 25, stage: 'P0', decision: 'REDESIGN' },
  { id: 'M-7', label: '删除 Generic ActionIntent', impact: 92, cost: 72, stage: 'P1', decision: 'DELETE' },
  { id: 'M-8', label: '事实投影替代 WorkState / Patch', impact: 90, cost: 84, stage: 'P1', decision: 'DELETE' },
  { id: 'M-9', label: '伪 Tool 降级 + ReAct 清理', impact: 74, cost: 40, stage: 'P1', decision: 'DELETE' },
  { id: 'M-10', label: '异步结果驱动 Evidence / Turn', impact: 70, cost: 38, stage: 'P1', decision: 'REDESIGN' },
  { id: 'M-11', label: 'Memory enrichment 简化', impact: 58, cost: 35, stage: 'P2', decision: 'SIMPLIFY' },
  { id: 'M-12', label: 'Agent / Java 决策权重分工', impact: 76, cost: 78, stage: 'P2', decision: 'REDESIGN' },
  { id: 'M-13', label: '死状态 / 参数袋清理', impact: 55, cost: 25, stage: 'P3', decision: 'DELETE' },
];

export const BEFORE_ARCHITECTURE: readonly ArchitectureLane[] = [
  {
    label: '用户可见事实',
    nodes: [
      { name: 'Session', tone: 'fact' },
      { name: 'Turn', tone: 'fact' },
      { name: 'Assessment / Evidence', tone: 'fact' },
    ],
  },
  {
    label: '复制的第二套状态',
    nodes: [
      { name: 'WorkState JSON + 投影列', tone: 'risk', note: '与事实同步维护' },
      { name: 'Patch journal', tone: 'risk', note: '不能 replay' },
      { name: 'revision × 3', tone: 'risk' },
    ],
  },
  {
    label: '执行 / 恢复协议',
    nodes: [
      { name: 'ActionIntent 五态', tone: 'risk' },
      { name: 'ToolExecution', tone: 'risk' },
      { name: 'ToolResultEvent 两阶段', tone: 'risk', note: '有 crash hole' },
      { name: 'recovery scheduler', tone: 'risk' },
    ],
  },
  {
    label: '真实外部副作用',
    nodes: [
      { name: 'SandboxExecution', tone: 'side-effect', note: '幂等键是随机 UUID' },
      { name: 'AnalysisJob', tone: 'side-effect', note: '无终态 owner' },
    ],
  },
];

export const AFTER_ARCHITECTURE: readonly ArchitectureLane[] = [
  {
    label: '唯一事实源',
    nodes: [
      { name: 'Session + Plan + Turn', tone: 'fact' },
      { name: 'Assessment + Evidence + ProbeGap', tone: 'fact' },
    ],
  },
  {
    label: '请求内计算（不持久化）',
    nodes: [
      { name: 'ContextAssembler', tone: 'compute' },
      { name: 'Coverage / DecisionContext', tone: 'compute' },
      { name: 'LLM 提案 → Java 裁决', tone: 'compute' },
    ],
  },
  {
    label: '原子提交',
    nodes: [
      { name: 'Turn 条件 claim', tone: 'fact' },
      { name: '短事务写 Turn / Assessment', tone: 'fact' },
      { name: '业务 unique 兜底', tone: 'fact' },
    ],
  },
  {
    label: '副作用边界',
    nodes: [
      { name: 'SandboxExecution（稳定业务键）', tone: 'side-effect' },
      { name: '终态 → 原子写 Evidence / consumedAt', tone: 'side-effect' },
      { name: 'AnalysisJob + 原子终态 owner', tone: 'side-effect' },
    ],
  },
];

export const ROADMAP: readonly RoadmapPhase[] = [
  {
    id: 'P0',
    title: '先修真实错误，不做大重构',
    objective: '建立真正的原子边界，消除 crash hole 与竞态。',
    changes: [
      '修复 enrichment Prompt 的 {contextJson} 变量',
      'ToolResult 消费改成一个原子事务，消除 RECEIVED 永久卡死',
      '沙箱改用稳定业务幂等键，retry 复用同一 execution',
      'SemanticState 删除或改为锁后读取',
      'AnalysisJob 使用一种原子终态机制',
      '修复 ActionIntent 保留期间的 answer A/B 不一致与不可发现 FAILED',
      'CodeTrace 配额改为原子 reservation / slot',
    ],
    tests: [
      'reserve 后进程崩溃仍能再次投递',
      '同一沙箱业务重试 / 并发只创建一个 execution',
      '两个 session 并发贡献后 semantic 聚合包含二者',
      'complete 与 timeout 并发时状态与产物一致',
      'enrichment 真实 Prompt 渲染包含权威上下文',
      '同一回答不同 payload 重放不会混用',
    ],
  },
  {
    id: 'P1',
    title: '删除状态协议',
    objective: '从最近领域事实恢复，而不是恢复中间步骤。',
    changes: [
      '删除 ASK / read-only / sandbox generic ActionIntent',
      '删除 WorkState Patch journal 与 JSON / 标量重复',
      '用事实 projector 替代 WorkPhase、activeIntent、awaitingIssue',
      '删除 ToolResultFollowUp 伪 API / UI',
      'sandbox 由 Application 直接调用提交 Service',
      '删除 read-only ToolExecution 持久化',
    ],
    tests: [
      '回答与 Assessment 已落库、下一题未创建时可重新生成',
      '下一 Turn 已存在时重放不重复创建',
      '同一回答并发只产生一个 Assessment / Episode / 后继 Turn',
      'Sandbox timeout / result 终态守卫',
      '删除 WorkState 后维度 API 输出与领域事实一致',
    ],
  },
  {
    id: 'P2',
    title: '恢复合理 Agent autonomy',
    objective: '模型负责语义策略，代码负责硬约束。',
    changes: [
      'Java 继续提供合法 Target、剩余预算、硬上限',
      '模型提案选择 gap、继续深挖、切换或动态只读检索',
      '题库检索成为当前需求时才启用最小内存 Tool loop',
      'Tool failure 作为 observation；只持久化采用的 ASK / provenance',
    ],
    tests: [
      'tool call → observation → final ASK 真实端到端',
      '多 gap 时模型提案受 Java allowlist / budget 裁决',
      '无依赖只读 Tool 不因执行顺序改变结果',
      '模型不能突破 turn cap、非法 target 或伪造 provenance',
    ],
  },
  {
    id: 'P3',
    title: '清理抽象与 schema',
    objective: '让类和表重新表达当前业务，而不是未来平台。',
    changes: [
      '删除参数袋、固定 Registry、单消费者 Factory 与同层 Adapter',
      '随 Intent / WorkState 删除拆分 933 行 ApplicationService 与 566 行 PersistenceService',
      '清理死 enum、字段、配置和迁移列',
      '统一 Skill meta、SKILL.md、Prompt 中的 taxonomy 与“一次一个问题”契约',
    ],
    tests: [
      'Tool allowlist 安全边界测试必须保留',
      'candidate / tenant ownership 测试必须保留',
      'Flyway PostgreSQL migration 与 JPA schema 验证',
      '报告包含沙箱 evidence 的集成测试',
    ],
  },
  {
    id: 'P4',
    title: '仅在有测量证据后优化',
    objective: '性能机制不得重新进入 correctness 模型。',
    changes: [
      'session single-flight（节省重复 LLM，不是 correctness）',
      'SemanticState cache（可重建）',
      'question-bank vector index 与 MCP remote-first',
      'read-only Tool 并行与更复杂的 enrichment delivery',
    ],
    tests: ['性能基准与容量测试', '缓存可重建验证'],
  },
];

// ── 附录：状态机审计 ────────────────────────────────────────────────────
export const STATE_MACHINES: readonly StateMachineRow[] = [
  { name: 'AdaptiveSessionStatus', category: '领域状态机', decision: 'KEEP', note: 'CREATED / IN_PROGRESS / COMPLETED / FAILED 用户可见，控制能否继续回答' },
  { name: 'Turn.answer 是否存在', category: '领域事实', decision: 'KEEP', note: '改为 answer IS NULL 原子 claim owner' },
  { name: 'WorkPhase', category: '重复 workflow 状态', decision: 'DELETE / DERIVE', note: '可由 Session、最新 Turn、外部执行事实推导' },
  { name: 'TargetWorkStatus', category: 'Coverage 投影', decision: 'DERIVE', note: '不应独立承担 correctness' },
  { name: 'WorkIssueStatus', category: 'ProbeGap 第二状态机', decision: 'SIMPLIFY / DELETE', note: 'INVESTIGATING 无生产者；OPEN / RESOLVED 可由 Gap→Turn provenance 推导' },
  { name: 'ActionIntentStatus', category: '可重算步骤执行状态', decision: 'DELETE', note: 'generic 状态机删除；仅副作用执行需要独立实体' },
  { name: 'SandboxExecutionStatus', category: '外部副作用状态机', decision: 'KEEP', note: 'PENDING / RUNNING / DONE / TIMEOUT 有真实执行语义' },
  { name: 'ToolExecutionOutcome', category: 'Tool wrapper 状态', decision: 'DELETE（read-only）', note: '沙箱由 SandboxExecution 替代' },
  { name: 'ToolResultEventStatus', category: '投递状态机', decision: 'REDESIGN', note: '改单一原子消费事实；当前两阶段有 crash hole' },
  { name: 'EpisodeEnrichmentStatus', category: '可重算 LLM 推理状态', decision: 'SIMPLIFY', note: '不需要 PROCESSING checkpoint + stale recovery + @Version' },
  { name: 'AnalysisJobStatus', category: '外部异步任务状态', decision: 'KEEP + 补 owner', note: '补一个原子终态机制' },
  { name: 'PracticeStatus', category: '伪状态机', decision: 'DELETE', note: '只有 PENDING，生产者也只写 PENDING' },
  { name: 'TurnTriggerType.TOOL_RESULT', category: '无真实生产者', decision: 'DELETE', note: '实际 Tool result handler 不创建 Turn' },
  { name: 'EpisodeAssistanceLevel.HINT', category: '未来预留值', decision: 'DELETE', note: '当前 producer 只生成 NONE / FOLLOW_UP / TOOL_ASSISTED' },
];

// ── 附录：重复 Source of Truth ─────────────────────────────────────────
export const FACT_SOURCES: readonly FactSourceRow[] = [
  { fact: '当前等待哪一题回答', current: 'Session.currentTurn；最新无 answer 的 Turn；WorkPhase；awaitingAnswerTurnIndex', recommended: '最新 Turn + Session 状态', removable: 'WorkPhase、awaitingAnswerTurnIndex；currentTurn 暂作投影' },
  { fact: '当前有动作执行中', current: 'Intent status；active_session_id；WorkState ACTION_PENDING；activeActionIntentId', recommended: '外部任务以 SandboxExecution 为源', removable: 'WorkState phase / pointer、active-session 协议' },
  { fact: '会话已结束', current: 'Session COMPLETED；WorkState FINISHED；所有 Target 终态', recommended: 'Session', removable: 'WorkState FINISHED；Target 终态按事实投影' },
  { fact: 'Target 定义', current: 'Plan 的 dimension / focus / depth / budget / tools；WorkState 内嵌 CapabilityTarget', recommended: 'Plan', removable: 'WorkState 中的 Target 定义副本' },
  { fact: '当前 Focus', current: 'Plan.focus；WorkState.attentionFocus；SetFocus Patch', recommended: 'Plan', removable: 'attentionFocus、SetFocus' },
  { fact: 'Evidence 已存在', current: 'evidence 表；WorkState.activeEvidenceRefs；AddEvidenceRef Patch', recommended: 'Evidence 表', removable: 'WorkEvidenceRef、AddEvidenceRef（无 Prompt / Report / Policy 消费者）' },
  { fact: 'Probe gap 是否处理', current: 'ProbeGap 表；WorkIssue；awaitingIssueId；Turn.sourceProbeGapId；synthetic issue ID', recommended: 'ProbeGap + Turn provenance', removable: 'WorkIssue 生命周期、awaitingIssueId、synthetic ID' },
  { fact: '当前深度 / Coverage / 预算', current: 'Plan 目标；Assessment depth；Turn 数；WorkState snapshot', recommended: 'Plan + Turn + Assessment 的内存投影', removable: '持久化 Coverage snapshot、budget revision' },
  { fact: 'WorkState 本身', current: '标量 revision / phase / activeIntent；完整 JSON；JPA version；Patch revision', recommended: '不持久化；过渡期最多保留一种表示', removable: '投影列与 JSON 二选一；最终全部由事实组装' },
  { fact: '沙箱正在运行', current: 'Intent EXECUTING；ToolExecution PENDING；WorkState ACTION_PENDING；SandboxExecution RUNNING', recommended: 'SandboxExecution', removable: 'Intent / ToolExecution / WorkState 对执行状态的复制' },
  { fact: 'Tool result 已消费', current: 'SandboxExecution 终态；ToolResultEvent status；Patch source unique', recommended: 'SandboxExecution + 一个原子消费标记或唯一 Evidence', removable: 'ToolResultEvent 两阶段状态、第二次 Patch 去重' },
  { fact: '候选人长期能力', current: 'Assessment / Episode / Tags；SemanticContribution；SemanticState', recommended: '不可变事实或 contribution', removable: 'SemanticState 仅可作为可重建 cache' },
  { fact: '问题已向用户展示', current: 'Turn.question；QuestionExposure', recommended: 'Turn', removable: 'Exposure 只可作为检索索引' },
  { fact: 'Episode 内容', current: 'Turn / Assessment / Plan 已有 owner、session、turn、topic、depth；Episode 再复制', recommended: 'Turn / Assessment / Plan', removable: 'workRevisionBefore / After、correctsEpisodeId、未消费 answerSummary、重复 unique' },
];

// ── 附录：过度校验矩阵（A 必要 / B 优化 / C 重复 / D 无价值）────────────
export const VALIDATION_MATRIX: readonly ValidationRow[] = [
  { invariant: '用户只能访问自己的 Session', current: 'AuthenticationPrincipal；candidate / tenant owned query', needed: '两者都保留（A）', duplicate: '无——认证与行级 ownership 解决不同边界' },
  { invariant: '同一 turn 只能有一个问题', current: 'Turn unique；Session @Version；ASK Intent；WorkState awaiting', needed: 'Turn unique（A）+ Session 单一推进版本（A）', duplicate: 'ASK Intent、WorkState phase / index（C）' },
  { invariant: '同一回答只能推进一次', current: '事务外 / 内 assertCanAnswer；WorkState revision 与 @Version；Patch / Assessment / Episode unique；Session @Version', needed: '事务内条件 claim / Session version + Assessment unique', duplicate: '应用层检查（B）；WorkState revision / Patch / Episode 重复 unique（C）' },
  { invariant: '同一 gap 只能生成一题', current: 'WorkIssue；awaitingIssueId；Turn provenance；FK；unique source_probe_gap_id', needed: 'Turn FK + unique（A）', duplicate: 'WorkIssue lifecycle、synthetic ID（C）' },
  { invariant: '同一会话只能有一个 active Intent', current: 'exists 查询；active_session unique；Intent status；WorkState pointer / phase；basedOnRevision', needed: '若保留 Intent，DB unique（A）', duplicate: 'exists（B）；WorkState pointer / revision（C）' },
  { invariant: 'Evidence 必须来自原回答', current: 'Prompt 要求；Assessor semantic validation；最终 quote exact match', needed: '一个最终 exact-match validator（A）', duplicate: 'Prompt 是模型契约；“重试后静默丢无效 quote”会让规则失真' },
  { invariant: 'Tool 参数合法', current: 'Coordinator validate；Gateway validate；Tool.execute 再解析 / validate', needed: 'typed Application command 或 Gateway 系统边界一次（A）', duplicate: '其余两次（C / D）' },
  { invariant: '沙箱结果不能被 timeout 覆盖', current: '行级悲观锁；锁后 terminal guard；状态 enum', needed: '行锁 + terminal guard（A）', duplicate: 'DB enum check 只保护形状，不重复' },
  { invariant: '沙箱提交不能重复副作用', current: '随机 Intent key；executionExists；SandboxExecution PK；Stream consumer claim', needed: '稳定业务键 + SandboxExecution 原子 claim（A）', duplicate: '随机 Intent key（D）；存在性预查仅是 B' },
  { invariant: 'Tool result 只处理一次', current: 'Event unique；reserve；Patch unique；补偿扫描', needed: 'resultId 对应唯一消费记录（A）', duplicate: 'Patch source unique（C）；reserve / complete 拆分当前有 bug' },
  { invariant: 'Enrichment 单 worker', current: 'PROCESSING；悲观锁；@Version；stale scheduler', needed: '只保护重复 LLM 成本（B）', duplicate: '三套机制是 C——没有对应不可逆副作用' },
  { invariant: 'Code analysis 终态不可翻转', current: 'Java isTerminal()', needed: '一个原子 DB owner（当前缺失）', duplicate: '必须补一个原子机制' },
  { invariant: '每场 CodeTrace 最多 3 次', current: 'countBySessionId 后执行外部调用，再插记录', needed: '原子 reservation / slot（当前缺失）', duplicate: 'queryHash / createdAt 不提供配额 correctness' },
];
