# 13. 自适应面试 Agent 优化方案（候选人侧）

> 维护：Agent；上游设计决策以 `docs/design/` 为准。
>
> 来源：`adaptive-agent-review-2026-08-15.md`（外部评估报告，已经逐条对照代码核实）。
> 范围：**不含企业 MCP / 租户答题链路 / ATS / 企业控制台**。企业侧事项另行立项。
> 状态：待评审。

## 0. 前置校正

评估报告有两条与当前代码不符，落地前先从问题清单中移除：

1. ~~前端路由 `/adaptive-interview` 未挂载入口~~ —— `Layout.tsx` 主导航「面试准备」分组已有「自适应面试」入口，无需处理。
2. 「练习推荐只有 PENDING」表述修正 —— `PracticeStatus` 枚举含 `PENDING / COMPLETED`，但无任何代码流转到 COMPLETED。问题改为「练习状态机无流转」，见 §4.1。

另有一条报告未点名但核实中发现的新问题，纳入 P0：`DepthLevel.L4` 内置文案「当前维度可提前完成」与状态机「禁止提前结束」自相矛盾，见 §1.5。

## 1. P0 正确性与稳定性

目标：修掉「候选人能遇到」的断链、超时和数据风险。全部完成后主链路在慢路径下不超时、不卡死、不丢状态。

### 1.1 请求链路超时不匹配（45s vs 90s）

**问题**：前端 `MODEL_CALL_TIMEOUT_MS = 45_000`，后端维度完成轮串行上限 90s（评估 20s + 小结 20s + 声明 20s + ReAct 30s）。候选人先收到前端超时，后端随后推进状态，重试得到「轮次不一致」。

**方案（两步，都进本里程碑）**：

a. 后端引入全局请求预算：
- `AdaptiveAgentProperties` 增加 `requestBudget`（默认 40s）。
- `submitAnswer` 入口处计算 `deadlineNanos`，评估与 ReAct 共享剩余预算；§1.2 完成后串行上限降为 50s（评估 20s + ReAct 30s），预算内快速失败而非悬挂。
- 超预算抛 `BusinessException(ErrorCode.AI_SERVICE_ERROR, ...)`，状态不推进（现有纪律已保证）。

b. 前端提交改异步：
- `submitAnswer` 改为「提交即返回 + 轮询推进」：POST 返回当前 `turnIndex/status`，前端轮询 `GET /sessions/{sessionId}`（复用现有响应模型）直到 `currentTurn` 前进或状态失败。
- 移除 `adaptiveInterview.ts` 中 45s 同步等待语义；轮询总超时放宽到 120s，间隔 2s。
- 暂不上 SSE，轮询即可满足，改动面最小。

**涉及文件**：
- `adaptive/application/AdaptiveAgentProperties.java`
- `adaptive/application/AdaptiveInterviewApplicationService.java`
- `frontend/src/api/adaptiveInterview.ts`
- `frontend/src/pages/AdaptiveInterviewPage.tsx`（提交后状态机）

**验收**：
- 人为将评估 deadline 拉长至 90s 的集成测试场景下，前端不出现「先超时后推进」；
- 预算耗尽时返回业务错误且会话状态不变（复用现有「失败不推进」断言）。

### 1.2 维度小结 / 声明抽取移出关键路径

**问题**：小结或声明抽取失败会直接导致回答提交失败、状态不推进；重试需重跑全部 LLM 调用。

**方案（本期只做失败降级，不做异步化）**：
- `submitAnswer` 编排中，维度小结与声明抽取各自 `try/catch`：失败时记录 warn 日志 + telemetry 指标，本轮标记「跳过长期记忆写入」，**不阻塞** `recordDecision()`。
- 保持「记忆写入与面试落库同一事务」的纪律：降级粒度是「整块不写」，禁止部分写入。
- 小结缺失时，`ContextAssembler` 对 interviewer 上下文回退为「无小结」（现有裁剪逻辑已允许空集合）。
- 异步化（独立线程池执行小结/声明）留到 P2，与 §4.2 上下文压缩一起设计。

**验收**：小结 Agent 抛错 / 声明 Agent 抛错两个测试场景下，回答提交成功、会话推进、`agent_assessments` 正常落库、`dimension_briefs` / `candidate_memory_claims` 无对应行。

### 1.3 SAMPLE 空回答边界缺陷

**问题**：前端允许「尚未提交回答」时直接运行 SAMPLE；结果到达后 `AdaptiveAlgorithmResultReadyHandler` 无条件调用 `reassessAlgorithmResult()`，此时 `agent_turns.answer` 为 null，评估上下文与证据校验拿到空回答。

**方案**：
- `reassessAlgorithmResult()` 入口校验 `turn.answer() == null` 时直接跳过，工具结果事件正常 ACK（不当作失败）。
- 判题结果保留在 `agent_tool_result_events`，候选人正式提交回答后由正常评估链路消费，不单独触发 reassess。
- 明确语义：SAMPLE 运行结果不作为候选人负面证据（与 `IE` / `TIMEOUT_QUEUED` 现有语义一致）。

