---
paths:
  - "app/src/main/java/interview/guide/modules/interview/agent/**/*.java"
  - "app/src/test/java/interview/guide/modules/interview/agent/**/*.java"
  - "app/src/main/resources/prompts/adaptive-agent-*.st"
---

# Adaptive Interview Agent Rules

适用于 `modules/interview/agent/adaptive/`。设计唯一事实源是 `docs/design/`，包划分与依赖方向见 `docs/design/20-implementation-modules.md` §3.2/§3.3。

## 包职责与依赖方向

- 依赖方向：`api → application → {core, runtime}`；`role`/`tool`/`planning`/`memory`/`assessment`/`persistence → core`；`algorithm → {tool, core}`；`codeanalysis → {mcp, tool}`。
- `core` 是纯领域内核（session 状态机、action、event、值对象），禁止 import Spring AI/JPA/Redis/Web，必须纯单测可验证。
- `runtime`（`BoundedReActRuntime`）不调 Repository，只返回建议动作；`persistence` 不决定下一动作。
- 存储端口由业务模块拥有，`persistence` 提供 `Jpa*Source/Store` 实现；业务模块不得 import Entity/Repository。
- 大模块内部按职责划二级子包（`persistence.session`、`assessment.depth` 等）；禁止 `m0/` 式阶段包。

## 模型建议，代码裁决

- 状态迁移、轮次上限（1-12）、维度完成、计划轮次分配由代码确定性裁决：`AdaptiveInterviewSession`、`InterviewPlan.decide`、`PlanningTaxonomy.validate`。模型输出一律视为提案。
- 轮次用尽时把模型的 ASK 强制改写为 FINISH；维度顺序固定，不允许模型跳维度。
- 角色只有 `PLANNER`（创建时规划，1 步无工具）和 `INTERVIEWER`；评估不是角色，由 application 层显式调 `DepthAssessmentAgent`。
- 证据必须锚定真实材料：评估 quote 经全半角/空白归一化后命中回答原文，单条不命中丢弃该条而非整轮失败（`AssessmentEvidenceValidator`）；`ProbeGap` 锚点按同一归一化匹配、超 2 条截断而非拒绝；代码出题必须携带命中真实分析产物的 `CodeQuestionProvenance`，否则注入 rejection observation 让模型重写一次，重写仍失败才拒绝。
- 裁决层（`InterviewPlan.decide`、`DepthAssessmentAgent` 裁决、`ToolGateway`）已校验的提案，下游代码不再重复校验。

## Runtime 与工具

- ReAct 循环统一走 `BoundedReActRuntime`（`maxSteps`/`maxToolCalls`/`deadline` 三重预算，重复工具调用直接拒绝）；不要在别处自写循环。
- 工具实现 `AdaptiveAgentTool` 并只能经 `ToolGateway` 执行：角色白名单、参数规范化、幂等 invocationId、结果大小上限都在这里；模型网关（`SpringAiAdaptiveAgentModelGateway`）已关闭 ToolCallingAdvisor，禁止改回自动注册。
- 新增工具要加进 `AgentRoleRegistry` 的角色白名单才生效。
- 异步工具（`sandbox_submit`）返回 `PendingToolResult`；结果以 `ToolResultEvent` 落库后由 `handleToolResult` 开启新一轮 ReAct。等待不发生在循环内。

## 编排与持久化

- 「外部调用 → 裁决 → 落库」的串联只在 `application` 层（`AdaptiveInterviewApplicationService`、`AdaptiveAlgorithmResultReadyHandler`）。
- 写库统一走 `AdaptiveInterviewPersistenceService`，短事务 + `@Version` 乐观锁；并发冲突抛业务异常，不自动重试。
- 全部会话状态/记忆存 PostgreSQL；Redis 只用于异步 Stream（判题、代码分析），生产/消费继承 `AbstractStreamProducer`/`AbstractStreamConsumer`。
- `persistence.memory.CandidateMemoryClaimStatus`（恒 `UNVERIFIED`）与 `codeanalysis.claim.ClaimVerificationStatus` 是两个概念，不可混用。
- 公平性：历史能力画像可影响选题，不得影响同一回答的评级（`CandidateMemoryFairnessContractTest` 锁定）。

## 算法判题与代码分析

- 判题异步链路：提交 → 落库 → Redis Stream 入队 → `SandboxWorker`（唯一实现 `SandboxdClient`，调独立部署的沙箱服务）→ 短事务落结果 → `AlgorithmResultReadyHandler` 唤醒重评估 + 生成追问。
- verdict 使用既有枚举（AC/WA/CE/TLE/MLE/RE/IE）；迟到结果（`supersededBy` 非空）直接忽略；`TIMEOUT_QUEUED` 不得作为负面证据。
- 代码分析经 `/internal/code-analysis/jobs` worker 回调；锚点必须命中 `CodeAnchorCatalog`，假锚点拒绝。
- codeanalysis 与 algorithm 不合并，不引入第二个 Agent loop。

## 配置、MCP 与可观测

- 配置前缀：`app.interview.adaptive-agent`（`AdaptiveAgentProperties`）、`app.interview.algorithm`、`app.interview.code-analysis`；新增配置项加进对应 `@ConfigurationProperties`，不散落 `@Value`。
- Prompt 模板放 `resources/prompts/adaptive-agent-*.st`。
- MCP server 用 Spring AI `@McpTool` 暴露；租户凭证/scope/审计走 `McpEndpointCredentialFilter` + `McpScopeGuard` + `AdaptiveMcpAuditService`，不要绕过。
- 指标走 `AdaptiveAgentTelemetry`/`AlgorithmInterviewTelemetry`/`CodeAnalysisTelemetry`（Micrometer）；失败日志统一 `adaptive_agent_failed phase=model|runtime|persistence|planning|tool` 格式，日志不记录回答原文。

## 测试

- 测试目录镜像主包；`core`/`runtime` 纯单测，编排用 `AdaptiveInterviewFlowIntegrationTest` 式纵切测试。
- 改包结构或新增子包时同步更新 `AdaptivePackageIsolationTest`。
- 并发语义（同轮重复提交只推进一次）由 `AdaptiveInterviewConcurrencyIntegrationTest` 锁定，改持久化层后必跑。
