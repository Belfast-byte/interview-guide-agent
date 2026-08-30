# Agent Loop 与 Working Memory 临时实施 Tickets

> 维护：Agent
>
> 上游规格：[36-agent-loop-working-memory-spec.md](./36-agent-loop-working-memory-spec.md)
>
> 状态：临时执行文档；全部票据验收后删除，长期裁决只回写 36 号规格及相关技术规格。
>
> 建立日期：2026-08-29

## 0. 执行规则

- 每票必须交付一个可运行的实现结果；不允许只建类型、接口、表或 TODO。
- 每票完成后只保留一条生产路径，不双写、不加长期 feature flag 或 compatibility adapter。
- “不做”是票据边界，不得顺手实现后续票；发现前置缺失时显式失败并回到依赖票。
- 每票只新增或改写证明该票业务不变量的测试；删除只锁定旧机制的测试，不按被删类逐个补“已删除”测试。
- 票内测试均使用 60 秒超时。默认命令为：
  `timeout 60s ./gradlew :app:test --no-daemon --console=plain --tests '<测试类>'`。
- 全量 `:app:test` 只在 T09、T14、T15 三个高风险门禁执行；迁移 SQL 只在 T14 做一次 PostgreSQL Flyway + JPA `validate` 验证。
- 每票实现、聚焦测试和相应规格同步完成后，按票独立提交。

## 1. 依赖与实施顺序

~~~text
T01 -> T02 -> T03 -> T04
                     |-> T05 --|
                     |-> T06 --+-> T07 -> T08 -> T09 --|
                     |-> T10 ---------------------------+-> T11 -> T12 -> T13 -> T14 -> T15
~~~

全部票据初始状态为“待实施”。T05 与 T06 可在 T04 后并行；T10 可在 T04 后与 T05～T09 并行，但 T11 必须等待 T09、T10。其余按箭头顺序执行。

## 2. Tickets

### T01 — SandboxExecution 稳定业务键与唯一终态

- **实现目标**：相同 `sessionId + turnIndex + problemId + sourceHash + runMode` 的提交创建或复用同一 `SandboxExecution`；worker、超时器和重投只能写入一个终态。
- **实现范围**：稳定键及唯一约束、`createOrReuse`、PENDING 扫描重投同一 `executionId`、终态条件更新；保留现有沙箱隔离和配额语义。
- **不做**：不生成 Evidence，不触发 Agent Loop，不改通用 Tool 协议。
- **必要测试**：同键重试复用同一执行；不同源码键生成新执行并正确 supersede；worker 与 timeout 竞争只保留一个终态。修改 `AlgorithmPersistenceServiceTest`、`SandboxExecutionEntityTest`，不新增穷举状态组合。
- **完成判定**：生产提交链不再使用 Intent UUID 作为沙箱幂等依据，失败直接暴露。

### T02 — 沙箱终态与 Evidence 原子消费

- **实现目标**：在一个短事务内锁定未消费终态、写入唯一 Evidence 并设置 `consumedAt`，重复送达等效一次。
- **实现范围**：用 `SandboxExecution` 取代 `ToolResultEvent` 的 reserve/complete 窗口；Evidence 指向 execution/artifact 稳定 ID；迟到、superseded、`TIMEOUT_QUEUED` 不产生负面 Evidence。
- **不做**：不删除其他只读 ToolExecution，不启动 Agent Loop。
- **必要测试**：重复/并发消费只写一条 Evidence；Evidence 插入失败时 `consumedAt` 同事务回滚；superseded 和平台超时不污染证据。改写 `AdaptiveAlgorithmResultReadyHandlerTest` 与 `JpaAlgorithmResultReadyDeliveryStoreIntegrationTest`。
- **完成判定**：算法结果送达不再依赖 `agent_tool_result_events`，`SandboxExecution` 是唯一副作用事实源。

### T03 — 领域事实读模型与 CoverageProjector

- **实现目标**：从 Session、不可变 Plan、Turn、Assessment、ProbeGap、Evidence 一次投影出中性的 `CoverageView`。
- **实现范围**：定义 facts reader 与 `CoverageProjector`；current turn、asked/remaining turns 和 target coverage 均由事实推导；停止写 Plan 的 runtime status/completedTurns，旧列暂留待 T14 删除。
- **不做**：不选择 active Target、不排序 Gap、不决定追问/切换/结束。
- **必要测试**：多 Target/多 Gap 投影完整且保持中性；remaining turns 只由 `maxTurns` 和 Turn 推导；关闭 Gap 和 Evidence 反映到对应 coverage。新增一个纯单测 `CoverageProjectorTest` 即可。
- **完成判定**：Coverage 计算不读取 WorkState、Patch、Intent 或 SemanticState。

