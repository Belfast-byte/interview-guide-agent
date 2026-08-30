# Agent 重构 T10～T15 设计规格

## 1. 目标

在已经完成 T01～T09、生产 create/submitAnswer 主链已切换到无 Tool `InterviewAgentLoop` 的基础上，完成剩余 T10～T15：建立真实只读 Tool 循环，删除伪 Tool、Role 平台与旧 WorkState/Intent 运行时，清理遗留 schema，并收缩 Episodic/Semantic 派生状态。

最终必须满足 `docs/design_spec/36-agent-loop-working-memory-spec.md` §13：生产主链存在真实 `ToolCall → Observation → Model → ASK`；Java 只裁决硬边界；Working Memory 只在 Turn 边界保存；SandboxExecution 是副作用唯一事实源；旧 WorkState、Patch、Intent、恢复协议和 schema 全部删除。

## 2. 当前基线

- T01～T09 已完成，T03 合并至 T04。
- 创建链为 `Planner → Session/Plan → InterviewAgentLoop → Turn/Snapshot`。
- 回答链为 `answer claim → Assessor → InterviewAgentLoop → 最终短事务`。
- `InterviewAgentLoop` 当前只支持 ASK/FINISH 和 validation Observation。
- `AgentContext.allowedReadTools` 当前为空，没有生产 `rubric_search`。
- 旧 `ToolGateway`、`AgentRoleRegistry`、WorkState、ActionIntent、旧 Runtime、恢复 API 和数据库结构仍存在，等待 T10～T14 删除。
- Episode enrichment 和 SemanticState 派生状态等待 T15 收缩。

## 3. 实施策略裁决

严格保留 T10～T15 的现有边界。每票必须形成一个可运行状态，完成聚焦验证并独立提交。

拒绝以下方案：

- 不合并 T10～T12：同时改 ToolGateway、模型协议、具体 Tool 和删除旧平台会扩大故障定位面。
- 不按 S3～S5 形成三个大提交：旧 Java 运行时删除与 schema 删除必须分开验证。
- 不引入 feature flag、双写、compatibility adapter 或第二套 Agent Loop。

## 4. 阶段 A：真实只读 Tool 闭环

### 4.1 T10：无状态只读 ToolGateway

`ToolGateway` 改为请求内 executor，只负责：

- Tool allowlist；
- 参数 schema；
- tenant/owner scope；
- provenance；
- 共享 deadline；
- 按模型顺序 dispatch。

输入为当前 Agent Context、模型给出的有序只读 Tool calls 和共享 deadline。输出统一为不可信 `DecisionObservation`，明确区分成功、空结果、超时和错误。

只读 Tool 调用不生成 invocation ID，不持久化 pending、execution、result event 或 recovery 状态。T10 不实现具体 rubric 搜索，也不改变 ASK/FINISH 主链。

### 4.2 T11：rubric_search 与真实循环

新增唯一首发 Tool：

```text
rubric_search(query, intent, levelHints)
```

扩展 `AgentDecision` 支持 `CALL_READ_TOOLS`。`InterviewAgentLoop` 按以下顺序执行：

1. 模型产生有序 Tool calls；
2. `ToolGateway` 校验并逐个执行；
3. 每个结果转为 Observation；
4. Observation 回流同一模型；
5. 模型继续 CALL_READ_TOOLS，或返回 ASK/FINISH。

固定 Skill 继续由 ContextAssembler 自动加载。最终采用的 rubric entry/version 进入 Turn provenance；未采用结果和中间调用不落库。Working Memory 可在多次 Observation 之间完整替换，最终只随 ASK Turn 保存一次。

## 5. 阶段 B：删除伪平台和旧运行时

### 5.1 T12：删除伪 Tool 与 Role 包装

删除：

- `LoadSkillTool`；
- `SandboxSubmitTool`；
- 固定查询身份的 `RubricLookupTool`；
- 当前没有业务闭环的 question-bank index/MCP fallback；
- `AgentRoleRegistry`、`AgentRoleDefinition` 及单角色平台配置。

职责归位：

- 固定 Skill → ContextAssembler；
- sandbox submit → Application Command；
- 固定 rubric get → 普通 Service；
- `rubric_search` → 唯一真实只读 Agent Tool。

不得创建替代 Registry、通用 Tool 平台或第二模型 runtime。

### 5.2 T13：删除旧运行时与恢复协议

删除生产代码、配置、API 和旧行为测试中的：

