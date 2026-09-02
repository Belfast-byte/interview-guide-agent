import type { RootCause } from '../auditTypes';

export const ROOT_CAUSES: readonly RootCause[] = [
  {
    id: 'durable-workflow',
    index: 'RC-01',
    title: '把可重算步骤当成 durable workflow',
    thesis:
      '恢复目标被定义为“恢复每一个 LLM / Tool 中间步骤”，而不是“从最近可靠领域事实重跑”。ASK 草稿、只读查询、WorkState Patch 都被持久化出执行状态机。',
    severity: 'P0',
    decision: 'DELETE',
    kind: 'accidental',
    modules: [
      'ActionIntent',
      'PersistentActionCoordinator',
      'WorkState Patch',
      'ToolExecution',
      'ToolResultEvent',
    ],
    secondaryComplexity: [
      'PLANNED / EXECUTING / SUCCEEDED / APPLIED 五态 Intent',
      'ACTION_PENDING、activeActionIntentId、revision 指针',
      'retry endpoint 与 recovery scheduler',
      'FAILED Intent 卡死且对用户不可发现',
    ],
    impacts: [
      '标准会话响应不返回 intentId，客户端没有自然恢复路径',
      'resume 只校验 turnIndex，却用新请求的 answer 重建旧上下文',
      'reserve 后崩溃的事件永久停在 RECEIVED，补偿扫描不再重投',
      'ASK 与只读查询承担与沙箱同等的持久恢复成本',
    ],
    recommendation:
      '删除 ASK / read-only / sandbox 的 generic Intent：Turn 是 ASK 事实，SandboxExecution 是副作用事实，其余步骤从最近领域事实重算。',
    evidenceIds: ['E-01', 'E-02', 'E-03', 'E-04', 'E-05'],
  },
  {
    id: 'correctness-owner',
    index: 'RC-02',
    title: '同一 correctness 没有唯一 owner',
    thesis:
      '多层校验和状态复制代替了明确的数据库原子边界；复杂之处仍有竞态，简单之处层层重复。错误类型取决于 flush 顺序，而不是取决于设计。',
    severity: 'P0',
    decision: 'REDESIGN',
    kind: 'mixed',
    modules: [
      'Session / Turn',
      'WorkState',
      'ToolResultEvent',
      'SemanticState',
      'AnalysisJob',
      'CodeTrace 配额',
    ],
    secondaryComplexity: [
      '逻辑 revision + JPA @Version + 业务 unique 叠加',
      'exists 预检 + status guard + 多个 scheduler',
      '嵌套重试最坏 2×2 次模型调用',
    ],
    impacts: [
      '同一回答并发提交会先烧掉两次 Assessment LLM',
      'Semantic 聚合在加锁前读取 contributions，并发丢更新',
      'AnalysisJob 完成与超时 last-write-wins，产物与状态可不一致',
      '沙箱幂等键是随机 UUID，retry 可产生第二个外部执行',
    ],
    recommendation:
      '每个 invariant 只保留一个原子 owner：Turn 条件 claim、Session 单一推进版本、业务 unique、沙箱稳定业务键、AnalysisJob 三选一（行锁 / @Version / 条件更新）。',
    evidenceIds: ['E-06', 'E-07', 'E-08', 'E-09', 'E-10', 'E-11'],
  },
  {
    id: 'pseudo-tools',
    index: 'RC-03',
    title: 'Tool API 包装了普通业务编排',
    thesis:
      '只要能力被模型“看见”就被称为 Tool，即使是否调用和参数均已由 Java 决定。所谓 ReAct Runtime 只有一次模型调用，初始 observations 固定为空。',
    severity: 'P1',
    decision: 'DELETE',
    kind: 'accidental',
    modules: [
      'load_skill',
      'rubric_lookup',
      'sandbox_submit',
      'question_bank_search（已下线）',
      'BoundedActionRuntime',
      'AgentRoleRegistry',
    ],
    secondaryComplexity: [
      'ReActRequest / Result / ToolObservation 概念层',
      'TOOL_FACT objective 与 toolBudget 死分支',
      '所有 Tool 调用无论只读与否一律落 agent_tool_calls',
    ],
    impacts: [
      'active Tool 3/3 都不需要模型动态决策',
      '成功的 Tool → Observation → 再推理闭环为 0',
      '唯一语义成立的动态 Tool（题库检索）反而被下线',
      '多 ToolCall 被静默截断，只保留第一个',
    ],
    recommendation:
      '固定能力由 ContextAssembler / Application 直接调用；只有真实动态只读检索上线时才引入最小内存 Tool loop。',
    evidenceIds: ['E-12', 'E-13', 'E-14', 'E-15', 'E-16'],
  },
  {
    id: 'dual-policy',
    index: 'RC-04',
    title: 'Java 策略与 Prompt 双重控制',
    thesis:
      'Java 不只维护不变量，还提前算完语义策略；Prompt 又描述一套相同甚至冲突的规则。模型只剩“根据 Java 参数写一句问题”。',
    severity: 'P1',
    decision: 'REDESIGN',
    kind: 'accidental',
    modules: [
      'NextActionPolicy',
      'AssessmentWorkStatePlanner',
      'Interviewer system prompt',
      'Planning prompt',
    ],
    secondaryComplexity: [
      'Gap 顺序、Target 切换、结束条件全部硬编码',
      'Prompt contract 与 ASK Intent 类型约束冲突',
      '同一套 Tool / budget 规则在代码与 Prompt 中双重维护',
    ],
    impacts: [
      '控制面自主性接近 0，退化为模板生成器',
      '模型遵循 Prompt 调用 Tool 反而导致整轮失败',
      '代码与 Prompt 规则漂移，维护成本翻倍',
    ],
    recommendation:
      '模型提案 gap / continue / switch / 只读检索；Java 只裁决合法 Target、预算、来源、权限和硬上限。',
    evidenceIds: ['E-17', 'E-18', 'E-19'],
  },
  {
    id: 'derived-platform',
    index: 'RC-05',
    title: '派生状态被升级为通用 Agent 平台',
    thesis:
      '为 Memory、Role、Recovery、Provider 等术语建立了通用框架，但当前消费者与实现数量不足以证明平台价值；派生状态比权威事实更难保持正确。',
    severity: 'P1',
    decision: 'SIMPLIFY',
    kind: 'mixed',
    modules: [
      'Persistent WorkState',
      'Episode enrichment',
      'Semantic contribution / state',
      '参数袋与固定 Registry',
    ],
    secondaryComplexity: [
      'snapshot + patch + 投影列三重表示',
      '四态 enrichment、claim、stale recovery、手工 retry',
      'Dependencies / Repositories 纯参数袋',
      '死 enum、死字段、死配置（PracticeStatus、HINT、TOOL_RESULT…）',
    ],
    impacts: [
      'enrichment LLM 实际拿不到权威上下文（模板字面量 <contextJson>）',
      '内部 WorkState 投影泄漏为前端 API 契约',
      '已下线的题库 Tool 仍运行索引与 MCP fallback',
      '933 行 ApplicationService + 566 行 PersistenceService 难以追踪',
    ],
    recommendation:
      'WorkState 改为请求内 DecisionContext / Coverage；保留 Episode/tags 真实闭环；SemanticState 按读聚合或明确为可重建 cache；删除死状态与参数袋。',
    evidenceIds: ['E-20', 'E-21', 'E-22', 'E-23', 'E-24'],
  },
];