### T04 — Context、维度 API 与报告切换到事实读链

- **实现目标**：`ContextAssembler`、维度/摘要 API 和最终报告只消费 T03 的事实读模型，不再读取 WorkState 或 Tool 执行状态。
- **实现范围**：批量查询事实；自动加载 Plan 固定 Skill；EVALUATION 仅组装中性历史曝光，Assessor 输入仍不包含历史评级、Semantic 或 Working Memory。
- **不做**：不引入 Working Memory 新契约，不改变 Agent 决策路径。
- **必要测试**：`ContextAssemblerTest` 证明完整 Target/Gap 暴露且不预选；`AdaptiveInterviewResponseTest` 与 `AssessmentReportServiceTest` 各保留一个事实一致性场景。禁止为每个 DTO 字段复制测试。
- **完成判定**：删除 WorkState 数据后，这些读路径在编译和测试层面仍可成立。

### T05 — Assessor 输出收缩为正式事实提案

- **实现目标**：Assessor 只产出 Assessment、Evidence、ProbeGap proposal，移除 WorkState Patch 和下一动作策略。
- **实现范围**：保留 quote、Gap anchor、代码 provenance 的真实来源校验；同一回答的 EVALUATION 评估输入与 Working Memory/历史画像隔离。
- **不做**：不选择下一 Target/Gap，不调用 InterviewAgentLoop，不落最终事务。
- **必要测试**：真实 quote/anchor/provenance 通过且伪造引用拒绝；同一回答在是否存在历史画像时形成相同正式评估输入。改写 `DepthAssessmentAgentTest`、`AssessmentEvidenceValidatorTest`，删除 `AssessmentWorkStatePlannerTest`。
- **完成判定**：assessment 包和 Prompt 不再出现 Patch、selectedGap 或固定切换策略。

### T06 — WorkingMemory 与 Turn Snapshot

- **实现目标**：实现 36 号规格的最小 `WorkingMemory`，ASK 时与产生的问题原子保存在同一 Turn。
- **实现范围**：完整对象替换更新；校验 Target/Gap/Evidence/Observation 引用来自当前 AgentContext；最新 Turn Snapshot 作为下一轮种子；FINISH 不写额外 Snapshot。
- **不做**：不建 Delta、Patch、Reducer、revision、独立当前状态表或恢复调度。
- **必要测试**：合法引用可随 Turn 保存并重读；非法引用显式拒绝；序列化结果不含领域全文、phase、revision、budget、Intent/Tool status。新增 `WorkingMemoryValidatorTest` 和一个 Turn 持久化测试，不测试 JSON 库本身。
- **完成判定**：一次 Loop 无论更新多少次，数据库只在 Turn 边界保存最终 Snapshot 一次。

### T07 — AgentDecision 校验与无 Tool InterviewAgentLoop

- **实现目标**：建立 `AgentDecision`、结构化 rejection Observation 和支持 ASK/FINISH 的 `InterviewAgentLoop`，由模型真正选择 Target/Gap/追问或切换。
- **实现范围**：校验 Plan 成员关系、单次一个问题、引用真实性；非法提案返回模型重决策；step/deadline 耗尽明确失败且不提交事实。
- **不做**：暂不执行 ToolCall，不替模型改写 Target、Gap 或问题。
- **必要测试**：模型选择非首 Target/Gap 可 ASK；模型可在 Plan 顺序和 expectedDepth 之外自由 follow-up/switch；非法 Target 或伪造引用收到 Observation 后可重新 ASK；FINISH 原样返回；资源耗尽抛出明确错误。集中在一个 `InterviewAgentLoopTest` 参数化场景，避免按字段拆类。
- **完成判定**：首题和后续决策均可通过 0 Tool 的真实模型循环得到 ASK/FINISH。

### T08 — 创建会话与首题切换到 Agent Loop

- **实现目标**：创建链变为 Planner → 校验 → 保存 Session/Plan → Agent Loop → 保存首 Turn/Snapshot，并移除首题 ASK Intent/WorkState 写入。
- **实现范围**：事务外 Planner/Loop、两个短事务、首 Turn/Snapshot/QuestionExposure 原子写入、响应丢失时返回已存在首 Turn；规划失败不得建立会话。
- **不做**：不迁移回答链，不接入 rubric Tool。
- **必要测试**：无 Tool 首题真实 ASK 并保存唯一 Turn/Snapshot；规划失败不建 Session；Loop 失败保留可重试的 Session/Plan 但不产生 Turn；首 Turn 已提交后的重试返回原 Turn。新增或改写一个 `AdaptiveInterviewCreationServiceTest`，删除对应 ASK recovery 测试。
- **完成判定**：生产创建链没有 ASK Intent、NextActionPolicy 或 WorkState 双写。

