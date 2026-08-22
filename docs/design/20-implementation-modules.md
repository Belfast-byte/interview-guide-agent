# Agent 重实现实施模块与交付切片

> 状态：实施基线
>
> 权威输入：[平台演进设计](./01-platform-design.md)、[自适应文本面试实现设计](./10-text-interview.md)、[算法题面试设计](./11-algorithm-interview.md)、[代码分析服务设计](./12-code-analysis-service.md)
>
> 最后更新：2026-08-12

## 1. 文档目的

本文把设计文档中的 M0～M5、A0～A3、CA-1～CA-4 转换为可独立实现、可独立验收、依赖明确的工程模块与交付切片。

这里的“阶段”和“模块”含义不同：

- **阶段**描述能力按什么顺序解锁，例如 M0 先建立 ReAct 内核，M5 最后启用评估；
- **模块**描述长期稳定的责任与依赖边界，例如 `core` 永远负责状态规则，`assessment` 永远负责评估与证据；
- 一个阶段可以激活多个模块，一个模块也可能被多个阶段逐步扩展；
- 禁止创建 `m0/`、`m1/` 这类阶段包。阶段结束后它们没有业务语义，还会迫使后续阶段跨包改写同一职责。

当前唯一实施主线是 M0 → M5。算法面试和代码分析是挂接到主线的专项能力，不得复制编排器、工具网关、证据库或评估体系。

## 2. 必须保持的业务不变量

模块拆分首先服务于正确性，而不是目录整齐。以下规则从第一批代码起就必须可执行：

1. **模型建议，代码裁决**：Agent 只能返回动作建议；会话状态、轮次预算、工具白名单、终止条件由 `core` 中的确定性规则裁决。
2. **编排器是唯一状态修改者**：Controller、Agent、工具、MCP 适配器都不能直接推进会话状态。
3. **评估可后置，证据不可后置**：M0 就完整保存问题、回答原文、模型动作摘要和工具调用记录；M5 必须能对 M0～M4 会话回填评估。
4. **外部调用不进入事务**：LLM、MCP、S3、HTTP 和沙箱调用在事务外执行；持久化由短事务命令完成。
5. **同一事实源，多种投影视图**：候选人报告和企业报告只能投影同一组 assessment/evidence，不能各自生成结论。
6. **不可信输入不能越权成为指令或证据**：JD、简历、回答和代码仓库都是数据；简历主张和代码分析产物不能直接变成能力结论。
7. **旧 MVP 已删除**：`agent-loop-mvp-v1` 已于 2026-08-22 删除；只保留其 deadline、单问题校验、skill hash、乐观锁四项工程经验。

## 3. 代码边界

### 3.1 根包与隔离策略

新实现统一放在：

```text
interview.guide.modules.interview.agent.adaptive
```

`adaptive` 表达业务能力“自适应 Agent 面试”，不是临时版本号。旧 MVP 的根级 `runtime/`、`tool/`、`model/` 已于 2026-08-22 删除。

第一阶段仍保留在现有 Gradle `:app` 模块内，以 package 边界和架构测试约束依赖。此时直接拆成多个 Gradle 子模块会增加 Spring 扫描、测试夹具、迁移脚本和配置装配成本，却还没有独立部署收益。真正需要独立扩缩容和安全边界的沙箱服务、代码分析服务从一开始就是独立部署单元。

### 3.2 稳定模块

| 模块 | 建议包 | 唯一责任 | 允许依赖 | 首次激活 |
|---|---|---|---|---|
| 接入 | `adaptive.api` | HTTP 路由、请求校验、DTO/Response 映射、限流 | `application` | M0 |
| 应用编排 | `adaptive.application` | create/answer/tool-result 等用例；组织“外部调用 → 裁决 → 短事务落库” | `core`、`runtime`、各能力端口 | M0 |
| 领域内核 | `adaptive.core` | 会话/轮次状态、输入事件、动作、预算和合法转换；唯一裁决规则 | 无 Spring AI/JPA/HTTP 依赖 | M0 |
| ReAct 运行时 | `adaptive.runtime` | 有界循环、deadline、步预算、重复工具调用检测；返回建议，不落库 | `core`、角色/模型/工具端口 | M0 |
| 角色系统 | `adaptive.role` | `AgentRoleRegistry`、角色 Prompt、上下文策略、工具白名单、角色预算 | `core`、`runtime` 端口、`common.ai` | M0 |
| 持久化 | `adaptive.persistence` | Entity、Repository、MapStruct、单一写入服务、乐观锁、结构化历史读取；实现各能力拥有的存储端口 | `core`、各能力的 port/model | M0 |
| 规划 | `adaptive.planning` | JD + 简历 → 维度计划；维度覆盖与轮次分配规则 | `core`、`role`、自身存储端口 | M1 |
| 工具 | `adaptive.tool` | Tool SPI、网关、白名单/预算、幂等键、调用审计、本地工具适配器 | `core`、`runtime` 端口 | M2 |
| 记忆 | `adaptive.memory` | 上下文装配、维度小结、topics/claims/practice；公平性读写规则 | `core`、自身存储端口 | M3 |
| MCP 集成 | `adaptive.mcp` | MCP Client 适配器、MCP Server 接入、租户/scope/审计边界 | `application`、`tool` 端口 | M4 |
| 评估与报告 | `adaptive.assessment` | 深度量规、评估 Agent、证据校验、确定性报告、历史回填 | `core`、自身只读端口 | M5 |
| 算法面试 | `adaptive.algorithm` | 题目、提交、异步判题、迟到结果裁决、沙箱证据 | `application`、`tool`、`core` 事件端口 | A0～A3 |
| 代码分析接入 | `adaptive.codeanalysis` | 仓库任务、三类结构化产物、锚点校验、MCP 薄适配 | `mcp`、`tool`、`planning` 端口 | CA-1～CA-4 |
| 可观测性 | `adaptive.observability` | 指标、审计字段、trace 关联；不得记录回答/代码等敏感原文 | 各模块发布的稳定事件 | M0 起 |

