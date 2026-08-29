# 13. 自适应面试 Agent 优化方案（候选人侧）

> 维护：Agent；上游设计决策以 `docs/design/` 为准。
>
> 来源：`adaptive-agent-review-2026-08-15.md`（外部评估报告，已经逐条对照代码核实）。
> 范围：**不含企业 MCP / 租户答题链路 / ATS / 企业控制台**。企业侧事项另行立项。
> 状态：部分有效；Agent 策略部分已按 2026-08-29 新控制边界重写。
>
> Agent Loop、Coverage 与 Working Memory 的实施权威见 [36-agent-loop-working-memory-spec.md](./36-agent-loop-working-memory-spec.md)。安全、权限、输入大小和外部服务边界继续有效。

## 0. 前置校正

评估报告有两条与当前代码不符，落地前先从问题清单中移除：

1. ~~前端路由 `/adaptive-interview` 未挂载入口~~ —— `Layout.tsx` 主导航「面试准备」分组已有「自适应面试」入口，无需处理。
2. 「练习推荐只有 PENDING」表述修正 —— `PracticeStatus` 枚举含 `PENDING / COMPLETED`，但无任何代码流转到 COMPLETED。问题改为「练习状态机无流转」，见 §4.1。

另有一条报告未点名但核实中发现的新问题，纳入 P0：`DepthLevel.L4` 把“切换维度”写进评级枚举，混淆了评估事实与 Agent 策略，见 §1.5。

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

### 1.2 维度小结 / 声明抽取成为独立 enrichment

**问题**：小结或声明抽取失败会直接导致回答提交失败、状态不推进；重试需重跑全部 LLM 调用。

**方案**：

- 回答、Assessment、Evidence、ProbeGap 和 Episode 是主链事实；小结与声明标签是可重算 enrichment，不进入同一事务。
- 主链提交后，enrichment worker 扫描“已有 Episode、缺少 enrichment”的记录处理；失败显式记录在该 enrichment 结果上，不在 application 中 `catch Exception` 后伪装成功。
- ContextAssembler 始终可以从 Turn/Assessment/Evidence 原始事实组装；小结只是已有时使用的导航优化，不是 fallback correctness 路径。
- 重跑只按缺失 Episode 扫描，不建立 Intent、Patch、checkpoint 或多层恢复状态机。

**验收**：小结/声明生成失败时，主链领域事实完整，失败状态可见；重新扫描只补缺失 enrichment，不重复 Assessment、Episode 或后继 Turn。

### 1.3 SAMPLE 空回答边界缺陷

**问题**：前端允许「尚未提交回答」时直接运行 SAMPLE；结果到达后 `AdaptiveAlgorithmResultReadyHandler` 无条件调用 `reassessAlgorithmResult()`，此时 `agent_turns.answer` 为 null，评估上下文与证据校验拿到空回答。

**方案**：
- `reassessAlgorithmResult()` 入口校验 `turn.answer() == null` 时直接跳过，工具结果事件正常 ACK（不当作失败）。
- 判题结果保留在 `SandboxExecution`；终态与唯一 Evidence/`consumedAt` 原子提交。候选人正式提交回答后从最新领域事实运行正常评估链路，不经过通用 ToolResultEvent。
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

**问题**：L0–L4 量规在 `DepthLevel` 枚举与 assessment prompt 中各有一份；且枚举 L4 文案「当前维度可提前完成」把下一动作策略写进了正式评级。

**方案**：
- prompt 模板通过 StringTemplate 注入 `DepthLevel` 枚举渲染的量规文本，删除 prompt 内的手写副本。
- 删除 `DepthLevel.actionTendency`。DepthLevel 只描述当前证据支持的能力深度，不决定追问、切换或结束。

**验收**：`AssessmentFoundationContractTest` 增加断言「prompt 渲染结果包含枚举量规原文」。

## 2. P1 Agent 驱动的动态面试策略（核心能力）

**问题**：`recommendSwitchQuestion`、`InterviewPlan.replan`、固定维度顺序和按 L0/L4 分配轮次，把“最值得验证什么”提前算在 Java 中。模型最终只按选定参数生成一句问题，Agent autonomy 退化为模板生成。

**目标数据流**：

~~~text
Plan + Turn + Assessment + ProbeGap + Evidence
  -> CoverageProjector
  -> ContextAssembler + WorkingMemorySnapshot
  -> InterviewAgentLoop
  -> ASK | FINISH
  -> AgentDecisionValidator
  -> 短事务提交最终事实和 Snapshot
~~~

**Agent 决定**：

- 当前优先验证哪个 Target 和 Gap；
- 继续深挖、换验证方式还是切换 Target；
- 是否调用允许的只读 Tool；
- 下一题内容和结束建议。

**Java 只保证**：

- 当前 Session/Turn 可推进；
- Target 属于 Plan；
- 任何路径不超过产品最大轮次；
- Tool、Evidence 和 provenance 合法；
- 同一回答并发时只提交一次最终事实。

Plan 保存初始化 Target 和硬上限，不在运行中维护 completed turns、维度状态或重新分配历史。Coverage 由 Turn/Assessment/ProbeGap/Evidence 推导；非法 Agent 决定以 rejection Observation 返回模型，不由 Java 代选另一个动作。

本场最新 Assessment、ProbeGap 和 Evidence 可以进入 InterviewAgentLoop，因为它们正是选择下一步所需的本场事实。公平性边界是：跨会话历史评级和 Semantic 画像不得进入本场 Assessor，也不得直接提高或降低本轮评分。

**验收**：

- 多个 open Gap 时，模型可在合法集合中选择不同优先项；
- 模型可在未按固定维度顺序的情况下切换，但不能选择 Plan 外 Target；
- 模型的 ASK/FINISH 不突破最大轮次；
- 删除 `recommendSwitchQuestion`、`actionTendency` 和运行时 `InterviewPlan.replan` 后，Coverage 仍可从事实完整生成；
- 同一回答并发只产生一个 Assessment、后继 Turn 和 Working Memory Snapshot。

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
- 复测：把用户选定 scope 内的 Semantic planning view 提供给 Planner；优先验证什么由模型决定，Java 只保证不扩大用户 scope。
- 这是长期记忆「反哺 planning」的第一个消费方，为后续预算调整铺路。

### 4.2 上下文压缩与显式边界
- JD / 简历：创建会话时生成一次会话级摘要，后续各轮注入摘要替代原文；原文仍持久化可查。
- 项目上下文：`ProjectInterviewContext` 按全部合法 Target/Coverage 组装必要 claims/scenarios；可以优先当前 Snapshot 的关注点，但不得隐藏其他合法选择。
- ContextAssembler 只使用已经存在且可追溯的导航摘要；不得在超限时临时生成摘要并静默替换原上下文。
- 必要字段仍超出统一 token 边界时返回包含各部分 token 占用的明确错误；§1.2 的 enrichment 独立生成，不改变主链 correctness。

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