### T09 — 回答推进与最终短事务切换

- **实现目标**：回答链变为条件 claim answer → 事务外 Assessor/Agent Loop → 一个短事务保存 Assessment/Evidence/ProbeGap/Episode 与下一 Turn/Snapshot，或完成 Session。
- **实现范围**：相同 payload 重放幂等、不同 payload 显式冲突；Session version 和唯一约束保证并发只推进一次；下一 Turn 同事务写 QuestionExposure；达到 `maxTurns` 直接完成；移除 `uk_agent_turn_source_probe_gap` 以允许多轮验证同一 Gap。
- **不做**：不持久化模型/只读 Tool 中间步骤，不调用 rubric Tool。
- **必要测试**：answer 后失败可重跑；model 后/commit 前失败不产生半套事实；commit 后响应丢失返回已有结果；并发同答仅一套 Assessment/Episode/后继 Turn；同一 Gap 可关联多个 Turn。集中为 `AdaptiveAnswerProgressionTest` 与一个 repository 并发测试。
- **完成判定**：回答生产路径不再调用 ActionIntent、WorkStatePolicy 或 Patch；本票执行一次 60 秒全量 `:app:test`。

### T10 — 无状态只读 ToolGateway

- **实现目标**：把 `ToolGateway` 收缩为请求内只读 executor，只负责 allowlist、schema、tenant/scope、provenance、deadline 与按序 dispatch。
- **实现范围**：Tool 成功、空结果、timeout、error 都返回不可信 Observation；同一模型响应的 calls 按原顺序执行；资源耗尽明确失败。
- **不做**：不持久化 invocation、pending、execution 或 recovery；不实现具体 rubric 搜索。
- **必要测试**：非白名单/schema/scope 越界被拒；调用顺序保持；成功、空、timeout/error 形成可区分 Observation。收敛到 `ToolGatewayTest`，不为每个 Tool adapter 重复网关测试。
- **完成判定**：只读 Tool 执行不写数据库，外部输出不能提升为 system instruction。

### T11 — rubric_search 与真实 Tool 循环

- **实现目标**：实现首个真 Tool `rubric_search(query, intent, levelHints)`，打通 Model → Tool → Observation → Model → ASK。
- **实现范围**：语义搜索 adapter、采用的 entry/version provenance；固定 Skill 由 ContextAssembler 自动加载，固定 ID 的 `rubric_get` 保持普通 Service；Working Memory 可跨多个 Observation 更新但只保存最终 Snapshot。
- **不做**：不启用题库或 memory_search，不保存只读调用历史。
- **必要测试**：一个集成场景证明 `rubric_search → Observation → ASK` 且保存采用 provenance/最终 Snapshot；一个 adapter 单测证明查询参数透传与 scope。新增 `RubricSearchLoopTest`，不做模型质量排列组合。
- **完成判定**：生产主链真实存在至少一次 Tool Observation 回流后的二次模型决策。

### T12 — 删除伪 Tool 与单角色平台包装

- **实现目标**：删除 `LoadSkillTool`、`SandboxSubmitTool`、固定查询身份的 `RubricLookupTool`、当前无闭环的 question-bank index/MCP fallback，以及单角色 `AgentRoleRegistry/Definition`。
- **实现范围**：调用方分别归入 ContextAssembler、Application Command、普通 Service 或具体模型 gateway；删除对应配置、测试和 Prompt 字段。
- **不做**：不删除真实 `rubric_search`、`code.trace` 或未来需求文档，不新增通用 Role/Tool 注册平台。
- **必要测试**：更新 `AdaptivePackageIsolationTest` 验证 runtime/tool 的长期依赖方向，并执行 `:app:compileJava`；复用 T08、T11 场景证明生产装配，不编写“类不存在”逐项测试。
- **完成判定**：Spring context 中只装配有真实消费者的能力，固定 Skill 和 sandbox 均不产生 ToolCall。

### T13 — 删除旧运行时与恢复协议代码