**涉及文件**：`adaptive/application/AdaptiveAlgorithmResultReadyHandler.java`、`adaptive/application/AdaptiveInterviewApplicationService.java#reassessAlgorithmResult`。

**验收**：新增测试「SAMPLE 结果先于回答到达 → 不触发评估、事件 ACK、回答提交后正常评估」。

### 1.4 Zip 解压与代码分析产物上限

**问题**：
- `S3ZipCodeAnchorCatalog.findMissing()` 对每个 entry `readAllBytes()`，只限压缩包总大小和文件数，存在 zip bomb 风险。
- `CodeAnalysisResultAcceptanceService` 只校验 commitHash / token cost / 锚点存在，claims / scenarios 数量、单条文本长度、payload 总大小无硬限制。

**方案**：
- `CodeAnalysisProperties` 增加 `maxSnapshotFileBytes`（单文件解压上限，默认 1MB）；逐 entry 流式计数读取，超限抛 `BusinessException(BAD_REQUEST, ...)`。
- `CodeAnalysisProperties` 增加 `maxClaims` / `maxScenarios` / `maxTextLength` / `maxPayloadBytes`；接收服务逐项校验，超限拒绝整个产物并返回明确错误。
- 上限值与 `AdaptiveInputTokenBudget` 的 12k 输入预算对齐，保证「通过校验的产物一定塞得进上下文」。

**验收**：zip bomb 样本、超大 claims 样本两个测试均被拒绝且错误信息可读。

### 1.5 DepthLevel 量规单一来源

**问题**：L0–L4 量规在 `DepthLevel` 枚举与 assessment prompt 中各有一份；且枚举 L4 文案「当前维度可提前完成」与状态机行为矛盾（§2 落地前，提前完成不会发生）。

**方案**：
- prompt 模板通过 StringTemplate 注入 `DepthLevel` 枚举渲染的量规文本，删除 prompt 内的手写副本。
- L4 的 `actionTendency` 改为与 §2 裁决规则一致的描述（在 §2 合并落地时同步更新）。

**验收**：`AssessmentFoundationContractTest` 增加断言「prompt 渲染结果包含枚举量规原文」。

## 2. P1 动态轮次裁决（核心能力）

**问题**：`recommendSwitchQuestion` 被计算并持久化但无消费方；`maxTurns = min(维度数 × 2, 12)` 一次性静态分配。当前只是「规划自适应 + 追问自适应」。

**裁决规则（模型只建议，代码裁决，全部确定性）**：

- 输入：最近一次 `AssessmentDecision`（`depthLevel` / `confidence` / `recommendSwitchQuestion`）。
- 预算池：`maxTurns = 12` 硬上限不变；初始分配 = 维度数 × 2；预留池 = 12 − 初始分配。
- 规则：
  1. `L0` → 当前维度追加 1 轮验证轮（从预留池扣减，池空则不追加）；
  2. `L4` 且 `recommendSwitchQuestion = true` → 当前维度提前完成，该维度剩余轮次归还预留池；
  3. 连续 2 轮 `confidence` 低于阈值（建议 0.5，配 `AdaptiveAgentProperties`）→ 当前维度追加 1 轮；
  4. 每个维度最少 1 轮、最多 4 轮；总轮次永远 ≤ 12。

**改动点**：
- `InterviewPlan` 增加 `replan(turnIndex, AssessmentDecision)`：返回新计划或拒绝理由（预算不足），纯函数、可单测。
- `AdaptiveInterviewSession` 状态机支持「维度提前完成」：FINISH 仍只能由代码合成，语义不变；提前完成只是把后续轮次重标维度。
- `submitAnswer` 编排在 `recordDecision()` 前调用 `replan`，计划变更与决策落库同一事务。
- `AdaptiveInterviewApplicationService` 的 LLM 调用保持在事务外（现有纪律不变）。

**公平性不变量（不许破坏）**：评估结论只进入「出多少题 / 换不换维度」的代码裁决，**不进入** interviewer / assessor 的上下文文本。`CandidateMemoryFairnessContractTest` / `PlanningContractTest` 继续通过，并新增「replan 后 interviewer 上下文不含评级结论」断言。

**验收**：
- 单测：四条规则 + 预算耗尽拒绝 + 维度轮次上下限；
- 集成测试：模拟 L0 → 追加轮、模拟 L4+换题 → 提前完成、整场轮次 ≤ 12；
- `DepthLevel.L4` 文案同步更新（§1.5）。

## 3. P1 候选人身份与权限

**问题**：`candidateId` 由客户端传入，`GET /candidates/{candidateId}/ability-profile`、会话查询、报告接口知道 ID 即可访问。全库无鉴权设施。