大文件量模块内部按职责划二级子包（2026-08-16 落地，纯机械移动，不改变顶层依赖方向）：

| 顶层模块 | 二级子包 |
|---|---|
| `core` | `session`（会话/轮次/状态机）、`action`（Agent 动作）、`context`（各角色上下文与领域值对象）、`event`（输入事件） |
| `memory` | `brief`（维度小结）、`claim`（候选人主张）、`profile`（能力画像）；`ContextAssembler` 留根包 |
| `persistence` | `session`、`plan`、`memory`、`assessment`、`practice`、`algorithm`，按 §4 数据所有权分组 |
| `assessment` | `depth`（深度评估）、`evidence`（证据校验）、`report`（双视图报告）、`practice`（练习推荐）、`backfill`（历史回填） |
| `algorithm` | `problem`（题目选题）、`sandbox`（沙箱协议）、`judge`（异步判题流）、`evidence`（沙箱证据）；`api` 原有 |
| `codeanalysis` | `job`（任务生命周期）、`repo`（仓库快照）、`claim`（主张核验）、`scenario`（场景卡）、`trace`（调用链）；入口服务留根包 |

注意：`persistence.memory.CandidateMemoryClaimStatus`（候选人记忆 claim 状态，仅 `UNVERIFIED`）与 `codeanalysis.claim.ClaimVerificationStatus`（代码事实核验状态）是两个不同概念，不要混用。

### 3.3 依赖方向

```mermaid
flowchart LR
    API[api] --> APP[application]
    MCP[mcp] --> APP
    APP --> CORE[core]
    APP --> RT[runtime]
    RT --> CORE
    RT --> ROLE[role ports]
    RT --> TOOL[tool ports]
    ROLE --> CORE
    TOOL --> CORE
    PLAN[planning] --> CORE
    MEM[memory] --> CORE
    ASSESS[assessment] --> CORE
    PERSIST[persistence] --> CORE
    ALGO[algorithm] --> TOOL
    ALGO --> CORE
    CODE[codeanalysis] --> MCP
    CODE --> TOOL
```

约束：

- `core` 不依赖 Spring AI、JPA、Redis、S3、MCP 或 Web；它必须能用纯单元测试验证状态机。
- `runtime` 不调用 Repository；它消费不可变上下文并返回建议或失败。
- `persistence` 不决定下一动作；它只保存已经裁决的事实。
- 存储端口由需要数据的业务模块拥有（例如 `memory.port.MemoryStore`），`persistence` 提供实现；业务模块不得 import Entity 或 Repository。
- `assessment` 不读取会影响公平性的候选人历史评级；长期记忆可影响选题，不影响同一回答的评级。
- `algorithm` 和 `codeanalysis` 只能通过稳定端口接入主线，不能各自创建第二个 Agent loop。

## 4. 持久化所有权

“M5 能否回填 M0 历史会话”的答案取决于持久化模块是否从第一天稳定。表按事实类型归属：

| 所有者 | 表/记录 | 首次建立 | 关键守护 |
|---|---|---|---|
| `persistence` | `agent_sessions` | M0 | `runtime_version`、状态、`@Version`；新旧运行时不混写 |
| `persistence` | `agent_turns` | M0 | 问题/回答原文不截断；一轮一行；可定位原始顺序 |
| `persistence` | 决策摘要与工具调用审计 | M0/M2 | 不存完整 CoT；动作、理由摘要、输入输出摘要可追溯 |
| `planning` | `agent_plans` | M1 | 维度、focus、预算、状态可查询，覆盖由代码强制 |
| `memory` | `dimension_briefs`、`candidate_memory_*`、`practice_records` | M3 | 小结只导航；claim 恒为未验证；写入白名单 |
| `assessment` | `agent_assessments`、`agent_evidences` | M5 | quote 原文子串；报告只引用已验证证据 |
| `algorithm` | `algorithm_problems`、`sandbox_executions`、日志引用 | A0 | submissionSeq/codeHash；IE 非负面证据；迟到结果可识别 |
| `codeanalysis` | repo/job/digest/claim/scenario | CA-1 | commitHash、稳定 ID、真实 file:line 锚点、保留期 |