- **实现目标**：删除 WorkState/Patch/Reducer/NextActionPolicy、ActionIntent 全链、ToolResultEvent/PendingToolResult、read-only ToolExecution 及其 scheduler/repository/codec。
- **实现范围**：删除生产引用、配置、Prompt/API 伪状态和只锁定旧行为的测试；Application/Persistence 大服务按 create/answer/sandbox-result 用例收缩。
- **不做**：暂不 drop 数据库表列（留给 T14），不保留 legacy branch 或空 adapter。
- **必要测试**：执行 T08 `AdaptiveInterviewCreationServiceTest`、T09 `AdaptiveAnswerProgressionTest`、T11 `RubricSearchLoopTest` 和 `AdaptivePackageIsolationTest`；删除旧机制测试，不按删除清单补同构测试。
- **完成判定**：`rg` 生产源码不再命中被删类型，主链编译且三条纵向场景通过。

### T14 — 新 Flyway 迁移与遗留 schema/API 清理

- **实现目标**：用新的、不可回改历史的 Flyway migration 删除 Intent/WorkState/Patch/Event/read-only ToolExecution 表、FK、列、枚举和 Plan runtime 字段，并使 JPA 映射与前端响应同步。
- **实现范围**：保留 Session version、领域 unique、Turn Snapshot、SandboxExecution/Evidence；清除 ToolResult 和 WorkState 伪状态；迁移已有必要事实后再 drop，无法无损映射的数据显式记录裁决。
- **不做**：不修改任何已应用 migration，不保留 legacy codec 或长期 feature flag。
- **必要测试**：新增一个 PostgreSQL 空库 Flyway migrate + JPA `validate` 测试，并验证从当前迁移基线升级；H2 只跑受影响 repository 测试，不能替代 PostgreSQL。执行一次 60 秒全量 `:app:test`。
- **完成判定**：生产 schema 与 Entity 均无旧状态机结构，维度/Coverage API 和报告仍只读领域事实。

### T15 — Episodic/Semantic 派生记忆收口

- **实现目标**：Episode 删除 `workRevisionBefore/After` 和题答副本依赖；enrichment 从缺少结果的 Episode 扫描；SemanticContribution 成为 correctness 事实源并默认按读聚合。
- **实现范围**：同一最终事务追加 Episode/Contribution；保留 QuestionExposure、公平性双轨和 correction 链；materialized SemanticState 若暂留只能作为可重建 cache。
- **不做**：不建设 checkpoint/recovery 状态机，不让正式 Assessor 读取历史评级或 Working Memory。
- **必要测试**：Episode 可由稳定 ID 追溯 Turn/Assessment/Evidence 且无 revision；缺 enrichment 可重算；Contribution 聚合在 EVALUATION/PRACTICE 隔离；相同正式回答不受历史画像影响。复用 `EpisodeFactPersistenceTest`、`SemanticAggregatorTest` 和一个公平性场景，不扩展模型质量测试。
- **完成判定**：36 号规格 §13 全部成立；执行最终一次 60 秒全量 `:app:test`，然后把永久裁决回写规格，并删除本临时文档及 README 索引项。

## 3. 总体验收映射

| 36 号规格验收主题 | 负责票据 |
|---|---|
| 沙箱稳定幂等、终态与 Evidence 原子性 | T01～T02 |
| 事实 Coverage、API/报告不读 Snapshot | T03～T04 |
| Assessor 公平性与证据真实性 | T05 |
| Working Memory 最小契约与单次 Snapshot | T06 |
| 模型自主选 Target/Gap、ASK/FINISH、明确资源失败 | T07～T09 |
| answer/模型/commit 崩溃恢复与并发一次推进 | T08～T09 |
| allowlist/schema/scope、Tool 错误 Observation | T10 |
| `rubric_search → Observation → ASK` | T11 |
| 伪 Tool、Role、旧状态机和 schema 删除 | T12～T14 |
| Episode/Semantic 收缩与历史隔离 | T15 |

任一主题只在表中指定票据编写行为测试；后续票只运行必要回归，不复制同一断言。

## 4. 执行记录