- WorkState、TargetWorkState、WorkPhase、WorkIssue；
- Patch、Reducer、Operation、NextActionPolicy；
- ActionIntent、PersistentActionCoordinator、recovery scheduler；
- `BoundedActionRuntime` 和旧 ReAct 类型；
- read-only ToolExecution、PendingToolResult、ToolResultEvent；
- ActionIntent retry API；
- 对应 repository、codec 和 transaction service。

Application 和 Persistence 按 create、answer、sandbox-result 用例自然收缩，不新增 Facade。T13 结束时生产 Java 源码不得再命中旧类型，且只保留新 `InterviewAgentLoop` 生产路径。

## 6. 阶段 C：schema 与 Memory 收口

### 6.1 T14：Flyway 与遗留 API 清理

新增单向 Flyway migration，不能修改已应用历史 migration。删除：

- Intent/WorkState/Patch/Event/read-only ToolExecution 表；
- 遗留 FK、列、索引和枚举约束；
- Plan runtime 字段；
- 前端/API 暴露的旧执行伪状态。

保留：

- Session version；
- Turn Snapshot；
- Session/Turn/Assessment/Evidence 领域唯一约束；
- SandboxExecution 和 `consumedAt`；
- Evidence provenance。

数据库门禁必须覆盖 PostgreSQL 空库 Flyway migrate、当前基线升级和 JPA `validate`。H2 只验证受影响 repository 行为，不能替代 PostgreSQL。

### 6.2 T15：Episodic/Semantic 派生记忆收口

Episode 删除 work revision 和题答副本依赖，以稳定 ID 追溯 Turn、Assessment 和 Evidence。Enrichment 从“缺少结果的 Episode 事实”扫描，不保存 PROCESSING checkpoint 或通用恢复状态。

SemanticContribution 成为 correctness 事实源并默认按读聚合。SemanticState 若存在真实性能消费者才允许暂留，且必须明确为可删除重建 cache，不能参与权限、正式评估或报告正确性。

保留 QuestionExposure、公平性双轨和 correction 链。正式 Assessor 不读取历史评级、Semantic 画像或 Working Memory。

## 7. 终态数据流

```text
ContextAssembler
  → InterviewAgentLoop
      → 0..N ToolGateway read-only calls
      → Observation 回流模型
      → ASK | FINISH
  → Java 硬边界校验
  → 一个短事务提交领域事实
```

Sandbox 不进入 ToolGateway：

```text
Application Command
  → SandboxExecution
  → Redis Stream / sandbox
  → 原子终态 + Evidence
  → 后续 Agent Loop 从领域事实读取
```

## 8. 错误与并发边界

- 非白名单、schema、scope 或 provenance 错误形成明确 validation Observation，由模型重新决策；Java 不改写 Tool、参数、Target、Gap 或问题。
- Tool 空结果、超时和外部错误作为不同 Observation 回流；资源 deadline 耗尽明确失败且数据库不推进。
- 只读 Tool 可重算，不建立幂等或恢复状态。
- sandbox 继续使用稳定业务键和 SandboxExecution 唯一终态。
- 同一回答只由 answer claim、数据库 unique 和必要的 Session version 保证一次推进，不叠加 Intent 或 WorkState revision。

## 9. 测试与提交门禁

每票执行：

1. 修改或新增能暴露该票旧行为的测试；
2. 运行并确认失败原因符合目标；
3. 实现最小生产改动；
4. 聚焦测试通过；
5. 执行该票要求的编译、集成或全量门禁；
6. 更新 `36-agent-loop-working-memory-tickets.md` 执行记录；
7. 独立主题提交。

关键门禁：

- T10：调用顺序、allowlist/schema/scope、成功/空/timeout/error Observation。
- T11：生产 `rubric_search → Observation → Model → ASK` 集成场景及采用 provenance。
- T12：Spring 装配不含伪 Tool/Role，固定 Skill 和 sandbox 不生成 ToolCall。
- T13：生产源码无旧类型命中，创建、回答、真实 Tool 三条纵切通过。
- T14：PostgreSQL Flyway + JPA validate，API/Coverage/Report 继续只读领域事实。
- T15：Episode/Semantic、公平性场景和最终全量 `:app:test` 通过。

## 10. 完成标准

- T10～T15 全部独立提交并记录验证证据；
- 生产主链有真实只读 Tool Observation 二次模型决策；
- 固定 Skill、sandbox 和固定 rubric 查询不再伪装成 Agent Tool；
- Java、配置、API、测试和 schema 中无 WorkState、Patch、Intent 或旧恢复协议；
- Working Memory、SandboxExecution、领域事实和 SemanticContribution 各自只有一个明确 owner；
- 没有双写、legacy runtime、silent fallback、mock success 或无消费者平台抽象。