所有写入通过应用编排调用对应的事务服务，禁止 Agent、Controller、Tool executor 或 Stream producer 直接写业务状态。Stream consumer 可以落自己的执行结果，但唤醒面试状态前仍须交给编排器裁决。

## 5. 主线实施切片

### 5.1 M0：可追溯的单面试官 ReAct 纵切

M0 不按“先写完所有 Entity，再写完所有 Service”的横向方式推进，而按可运行纵切拆分：

| 切片 | 交付 | 主要模块 | 出口验收 |
|---|---|---|---|
| M0.0 边界守护 | `adaptive` 根包、feature flag/runtime version | core/test/config | 新旧入口可明确区分（旧 MVP 已于 2026-08-22 删除，依赖禁令随之移除） |
| M0.1 领域契约 | Session/Turn/InputEvent/Respond/ToolCall、状态转换、预算值对象 | core | 非法转换、空问题、超预算均由纯单测拒绝 |
| M0.2 事实落库 | `agent_sessions`、`agent_turns`、决策记录、Repository、短事务写入服务 | persistence | 完整问题/回答可重读；schema 守护测试证明 M5 回填载体存在 |
| M0.3 有界运行时 | model port、ReAct loop、step/tool/deadline 预算、重复调用检测 | runtime/role | 模型空转被截断；超时和模型失败不推进数据库状态 |
| M0.4 应用纵切 | 创建会话、生成首题、提交回答、生成下一题/结束、幂等与乐观锁 | application/api | 6 轮动态面试可完成；并发提交只有一个合法推进 |
| M0.5 可观测与回归 | 调用次数/耗时/token、动作审计、黄金场景与失败回归 | observability/test | 能定位一次失败停在哪个输入、动作和状态；日志无回答原文 |

M0 的完成定义不是“接口能返回下一题”，而是：外部失败不会推进状态、重复/并发提交不会多走一轮、每个已完成轮次都保留未来评估所需事实。

### 5.2 M1～M5

| 阶段 | 实施切片 | 激活模块 | 硬依赖 | 出口验收 |
|---|---|---|---|---|
| M1 | M1.1 规划契约；M1.2 规划 Agent；M1.3 维度状态机与预算再分配 | planning/role/core | M0 | 桩模型永远建议追问时，代码仍覆盖全部维度；规划失败不建会话 |
| M2 | M2.1 Tool SPI；M2.2 白名单/预算/幂等；M2.3 审计；M2.4 本地 skill/题库/量规工具 | tool/runtime/role | M0；可与 M1 并行 | 越权被拒；相同工具参数不空转；题库结果有稳定 ID |
| M3 | M3.1 ContextAssembler；M3.2 维度小结；M3.3 topics/claims/practice；M3.4 公平性与注入守护 | memory/persistence | M1 + M2 | 12 轮 token 有界；非法 turnIndex 拒绝；回答注入不污染长期画像 |
| M4 | M4.1 MCP Client 适配；M4.2 远端题库降级；M4.3 tenant/scope/audit；M4.4 create/status Server | mcp/tool/application | M2 | 远端黑洞在 deadline 内降级；跨租户资源返回 404 |
| M5 | M5.1 L0～L4 量规；M5.2 评估 Agent；M5.3 quote/tool 证据校验；M5.4 确定性双视图报告；M5.5 历史回填 | assessment/persistence/memory | M3；M4 非硬依赖 | 证据可追溯率 100%；相同回答不受历史画像影响；M0～M4 会话可回填 |

主线依赖允许的并行关系只有：M1 与 M2 在 M0 完成后并行。M3 必须等待两者；M5 只硬依赖 M3，不能因 MCP 延后而阻塞评估闭环。

## 6. 专项能力挂接

### 6.1 算法面试 A0～A3

算法能力不是新主线，而是主线事件和工具协议的扩展：

| 阶段 | 模块改动 | 前置 |
|---|---|---|
| A0 | `algorithm` 打通 problem → submit → Redis Stream → sandbox worker → execution 落库 | M0 + M2 |
| A1 | `core` 增加 `ToolResult` 输入事件；`tool` 增加 `Pending(handle)`；`algorithm` 实现 superseded/90s 降级裁决 | A0 |
| A2 | 题目变体、隐藏用例隔离、执行配额、滥用监控 | A1 |
| A3 | `assessment` 启用 `TOOL_RESULT` 证据，算法评级强制引用执行结果 | A2 + M5 |