| Ticket | 状态 | 必要性裁决 | 验证 |
|---|---|---|---|
| T01 | 已完成 | 实施稳定负载 ID 与 `createOrReuse`；复用已有终态锁，不增加重复守卫；现有同步入队失败已显式暴露，个人项目不为极小崩溃窗口新增 PENDING 常驻扫描器 | 6 组沙箱聚焦测试通过 |
| T02 | 已完成 | 删除沙箱对 `ToolResultEvent` reserve/complete 的依赖；终态锁、唯一 Evidence 与 `consumedAt` 共用一个事务；平台失败、排队超时和过期结果不形成候选人证据 | 5 组 Evidence/对账/判题聚焦测试通过 |
| T03 | 合并至 T04 | 单独建立无生产消费者的 Projector 删除后系统等价，因此拒绝横向脚手架；与 T04 合并为事实读链纵切 | 由 T04 共同验证 |
| T04 | 已完成 | API/MCP 改读 Coverage，current turn 从 Turn 推导；报告原本已只读领域事实，不做重写；Context 切换与新 Agent Loop 强耦合，延后到 T07，避免双上下文约束模型 | Coverage、API、MCP、报告 4 组测试通过 |
| T05 | 已完成 | Assessor 的公开结果原本已是 Assessment/Evidence/ProbeGap，拒绝再造等价 DTO；删除追问点数量截断、校验失败后的隐式重调与非法项丢弃；Plan 深度硬边界保留但改为明确拒绝，不替模型钳制结果。旧 WorkState Planner 的整体删除随 T07 新 Loop 替换，避免此票制造不可运行中间态 | Assessor、Evidence、旧 Planner 边界、Spring AI 生成器 4 组共 20 个测试通过 |
| T06 | 已完成 | 仅新增不可变 WorkingMemory、上下文引用 Validator 和 Turn 上的可空 Snapshot；不建 Delta/Patch/Reducer/独立状态表，不增加内容上限或认知状态枚举；同一 Gap 多轮验证所需唯一约束一并移除。Snapshot 读入 AgentContext 与 T07 Loop 一起接通，避免提前建立无消费者读取层 | WorkingMemory Validator、Turn 数据库往返及既有 Turn 实体 3 组测试通过 |
| T07 | 已完成 | 建立中性 AgentContext、AgentDecision 与 0-Tool InterviewAgentLoop；Java 只校验 Plan/Gap/事实引用及 ASK/FINISH 必填结构，非法提案以结构化 Observation 回流，不改写模型选择；复用共享 deadline，拒绝新增无独立收益的 step cap。生产创建链接线归 T08 | 单一 InterviewAgentLoopTest 覆盖自由选择、两类拒绝后重决策、FINISH、deadline 共 4 个场景 |
| T08 | 已完成 | Planner 成功后才以短事务保存 Session/Plan；新 Spring AI Decision Model 与 Loop 在事务外生成首题，随后原子保存 Turn/Snapshot/QuestionExposure；创建路径不再初始化 WorkState 或建立 ASK Intent。Loop/队列失败保留 CREATED Session/Plan 供原业务键重试，不写伪失败状态 | 首题事务纵向测试、InterviewAgentLoopTest、Spring AppTest 3 组通过 |
| T09 | 已完成 | 回答路径改为条件 claim → 事务外沙箱命令/Assessor/AgentLoop → 单一最终事务；同 payload 在 PENDING/COMMITTED 均可重跑，不同 payload 明确冲突；当前 Assessor proposal 用显式临时负 ID 供模型引用，提交时只做技术 ID 解析，不改写 Target/Gap 语义；达到 maxTurns 直接完成。旧 WorkState/Intent 方法等待 T13 物理删除，但生产 submitAnswer 已无调用 | T09 纵向测试与架构隔离通过；单命令全量触发 60 秒硬超时后按 App/common/infrastructure、Adaptive 核心链、其余模块三批等价覆盖，全部通过 |
| T10 | 已完成 | `ToolGateway` 收缩为请求级 `ReadToolExecutor`，只按 `AgentContext` allowlist 与模型顺序执行同步只读 Tool；统一返回 validation/success/empty/timeout/error Observation，不生成 invocation、不持久化、不支持 Pending。旧 CALL_TOOL Intent 执行、恢复和 ToolExecution 写入口同步删除，不保留兼容网关 | `ToolGatewayTest` 覆盖 allowlist/schema/scope、顺序、四类结果与共享 deadline；`AskActionIntentRecoveryTest` 回归通过；生产源码旧 Tool Intent 入口零命中 |
| T11 | 已完成 | 新增 `rubric_search(query, intent, levelHints)` 语义检索和专用索引；`InterviewAgentLoop` 支持模型发起有序只读 Tool 调用并把 Observation 回流同一模型；ASK 只能采用成功 Observation 的来源，采用的 rubric entry/version 与最终 Snapshot 随 Turn 保存 | `RubricSearchLoopTest`、`RubricSearchToolTest`、`ToolGatewayTest` 及创建/回答纵向回归共 8 组聚焦测试通过 |
