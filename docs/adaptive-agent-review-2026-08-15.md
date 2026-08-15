# 自适应面试 Agent 代码评估报告

> 评估日期：2026-08-15
> 评估对象：`modules/interview/agent/adaptive` 及其关联的 planning / assessment / memory / tool / algorithm / codeanalysis / mcp / persistence 模块
> 评估方式：逐层阅读代码、Flyway 迁移、Prompt 模板、前端页面与测试，不依赖设计文档结论
> 验证结果：`./gradlew :app:test --no-daemon` 通过（458 个测试，129 个测试类）；`cd frontend && pnpm run build` 通过

---

## 目录

1. [总体结论](#1-总体结论)
2. [业务评估：企业侧与候选人侧](#2-业务评估企业侧与候选人侧)
3. [架构设计](#3-架构设计)
4. [Agent 范式](#4-agent-范式)
5. [工具设计](#5-工具设计)
6. [上下文工程](#6-上下文工程)
7. [短期记忆](#7-短期记忆)
8. [长期记忆](#8-长期记忆)
9. [多 Agent 设计](#9-多-agent-设计)
10. [文档与代码差异](#10-文档与代码差异)
11. [优先改进清单](#11-优先改进清单)
12. [评分表](#12-评分表)
13. [附录：关键代码位置](#13-附录关键代码位置)

---

## 1. 总体结论

这是一个**“可信评估内核做得相当扎实、但离可售卖的企业级面试产品还差一段商业闭环”的工程原型**。

对候选人自测 / 模拟面试，闭环已经基本成立；对企业筛选，MCP、租户治理和证据链是亮点，但租户答题链路、能力模型配置、ATS、运营配套仍不完整。

最值钱的不是“自适应”三个字，而是三条在代码中真实存在并有测试守护的原则：

1. **证据可追溯**：报告只引用持久化的 `agent_assessments` / `agent_evidences`，不经过 LLM 二次生成。
2. **模型建议、代码裁决**：会话状态、轮次预算、动作集合、来源引用都由确定性 Java 代码强制校验。
3. **公平性防火墙**：长期记忆可以影响选题，但不会进入当前面试的评级链路。

---

## 2. 业务评估：企业侧与候选人侧

### 2.1 候选人侧：模拟面试 / 自我评估

代码中已经存在较完整的主链路：

`AdaptiveInterviewApplicationService.create()`
→ `PlanningAgent` 生成维度计划
→ `BoundedReActRuntime` 生成首题
→ 候选人回答
→ `DepthAssessmentAgent` 评级
→ `AssessmentEvidenceValidator` 校验原文引用
→ 维度小结 / 声明抽取
→ 面试结束后 `AssessmentReportService` 确定性组装报告、薄弱点和练习推荐。

**优点**

- 报告由持久化事实组装，每条结论可下钻到原问题、原回答和原文引用，不是“AI 自由发挥”。
- 薄弱点与练习推荐是确定性代码逻辑，且会排除本场已经使用的题目。
- 算法判题和项目代码核验提供了文本问答之外的客观证据。
- 报告明确输出 `L0–L4` 深度等级，不输出百分制总分。

**不足**

- 前端路由 `/adaptive-interview` 存在，但没有从面试中心等入口挂载，处于半隐藏状态。
- JD、简历通过 textarea 粘贴，未与已有简历模块 / JD 解析打通。
- 练习推荐只有 `PENDING`，没有“完成练习 → 复测”的闭环。
- 没有候选人身份体系和登录；`candidateId` 由客户端传入，`GET /candidates/{candidateId}/ability-profile` 知道 ID 即可查看轨迹，隐私保护不成立。
- 前端请求超时为 45s（`frontend/src/api/adaptiveInterview.ts` 的 `MODEL_CALL_TIMEOUT_MS = 45_000`），但后端在维度完成轮次可能串行执行：
  - 评估 deadline：20s
  - 维度小结 deadline：20s
  - 声明抽取 deadline：20s
  - ReAct 决策 deadline：30s
  - 理论上限 90s

  候选人可能先收到前端超时，后端随后才推进状态；重试时又会得到“轮次不一致”，体验和一致性都有风险。

### 2.2 企业侧：筛选 / 评估

企业侧最值得肯定的设计是“可追溯证据 + 不输出总分排名”：

- `EnterpriseAssessmentReport` 带固定免责声明：`AI 初筛建议，不构成录用决定`。
- 企业视图只按维度输出深度等级和证据，不做综合总分排名。
- MCP Server 已实现租户凭证、scope、审计和跨租户返回“不存在”的语义。

**关键阻塞：租户会话无法答题**

企业 MCP 创建会话走 `createForTenant()`：

```java
// AdaptiveInterviewApplicationService
public PlannedInterview createForTenant(...) {
    return persistenceService.createForTenant(...);
}
```

但候选人答题入口只接受 `tenant_id IS NULL` 的会话：

```java
// AdaptiveInterviewApplicationService
public PlannedInterview submitAnswer(String sessionId, CandidateAnswer answer) {
    PlannedInterview interview = persistenceService.get(sessionId);
    ...
}

// AdaptiveInterviewPersistenceService
Optional<AdaptiveAgentSessionEntity> findByIdAndTenantIdIsNull(String id);
```

`recordDecision()`、候选人 `get()`、候选人报告路径均使用 `findByIdAndTenantIdIsNull`。MCP 暴露的四个工具只有 `interview.create / get_status / list_dimensions / get_report`，没有 `submit_answer`。

因此当前企业通过 MCP 创建的面试只能停留在第一题，候选人无法提交回答，企业也永远拿不到最终报告。**这是端到端流程缺口，不是体验瑕疵。**

其他企业侧缺口：

- 没有 `tenants`、`capability_models`、`rubrics` 表；企业无法配置能力模型和维度权重。
- 没有 ATS Client、候选人名单、JD 拉取、结果回写、HR 确认动作。
- 没有企业 Web 控制台，企业侧只有 MCP。
- `docker-compose.yml` 不包含 `sandboxd` 和代码分析 Worker；算法判题和项目代码分析实际无法随平台部署。
- 算法题没有生产录入入口：`saveProblem()` 仅存在于 `AlgorithmPersistenceService`，没有 Controller / 管理端调用，也没有 seed 数据。

### 2.3 业务定位判断

当前更适合的定位是：

1. **候选人端自适应模拟面试工具**：闭环基本成立，优先补登录、入口、练习闭环和延迟体验。
2. **企业评估能力的 API / MCP 内核**：证据链是护城河，但还不是可交付的企业产品。

真正向企业销售还需要：租户管理、候选人触达、ATS 对接、人工复核、合规删除、SLA / 配额与部署配套。

---

## 3. 架构设计

### 3.1 优点

- **包边界清楚**：`core` 是纯领域模型，不依赖 Spring AI / JPA / Redis / S3；`runtime` 不碰 Repository；`persistence` 是唯一写入者。
- **新老实现物理隔离**：`AdaptivePackageIsolationTest` 禁止新 `adaptive` 包 import 旧 MVP 包。
- **状态机确定性**：`AdaptiveInterviewSession.apply()` 会在最后一轮把模型的 `ASK` 强制替换为 `FINISH`；`InterviewPlan.decide()` 校验维度数量、重复、工具 ID 和 Skill ID。
- **事务纪律好**：LLM 调用在事务外，评估 / 小结 / 声明 / 决策完成后通过一次 `recordDecision()` 短事务落库。
- **并发安全**：`@Version` 乐观锁 + 轮次断言；`AdaptiveInterviewConcurrencyIntegrationTest` 验证两个并发回答只有一个能推进会话。
- **失败不推进状态**：评估失败、小结失败、声明抽取失败、决策失败都会在落库前终止，测试覆盖完整。
- **历史可回填**：`agent_turns` 从 M0 起就是结构化行，`AssessmentBackfillService` 可为历史会话补评估。

### 3.2 问题

- **基础设施反向依赖业务模块**：`infrastructure/codeanalysis/S3ZipCodeAnchorCatalog` 和 `S3ZipCodeTraceSource` import 了 `modules.interview.agent.adaptive.codeanalysis`，违反“基础设施在业务之下”的分层。
- **服务类开始膨胀**：`AdaptiveInterviewApplicationService` 约 500 行，`AdaptiveInterviewPersistenceService` 约 540 行；后者同时实现 assessment / algorithm / memory 的多个存储端口。
- **单体模块内耦合在上升**：当前拆 Gradle 子模块没有独立部署收益，但包内职责边界需要继续收紧。
- **文档与代码存在漂移**：详见第 10 节。

---

## 4. Agent 范式

### 4.1 做得好的

`BoundedReActRuntime` 是规范的有限 ReAct 内核：

- 单轮 `maxSteps=4`、`maxToolCalls=2`、总 deadline 30s。
- 单步只允许一个工具调用。
- 相同工具 + 相同参数的重复调用会被拒绝，并作为 observation 返回给模型。
- `DeadlineExecutor` 使用虚拟线程 + 共享 deadline 约束每个模型 / 工具步骤。
- `AgentAction` 是 sealed interface，动作集合封闭，只允许 `RespondAction` / `ToolCallAction`。

单轮实际调用链：

```text
评估 Agent（1 次结构化输出）
→ 维度完成时：维度小结 Agent + 声明抽取 Agent
→ 面试官 ReAct（1–4 次模型调用，最多 2 次工具调用）
```

角色之间没有自由对话，全部由应用编排器串接。

裁决点非常硬：

- 题库题目来源必须精确匹配工具返回的 `stableId + difficulty + 原题文本`。
- 代码题来源必须匹配项目分析产物中的 `sourceId + anchor + usage`。
- 证据 quote 必须是候选人回答原文子串。
- 面试官永远不允许提前 `FINISH`，结束由代码在最后一轮合成。

### 4.2 问题

- **“自适应”目前主要发生在规划和单轮追问，没有发生在评估驱动的动态预算**：
  - `InterviewPlan.decide()` 使用 `maxTurns = min(维度数 × 2, 12)` 一次性分配。
  - `AssessmentDecision.recommendSwitchQuestion()` 被计算并持久化，但全代码库没有任何消费方。
  - L0 不会追加验证轮，L4 不会提前完成维度，弱维度不会获得额外预算。

- **每轮深度评级不进入下一轮决策**：面试官只看原始回答和本维度历史，看不到评估结论。这保护了评估独立性，是正确取舍；但系统目前没有基于评级的路径调整，因此只能算“规划自适应 + 追问自适应”，不是完整自适应面试。

- **辅助记忆任务在关键路径上**：维度完成时，小结或声明抽取失败会直接导致候选人回答提交失败、状态不推进。候选人重试时需要重跑全部 LLM 调用。这些任务应当允许降级或异步。

- **工具错误不是 observation**：模型传错参数时，`ToolGateway` 直接抛异常终止整个请求，不会像标准 ReAct 那样把错误反馈给模型进行修复。唯一可自我纠正的场景是重复工具调用。

- **角色抽象不一致**：interviewer 走 ReAct + `AgentRoleRegistry`；planner / assessor / brief / claim 是四套独立的单次结构化调用，各自维护 deadline 和 prompt path，没有统一进入 registry。

---

## 5. 工具设计

### 5.1 优点

- **执行与声明分离**：每个工具注册 `FunctionToolCallback`，但 callback 直接调用会抛 `unsupportedDirectCall`，所有执行必须经过 `ToolGateway`，防止框架绕过编排器直接执行。
- **白名单 + 可审计**：角色只允许 `load_skill / question_bank_search / rubric_lookup / sandbox_submit`；每次调用记录在 `agent_tool_calls`，包含调用方、输入键摘要、输出摘要、resultId 和耗时。
- **稳定 ID 与幂等**：工具结果使用 `question-search:...`、`skill:...:sha256` 等稳定 ID；调用 ID 由 session + turn + tool + 规范化参数的 SHA-256 生成。
- **延迟执行设计正确**：`sandbox_submit` 返回 `PendingToolResult`，判题完成后通过 `ToolResultEvent` 唤醒新的 ReAct，而不是给模型轮询工具。
  - 过期结果 `supersededBy` 不唤醒。
  - 队列超时降级为代码走读。
  - `IE` / `TIMEOUT_QUEUED` 不作为候选人负面证据。
- **隐藏用例不进入模型上下文**：模型只看到 verdict、通过数、耗时、首个失败用例编号。
- **题库治理真实存在**：pgvector 检索 ACTIVE 审核题，来源必须原样引用；远端 MCP 题库故障会 fallback 到本地题库。

### 5.2 问题

- **算法题没有运营闭环**：题目表存在，但没有管理 API、导入 seed 或题目创建页面；前端要求候选人手动输入 `problemId`。
- **样例运行路径存在边界缺陷**：前端允许在“尚未提交回答”时直接运行 SAMPLE。结果到达后 `AdaptiveAlgorithmResultReadyHandler` 无条件调用 `reassessAlgorithmResult()`，但此时 `agent_turns.answer` 可能仍为 null，评估上下文会拿到空回答，导致工具结果事件处理失败。
- **代码分析产物缺少结构化数量 / 大小上限**：只校验 commitHash、token cost 和锚点存在；claim / scenario 数量、单条文本长度、payload 总大小没有硬限制，最终只能靠 12k 输入 token 预算把面试打挂。
- **Zip 校验存在 zip bomb 风险**：`S3ZipCodeAnchorCatalog.findMissing()` 对每个 entry 直接 `readAllBytes()`，只限制压缩包总大小和文件数，没有单文件解压大小限制。
- 沙箱和代码分析 Worker 都不在 `docker-compose.yml` 中，属于“代码有、交付没有”。

---

## 6. 上下文工程

### 6.1 优点

- **统一上下文装配**：所有 LLM 输入通过 `ContextAssembler` 按角色裁剪：
  - planner：JD + 简历 + coveredTopics + unverifiedClaims + skill 目录。
  - interviewer：当前维度轮次 + 已完成维度小结 + 当前回答 / 工具结果 / 代码提交 + 项目上下文。
  - assessor：当前 question + answer + 固定量规。
  - 小结 / 声明抽取：本维度轮次。
- **公平隔离由代码保证**：长期能力画像不进入 interviewer / assessor 上下文，只有 `coveredTopics / unverifiedClaims` 进入 planner。`CandidateMemoryFairnessContractTest` 和 `PlanningContractTest` 将这一规则做成了契约测试。
- **Prompt 有 data-boundary + schema 校验**：不可信内容包裹在 `<data-boundary>` 中，结构化输出经过 schema validation、repair 和有限重试。
- **输入 token 有预算和指标**：`AdaptiveInputTokenBudget` 估算输入 token，超限直接拒绝，不静默截断。

### 6.2 问题

- **超限策略是 fail-fast，不是压缩 / 检索**：设计文档中的“滚动压缩”只实现了维度小结；当前维度原文、完整 JD 和简历每轮重复进入上下文。超出 12k token 直接报错，长 JD、长简历、大项目上下文会卡死面试。
- **JD 和简历每轮重复注入**，没有会话级摘要或分块检索，成本和噪声随轮次线性放大。
- **项目上下文整包注入**：`ProjectInterviewContext` 将仓库全部 claims / scenarios / digest 塞进 interviewer 上下文，没有按维度裁剪或 top-k。
- **自适应路径未使用 `PromptSanitizer`**，也没有随机化 data-boundary 标签。虽然 schema 校验和来源校验能兜住大部分注入，但边界防护弱于语音 / 知识库链路。
- **量规存在两份来源**：`DepthLevel` 枚举与 assessment prompt 内置文本分别定义 L0–L4，没有共享 include，存在口径漂移风险。

---

## 7. 短期记忆

短期记忆的工程实现优于设计文档的描述：

- `agent_turns` 一行一轮，问题和回答原文完整保存，不用摘要替代原文。
- 维度完成后生成 `dimension_briefs`，`DimensionBriefService` 会校验所有 `turnIndex` 真实存在。
- 小结只用于上下文导航，报告永远读原始轮次。
- 工具调用和延迟工具结果有独立表：`agent_tool_calls` / `agent_tool_result_events`。
- 并发 / 重复提交由 `@Version` 乐观锁和轮次断言保护。

**不足**

- 文档提到的 Redis 热缓存没有落地；adaptive 路径每次直接读 DB，Redis 只用于判题 Stream。当前简单可靠，但高并发下 `get()` 会频繁重放整场历史。
- 没有会话 TTL / 过期回收。
- 当前每维度通常只有 2 轮，所以“全量进上下文”尚未爆炸；一旦实现动态预算，压缩策略必须同步跟进，否则 token 预算会先崩。

---

## 8. 长期记忆

长期记忆是本次评估中执行纪律最好的部分。

**优点**

- 只写结构化字段：
  - `candidate_memory_topics(skillId, focusId)`
  - `candidate_memory_claims(type, skillId, focusId, UNVERIFIED)`
  - `candidate_ability_profiles(dimension, depthLevel, sourceSessionId, superseded_by)`
- 声明永远为 `UNVERIFIED`，数据库 CHECK 约束限制枚举值。
- 记忆写入与面试落库在同一事务；能力画像只在会话完成时刷新。
- 公平性防火墙是物理存在，不是 Prompt 祈祷：评估 Agent 的输入 record 中没有历史评级字段，并有契约测试守护。
- 画像采用“最新为准 + superseded 轨迹”，不是覆盖删除。

**不足**

- 能力画像目前只读给候选人轨迹页面，没有反哺 planning 做预算调整或维度降权；长期记忆实际只实现了“避重出题”和“声明核验选题”。
- 画像维度 key 是 planner 每次生成的自由文本 `dimension`，例如本次叫“Redis 缓存”、下次叫“缓存一致性”，跨会话无法稳定匹配，成长轨迹和复测价值会打折。
- 没有删除 / 遗忘接口；设计文档承诺的 GDPR / 个保法删除未在代码中实现。
- 练习闭环只有 `PENDING`，没有完成状态流转。
- 没有 answer embeddings / 相似问答离线分析，也没有企业侧量规校准数据表；L2 长期记忆只实现了一半。

---

## 9. 多 Agent 设计

当前实际角色清单：

| 角色 | 形态 | 职责 |
|---|---|---|
| Planner | 单次结构化输出 | JD + 简历 → 维度计划 |
| Interviewer | ReAct 主体 | 单维度出题，可调 4 个工具 |
| Assessor | 单次结构化输出 | L0–L4 评级 + quote 证据 |
| DimensionBriefGenerator | 单次结构化输出 | 维度小结 |
| CandidateClaimGenerator | 单次结构化输出 | 候选人声明抽取 |
| 报告 | 无 LLM | 确定性组装 |

这个结构整体正确：

- 星型编排，没有 Agent 间自由对话。
- 提问与评估分离，上下文互相隔离。
- 刻意省略“报告措辞 Agent”，避免弱化负面结论。
- 判题和代码分析是外部服务，没有在面试进程内再开 Agent loop。

**主要不足**

- **评估结果不参与编排**：Assessor 是“只记录、不控制”，动态换维、提前结束、预算转移都没有发生；多 Agent 的“评估质量反馈到面试过程”这一核心价值尚未打通。
- 规划、评估、记忆辅助 Agent 的抽象不统一。
- 辅助 Agent 的失败会阻塞主流程。
- 企业侧 Agent（ATS、JD 拉取、HR 复核）完全不存在，多 Agent 目前只是“面试内的角色拆分”。

---

## 10. 文档与代码差异

以下差异可能误导后续开发，需要校正：

| 文档说法 | 代码实际 |
|---|---|
| 动态轮次预算、按证据重新分配 | `InterviewPlan.decide()` 静态分配，`recommendSwitchQuestion` 无消费方 |
| 短期记忆用 Redis 热缓存 | adaptive 会话路径无 Redis 缓存，只读 DB |
| 代码分析为平台作为 MCP Client 调 Pi SDK | 实际是内部 Worker HTTP API + 平台作为 MCP Server 暴露 `code.*` 工具；MCP Client 仅用于远端题库 |
| 评估体系后置 M5，算法沙箱 Phase 2 推迟 | 代码已实现 assessment、算法判题、部分代码分析 |
| 企业可配置能力模型、权重、量规 | 没有 `tenants` / `capability_models` / `rubrics` 表 |
| L4 可提前完成维度 | 代码禁止提前结束，最后一轮由状态机合成 FINISH |

---

## 11. 优先改进清单

按优先级排序：

1. **打通企业租户会话的答题链路**：`submitAnswer / recordDecision / get / report` 增加 tenant-aware 版本，或为租户会话签发匿名答题 token。否则企业 MCP 无法完成一场面试。
2. **实现真正的动态轮次裁决**：让 `recommendSwitchQuestion` / depth / confidence 进入 `InterviewPlan` 的再分配规则，同时保留硬上限。
3. **全局请求 deadline + 前端异步 / SSE / 轮询**：解决 45s 前端超时与 90s 后端串行上限不匹配的问题。
4. **将维度小结 / 声明抽取移出关键路径**：失败时降级为“本次不写长期记忆”，不阻塞面试推进。
5. **候选人 / 企业身份与权限**：`candidateId` 不能裸奔；能力画像、报告、会话都需要鉴权。
6. **补运营配套**：算法题导入 / 管理、sandboxd 部署、代码分析 Worker 交付、租户能力模型配置。
7. **合规闭环**：数据删除、保留期、候选人授权、审计查询。
8. **上下文压缩**：JD / 简历 / 项目上下文的分块检索或摘要，超限降级而不是报错。
9. **修复工具与算法边界缺陷**：SAMPLE 空回答结果处理、zip 解压大小上限、代码分析产物大小上限。
10. **校准设计文档与代码的差异**，避免后续实现基于过期蓝图。

---

## 12. 评分表

| 维度 | 评分 | 说明 |
|---|---:|---|
| 候选人业务闭环 | 6.5 / 10 | 能跑且报告可信，入口、体验、练习闭环不足 |
| 企业业务闭环 | 3 / 10 | 证据链好，但租户答题链路断，无 ATS / 控制台 / 配置 |
| 架构设计 | 8 / 10 | 边界和事务纪律好，少量分层泄漏和类膨胀 |
| Agent 范式 | 7.5 / 10 | 有界 ReAct + 代码裁决扎实，动态自适应未完成 |
| 工具设计 | 8 / 10 | 白名单 / 审计 / Pending / 防泄题漂亮，缺运营与部署配套 |
| 上下文工程 | 7 / 10 | 隔离和预算好，缺压缩 / 降级和项目上下文裁剪 |
| 短期记忆 | 8 / 10 | 结构化事实链完整，缺缓存 / TTL |
| 长期记忆 | 7 / 10 | 公平性防火墙优秀，但只写不用于规划、无删除 |
| 多 Agent | 7 / 10 | 星型拆分正确，评估未反哺编排 |

---

## 13. 附录：关键代码位置

**核心编排**

- `app/src/main/java/interview/guide/modules/interview/agent/adaptive/application/AdaptiveInterviewApplicationService.java`
- `app/src/main/java/interview/guide/modules/interview/agent/adaptive/planning/InterviewPlan.java`
- `app/src/main/java/interview/guide/modules/interview/agent/adaptive/persistence/AdaptiveInterviewPersistenceService.java`

**ReAct 运行时**

- `.../adaptive/runtime/BoundedReActRuntime.java`
- `.../adaptive/runtime/ReActBudget.java`
- `.../adaptive/runtime/DeadlineExecutor.java`
- `.../adaptive/role/AgentRoleRegistry.java`
- `.../adaptive/role/SpringAiAdaptiveAgentModelGateway.java`

**评估与证据**

- `.../adaptive/assessment/DepthAssessmentAgent.java`
- `.../adaptive/assessment/AssessmentEvidenceValidator.java`
- `.../adaptive/assessment/AssessmentReportService.java`
- `.../adaptive/persistence/JpaAssessmentReportFactsSource.java`

**记忆与上下文**

- `.../adaptive/memory/ContextAssembler.java`
- `.../adaptive/memory/CandidateMemoryService.java`
- `.../adaptive/memory/DimensionBriefService.java`
- `.../adaptive/memory/CandidateClaimExtractionService.java`
- `.../adaptive/memory/CandidateAbilityProfileService.java`

**工具与算法**

- `.../adaptive/tool/ToolGateway.java`
- `.../adaptive/tool/QuestionBankSearchTool.java`
- `.../adaptive/tool/RubricLookupTool.java`
- `.../adaptive/tool/LoadSkillTool.java`
- `.../adaptive/tool/SandboxSubmitTool.java`
- `.../adaptive/algorithm/AlgorithmSubmissionService.java`
- `.../adaptive/algorithm/AlgorithmJudgeStreamConsumer.java`
- `.../adaptive/application/AdaptiveAlgorithmResultReadyHandler.java`

**MCP 与代码分析**

- `.../adaptive/mcp/AdaptiveInterviewMcpTools.java`
- `.../adaptive/mcp/CodeAnalysisMcpTools.java`
- `.../adaptive/mcp/McpTenantCredentialResolver.java`
- `.../adaptive/mcp/McpQuestionBankGateway.java`
- `.../adaptive/codeanalysis/CodeAnalysisResultAcceptanceService.java`
- `.../infrastructure/codeanalysis/S3ZipCodeAnchorCatalog.java`

**关键测试**

- `CandidateMemoryFairnessContractTest`：长期记忆与当前评级隔离
- `PlanningContractTest`：规划上下文不携带评估结论
- `AdaptiveInterviewFlowIntegrationTest`：完整事实链与维度覆盖
- `AdaptiveInterviewConcurrencyIntegrationTest`：并发提交只推进一次
- `AssessmentFoundationContractTest`：L0–L4 量规与证据 schema
- `AdaptivePackageIsolationTest`：新旧 Agent 包隔离