内核唯一的框架级扩展是增加事件输入类型，不增加“挂起中的 Agent loop”。等待发生在循环之外，结果事件开启新的正常循环。

### 6.2 代码分析 CA-1～CA-4

代码分析处理候选人既有仓库，算法沙箱执行候选人现场代码；两者安全模型、证据语义和部署边界不同，禁止合并。

| 阶段 | 模块改动 | 前置 |
|---|---|---|
| CA-1 | 独立分析服务、异步任务、MCP 薄工具面、digest/claim/scenario 存储 | M4 + A0 + M5；按专项设计在评估闭环稳定后启用 |
| CA-2 | planning 读取 digest；项目面试官使用 claim/scenario；锚点校验 | CA-1 + M1 + M5 |
| CA-3 | PATCH 场景接入算法沙箱，执行结果回到统一证据链 | CA-2 + A3 |
| CA-4 | Prompt 注入、超大仓库、超时降级、保留期与成本治理 | 与 CA-1～CA-3 同步加固，最终独立验收 |

`CONTRADICTED` 只能触发核验问题，不能直接产生负面评估；`CODE_FACT` 只能说明问题来源或主张核验，能力结论仍来自候选人回答与实操结果。

## 7. 三个优先失败场景

### 7.1 LLM 成功，落库冲突

朴素方案在 LLM 返回下一题后直接覆盖会话 JSON。两个并发回答都可能调用模型并各自写入，表现为跳轮、重复题或回答绑定错问题。

防线：输入带 expectedTurn/version；`core` 裁决合法转换；`persistence` 用 `@Version` 或条件更新；冲突方不追加新 turn。测试用两个线程提交同一轮回答，只允许一条状态推进。

### 7.2 为省空间截断原始回答

M0 看起来只需要上下文摘要，若把回答截断或只存摘要，M5 无法校验 quote，也无法回填历史评估。症状是新会话可评估、旧会话只能生成不可追溯结论。

防线：`agent_turns` 原文列从 M0 建立；小结只供导航；schema 守护测试和历史回填测试共同锁定“不截断”不变量。

### 7.3 把阶段当模块

若创建 `m0/runtime`、`m2/tool`、`m5/evidence`，A1 增加 `ToolResult` 时会同时穿透多个阶段包，最终形成循环依赖；维护者也无法判断状态规则到底属于哪个阶段。

防线：package 按长期责任命名；阶段只存在于 issue、里程碑、测试标签和本文交付切片中。架构测试约束 `core` 的独立性与适配器依赖方向。

## 8. 测试分层与阶段门禁

| 测试层 | 证明什么 | 不证明什么 |
|---|---|---|
| `core` 纯单测 | 状态转换、预算、动作和并发前置条件确定性 | Spring/JPA 装配和真实数据库约束 |
| Repository/H2 集成测试 | 映射、查询、事务边界和基本约束 | PostgreSQL 特有 SQL、pgvector、真实并发语义 |
| PostgreSQL 迁移测试 | Flyway 脚本、唯一约束、索引与生产方言 | LLM/MCP/Redis 外部故障 |
| 桩模型/桩工具场景测试 | 空转、超时、非法动作、迟到结果和降级可重复复现 | 真实模型质量与 token 分布 |
| 黄金场景回归 | 端到端业务流程和结构化事实完整 | 所有异常组合和容量上限 |
| 真实 Provider 小样本评测 | Prompt 可用性、延迟、token 与输出分布 | 确定性正确性；不能替代代码规则测试 |

每个阶段必须同时满足：编译通过、相关单元/集成测试通过、失败场景通过、关键指标可观测。只通过 happy path 不得进入下一阶段。

## 9. 第一批执行顺序

从当前分支开始按以下顺序推进（旧 MVP 实现已于 2026-08-22 删除）：

1. M0.0：建立 `adaptive` 根包、依赖禁令和 runtime feature/version 边界；
2. M0.1：定义纯领域契约和非法状态转换测试；
3. M0.2：先建立 `agent_sessions` / `agent_turns` 的事实模型与回填守护测试；
4. M0.3：实现不落库的有界 ReAct runtime；
5. M0.4：用 application service 串成首个端到端纵切；
6. M0.5：补并发、失败不推进、审计与指标门禁；
7. M0 验收通过后，M1 与 M2 才允许并行启动。

第一批代码的最小业务闭环是“创建会话 → 首题 → 回答 → 下一题/结束 → 完整事实可重读”。暂不包含多维度规划、长期记忆、MCP、评级和报告；这些能力提前进入 M0 会掩盖内核与证据底座是否正确。