**方案（最小可行，不引入完整登录体系）**：
- 创建会话时签发 **session token**：`HMAC-SHA256(sessionId + candidateId, 服务端密钥)`，随创建响应返回，前端本地保存。
- 候选人侧所有端点（会话查询 / 提交回答 / 报告 / 能力画像 / 练习列表）校验 token；能力画像按 candidateId 的访问要求提供该候选人任一会话的有效 token。
- 密钥放 `.env`（`APP_ADAPTIVE_TOKEN_SECRET`），不进 Git。
- 全局异常处理器按现有纪律返回 `Result.error(...)`。

**验收**：无 token / 错 token / 跨 candidateId token 三种场景返回统一鉴权错误；正路径测试全绿。

## 4. P2 体验与闭环

### 4.1 练习闭环
- 练习完成 → `PracticeStatus` 流转 `PENDING → COMPLETED`（新增提交入口，复用算法判题或问答评估结果作为完成依据）。
- 复测：基于能力画像对薄弱维度降权重新生成计划（复用 `create()`，planner 上下文注入画像维度）。
- 这是长期记忆「反哺 planning」的第一个消费方，为后续预算调整铺路。

### 4.2 上下文压缩与降级
- JD / 简历：创建会话时生成一次会话级摘要，后续各轮注入摘要替代原文；原文仍持久化可查。
- 项目上下文：`ProjectInterviewContext` 按当前维度对 claims / scenarios 做 top-k 裁剪，不整包注入。
- 超限策略：从 fail-fast 改为「摘要降级 → 再超限才报错」两级。
- 与 §1.2 的异步化在此一并设计（小结/声明异步生成，写入完成前上下文回退无小结）。

### 4.3 输入打通
- 创建会话支持 `resumeId` 引用已有简历模块解析结果，替代 textarea 粘贴；JD 同理对接已有 JD 解析（如已有模块可用，否则保持 textarea 并标注后续项）。

### 4.4 可选：会话热缓存与 TTL
- adaptive 会话读路径加 Redis 热缓存 + 会话 TTL / 过期回收。仅在压测证明 `get()` 重放整场历史成为瓶颈后实施。

## 5. P2 运营配套（候选人侧）

- **算法题录入**：新增管理端 `POST /admin/algorithm/problems`（复用 `saveProblem()`）+ 首批 seed 数据（Flyway 迁移或导入脚本）；前端算法维度改「题目选择」替代手输 `problemId`。
- **部署配套**：`docker-compose.yml` 增加 `sandboxd` 与代码分析 Worker 服务定义，使算法判题与项目代码分析可随平台部署；管理端接口按 §3 的 token 机制隔离。

## 6. P3 合规闭环

- 候选人数据删除接口：级联删除会话 / 轮次 / 工具调用 / 评估 / 记忆 / 画像 / 练习记录，返回删除回执。
- 数据保留期策略（配置化）与到期清理任务。
- 删除动作写审计日志。

## 7. 文档校准

落地评估报告第 10 节的 6 行差异修正（改文档对齐代码，或按本方案改代码后更新文档）：

| 文档说法 | 处置 |
|---|---|
| 动态轮次预算、按证据重新分配 | §2 落地后更新 `10-text-interview.md` 为已实现语义 |
| 短期记忆用 Redis 热缓存 | 改文档：当前只读 DB，缓存为 §4.4 可选增强 |
| 代码分析为平台作为 MCP Client 调 Pi SDK | 改 `12-code-analysis-service.md`：实际为内部 Worker HTTP API + 平台作 MCP Server 暴露 `code.*` |
| 评估体系后置 M5、算法沙箱 Phase 2 推迟 | 改文档：assessment / 算法判题已实现，更新阶段状态 |
| 企业可配置能力模型、权重、量规 | 改文档：标注为企业侧待立项，不在本方案范围 |
| L4 可提前完成维度 | §2 落地后语义成真，更新 `10-text-interview.md` 与 `DepthLevel` 文案 |

## 8. 里程碑与验证

| 里程碑 | 内容 | 成功标准 |
|---|---|---|
| M-A（P0） | §1.1–§1.5 | 慢路径不超时；小结/声明失败不阻塞；SAMPLE 空回答安全；zip/产物上限生效；`./gradlew :app:test --no-daemon` 全绿 + `cd frontend && pnpm run build` 通过 |
| M-B（P1） | §2 动态裁决 + §3 身份 | 四条裁决规则有集成测试；未授权访问全部拒绝；公平性契约测试不回归 |
| M-C（P2） | §4 闭环与压缩 + §5 运营 | 练习可完成可复测；长 JD/大项目上下文不再打挂面试；算法题可录入可选择；compose 一键起全链路 |
| M-D（P3） | §6 合规 + §7 文档 | 删除接口级联正确；文档与代码零已知差异 |

每个里程碑的通用纪律（遵循 AGENTS.md）：
- 业务失败一律 `BusinessException(ErrorCode.XXX, ...)`，禁止 `RuntimeException` 与静默 catch；
- LLM / S3 / 外部 HTTP 调用不进数据库事务；
- 公共能力放 `common/` 或 `infrastructure/`，不散落业务 Service；
- 改后端公共能力至少跑 `./gradlew :app:test --no-daemon`；改前端至少跑 `cd frontend && pnpm run build`。
