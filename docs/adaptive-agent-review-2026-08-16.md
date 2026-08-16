# 自适应面试 Agent 综合审查报告（2026-08-16）

> 审查方式：五路并行只读审查（业务蓝图差距 / 核心链路编码质量 / 评估与记忆编码质量 / 专项能力与工程健壮性 / 优秀 Agent 横向差距），全部结论以当前代码为准，附文件:行号证据。
>
> 基线文档：`docs/design/00~14、20`。上一版评审：`docs/adaptive-agent-review-2026-08-15.md`（其中指出的问题本文已逐项复查，"仍未修复"均有代码证据）。
>
> 前置事件：本报告产生前刚完成 adaptive 包二级子包重构（core/memory/persistence/assessment/algorithm/codeanalysis 六个大包内部划子包，466 测试全绿），本文路径引用均为重构后新路径。

## 总体判断

**内核比外围强。** 状态机裁决、证据链、公平性防火墙、并发一致性这套"可信"内核工程质量明显高于平均水平：7 条业务不变量全部守住，M0~M5 / A0~A3 / CA-1~CA-4 骨架均已落地，466 个测试覆盖大量失败场景（并发、超时、空转、伪造来源、跨租户）。

但系统目前是**"可信但不可度量、不可回放、不可上线"**：评测闭环、决策回放、鉴权合规、灰度部署几乎全缺；且"自适应"的核心循环只写了一半——评估结论不驱动编排。

---

## 一、业务差距：距离"完整的自适应面试 Agent"还差什么

### 1.1 最大缺口：评估不驱动编排（"自适应"只完成了一半）

所有审查共同指向的核心问题：

- `recommendSwitchQuestion` 被评估 Agent 计算、校验、落库（`persistence/assessment/AdaptiveAgentAssessmentEntity.java:72`），但**全库没有消费方**；
- `planning/InterviewPlan.decide()` 是创建时一次性静态分配（`maxTurns = min(维度数×2, 12)`），L0 不追加验证轮、L4 不提前收维度；
- 连带矛盾：`assessment/depth/DepthLevel.java:30` 的 L4 文案"当前维度可提前完成"与状态机（无提前完成路径）自相矛盾（13 号文 §0 已点名的 P0 文档/代码冲突仍在）。

当前实际能力 = **规划自适应 + 追问自适应**。追问是真自适应（ReAct 循环每轮基于本维度历史自主决策，可调 4 个工具，有来源硬校验），不是固定脚本 + 润色；但评估结论不影响"出几道题"。设计文档 `13-adaptive-optimization.md` 已有 replan 方案，属于"资产已建、变现未做"。

### 1.2 蓝图承诺但未实现（按优先级）

| 优先级 | 缺口 | 证据 |
|---|---|---|
| P0 阻断 | **无鉴权**：`candidateId` 客户端裸传，知道 ID 即可读任何人画像/报告/会话 | `api/AdaptiveInterviewController.java:111-119` |
| P0 | **前后端超时错配**：前端 45s 同步等待 vs 后端维度完成轮串行最坏 ~90s（评估 20s + 小结 20s + 声明 20s + ReAct 30s），"前端先超时、后端后推进、重试报轮次不一致"断链场景存在 | `frontend/src/api/adaptiveInterview.ts:14` vs `application/AdaptiveAgentProperties` |
| P0 | **小结/claim 失败即提交失败**：`submitAnswer` 中 summarize/extract 无降级路径 | `application/AdaptiveInterviewApplicationService.java:231-249` |
| P0 | **SAMPLE 空回答保护缺失**：判题结果先于回答落库时 `reassessAlgorithmResult` 直读 `turn.answer()` 为 null，quote 校验 NPE | `AdaptiveAlgorithmResultReadyHandler.java:43-49` → `AssessmentEvidenceValidator.java:65` |
| P0 | **代码分析产物无上限**：zip 无单文件解压上限；claims/scenarios 无数量/单条文本/payload 总大小限制 | `infrastructure/codeanalysis/S3ZipCodeAnchorCatalog.java:55`、`codeanalysis/job/CodeAnalysisProperties.java` |
| P1 | **租户链路半吊子**：MCP 能 `createForTenant` 创建会话，但所有写路径硬编码 `tenantId IS NULL`，租户会话创建后无法推进（能创建、无法答题） | `persistence/session/AdaptiveInterviewPersistenceService.java:73,242,318,480` |
| P1 | `agent_turns`"不截断"无 schema 守护测试（20 号文 §7.2 明确要求） | — |
| P1 | 算法维度评级"强制引用执行结果"无强制校验（A3 部分达成，attachAvailable 是"有就挂"） | `algorithm/evidence/AlgorithmAssessmentEvidenceService` |
| P1 | 评级一致性端到端回归测试缺失（"固定回答 × 不同画像 → 评级一致"，10 号文 §7.7） | — |
| P2 | 练习闭环断裂：`PracticeStatus.COMPLETED` 无任何代码流转到它，无完成入口；复测降权 planning 未接 | `assessment/practice/PracticeStatus` |
| P2 | 上下文无压缩：JD/简历全文每轮注入面试官上下文，12k token 预算靠 fail-fast 硬扛，长 JD 直接打挂面试 | `memory/ContextAssembler.java` |
| P2 | 输入未打通：创建会话只接受 textarea 粘贴文本，未对接已有简历/JD 解析模块 | — |
| P2 | 运营配套：无录题入口；`docker-compose.yml` 无 sandboxd / 代码分析 worker 服务定义，算法判题和代码分析"代码有、交付没有" | `SandboxdClient` 默认指向 compose 中不存在的 `http://sandboxd:8090` |
| P3 | 合规闭环：无候选人数据删除接口；面试/画像/练习数据无保留期（代码分析产物有 30 天保留期，是全库唯一） | `codeanalysis/repo/CodeAnalysisRetentionScheduler.java` |

### 1.3 实现了但有偏差

- **代码分析接入形态**：实际是"内部 Worker HTTP 回调（token 鉴权）+ 平台作 MCP Server 暴露 `code.*` 五工具"，与 12 号文设计的"平台 MCP Client + Pi SDK"不同；13 号文 §7 已列入"改文档对齐代码"，12 号文至今未改。
- **工具 executor 直接写业务状态**：`SandboxSubmitTool` 在 ReAct 循环内直接写 `sandbox_executions` 并投递 Stream，发生在 `recordDecision` 之前——乐观锁冲突时留下"回答未落库但判题已入队"的孤儿执行，与 20 号文 §4 写入纪律有张力，且放大 1.2 的 SAMPLE 空回答 NPE 路径。
- **"最终评估"语义不一致**：报告/练习取 **max(depthLevel)**（`assessment/report/AssessmentReportService.java:21-23`），能力画像取**最后一轮**（`persistence/session/AdaptiveInterviewPersistenceService.java:418-423`）。同一维度从 L3 退步到 L2 时，报告与画像结论互相矛盾。
- **报告无措辞 Agent**：候选人报告是纯结构化结论 + 量规模板文案（10 号文标注"M5 后可选"，可接受，可读性打折）。
- `evidence_quote_check`/`practice_recommend` 未做成工具，由内部服务替代（功能等价，10 号文工具表字面承诺未兑现）。

### 1.4 蓝图外的合理补充（供参考）

- 会话生命周期：无暂停/续面语义、无进行中会话 TTL 与回收；
- 反作弊：沙箱 policy violation 之外无候选人侧反作弊（粘贴检测、作答时延异常等）；
- 报告人工复核队列（11 号文 §8 "WA 却评 L3+ 需人工复核"无落地）；
- 企业侧整体（能力模型权重、候选人列表、企业控制台）：01 号文有设计，13 号文明确"另行立项"，属已知留白。

### 1.5 业务不变量与失败场景核查结果

7 条业务不变量**全部守住**（模型建议代码裁决 / 编排器唯一状态修改者<small>有张力点见 1.3</small> / 评估可后置证据不可后置 / 外部调用不进事务 / 同一事实源多视图 / 不可信输入不越权 / 旧 MVP 只读隔离）。

三个优先失败场景：并发落库冲突防线齐（expectedTurn + 状态机裁决 + @Version + 集成测试）；回答截断部分（TEXT 列在、schema 守护测试缺）；阶段当模块防线齐（本次二级子包划分与 20 号文 §3.2 一致，已回写文档）。

---

## 二、编码实现问题（按严重度）

### 2.1 高危（必修）

1. **DB CHECK 约束漂移：CODE_FACT 证据在生产库落库必失败。**
   `V20260823__add_adaptive_assessment_evidence.sql:43-44` 建 `CHECK (evidence_type IN ('QUOTE','TOOL_RESULT'))`；`V20260906__add_code_fact_evidence.sql` 只重建了 source 约束未触碰 type 约束。凡 `turn.codeFactUsage() != null` 的 INSERT 在 Postgres 触发约束违反，整个 `recordDecision` 回滚——**候选人提交回答直接失败**。测试没抓到：`@DataJpaTest` 用 H2 `create-drop` 绕开 Flyway（生产是 `ddl-auto: validate` + Flyway）；更糟的是 `AssessmentFoundationContractTest.java:47-52` 把过时约束固化成了"契约"。
   修复：新增迁移放宽 type 约束 + 修正契约测试 + 补一条 Flyway/Postgres 级（Testcontainers）落库测试。

2. **沙箱执行 RUNNING 状态永久卡死，无回收路径。**
   消费者 JVM 在 `markRunning`（`algorithm/judge/AlgorithmPersistenceService.java:143`，仅 PENDING→RUNNING）之后、`applyResult` 之前崩溃：Stream 消息 idle 回收后 `tryMarkProcessing` 因状态已 RUNNING 返回 false，消息 ACK 丢弃，执行记录永远 RUNNING，面试官永远等不到唤醒。
   修复：`AlgorithmQueueTimeoutScheduler` 增加 `RUNNING && startedAt < cutoff` 回收（标 IE + pendingRejudge + 触发 resultReadyHandler）。

3. **判题结果落库后编排器唤醒失败即永久丢失。**
   `algorithm/judge/AlgorithmJudgeStreamConsumer.java:118-126`：`applyResult`（状态已 DONE）成功后调 `resultReadyHandler.handle(...)`（内含 LLM 调用）；handle 抛异常 → retryMessage → `resetAfterWorkerFailure` 因非 RUNNING 返回 false → 消息 ACK。结果已落库但 ToolResultEvent 未送达，无重试/对账。同类：`AlgorithmQueueTimeoutScheduler.java:26-29` forEach 中单个 handle 失败导致整批丢失唤醒。
   修复：唤醒事件持久化（复用 `uk_agent_tool_result_event` 幂等键机制）或 handle 独立带重试投递；调度器循环内逐个 try/catch。

4. **`code.submit_repo` 接受任意 S3 key，构成跨租户对象读取通道。**
   `mcp/CodeAnalysisMcpTools.java:53-75` 的 `repositoryRef` 仅校验非空/长度；存储是全平台单 bucket（`APP_STORAGE_BUCKET:interview-guide`）与候选人源码/简历/快照混放；`code.trace` 会把匹配行原文返回给调用租户。恶意租户提交他人/平台对象 key 即可逐行外泄。
   修复：服务端强制 `repositoryRef` 前缀（`code-analysis/{tenantId}/{sessionId}/`）或平台代上传；git URL 形式的授权校验与 worker 侧 SSRF 面需一并处理。

### 2.2 中危

5. **`handleToolResult` 事件预留在非 BusinessException 下泄漏**：`AdaptiveInterviewApplicationService.java:412-455` 只在 catch BusinessException 时 discard 预留；其他异常导致预留残留，重试被 `existsByToolNameAndResultId` 去重挡住 → 结果永远无法再处理且无告警。建议 catch 放宽或预留记录状态机化（RESERVED/FAILED 可重试）。
6. **限流 IP/USER 维度可被请求头伪造绕过**：`common/aspect/RateLimitAspect.java:215-242` 无条件信任 `X-Forwarded-For` 首跳与 `X-User-Id` 头，直连后端（绕过 nginx）即可绕过提交限流。建议可信代理链配置 + UserId 只取认证过滤器的 request attribute。
7. **代码分析 Job 状态机无守卫**：`codeanalysis/job/AnalysisJobEntity.java:73-95` complete/markRunning/fail/timeout 全部无条件赋值，迟到回调可任意翻转状态；且 FAILED/TIMED_OUT 的 job 因 `createJob` 复用已有记录**永远无法重新分析**（`CodeAnalysisPersistenceService.java:67-73`）。
8. **`pending_rejudge` 只写不消费**：11 号文承诺的"IE 后台重判回填"无读取方，半成品能力，要么补重判 worker 要么文档降级该承诺。
9. **并发提交的 LLM 成本浪费**：两个并发同 turnIndex 请求都跑完 4 类 LLM 调用才撞乐观锁。建议 LLM 流程前加轻量预占（turn 级 Redis 锁）。
10. **代码分析产物自由文本直入面试官上下文**：锚点存在性校验挡不住"带真实锚点的注入文案"（`CodeAnalysisInterviewContextService.java:56-81`）；缺 CA-4 要求的"注入样本仓库"端到端测试。
11. **quote 严格 `answer.contains()` 校验在 StructuredOutputInvoker 重试循环之外**：LLM 全半角改写/空白压缩即导致整个 submitAnswer 失败。建议校验失败时给一次带错误反馈的修复重试，或最小归一化比较（需先与设计确认"逐字"严格度）。同一模式也存在于 probeGaps 校验（`DepthAssessmentAgent.java:61-79`，gap 降级修复方案已定，尚未落地）。
12. **报告事实加载依赖隐式不变量**：`JpaAssessmentReportFactsSource.java:170,188-194` 多处假设"每个 assessment 必有 evidence""证据引用的 turn 存在"，破坏即裸 NPE。建议显式校验或测试固定不变量。

### 2.3 低危 / 坏味道（选列）

- 多处裸 `orElseThrow()` 违反 BusinessException 规范（app service :473；persistence :88/:248/:335/:423）；
- `SpringAiPlanningAgent.java:103-112` 重抛丢 cause 且用 NOPLogger（重试无日志）；`SpringAiDimensionBriefGenerator.java:112`、`SpringAiCandidateClaimGenerator.java:112` 同样丢 cause；
- `DeadlineExecutor` 超时是"调用方不等"而非资源停止，阻塞 HTTP 不响应中断，后台继续烧 LLM 额度；
- 题库题"恰好一个问号且无换行"校验可能让合规题库题系统性不可用（建议带 sourceQuestionId 的输出豁免）;
- `evidenceQuotes` 无数量/单条长度上限（probeGaps 有 MAX=2、rationale 有 500 字，quotes 无界）；
- `InterviewPlan.answer():92` 依赖"sum(allocatedTurns)==maxTurns"隐式不变量，无断言无注释，分配策略一改即越界；
- `InterviewerContext` 望远镜构造器（16 字段 4 构造器）+ `ContextAssembler` 四重 12-14 参数重载，新增字段要改 8+ 处签名；
- `submitAnswer` 单方法 136 行串 9 个阶段，建议抽阶段私有方法；
- PLANNER 的 `ReActBudget(1,0,plannerDeadline)` 是死配置（无使用方）；
- `AssessmentReportService.java:10` 未使用的 Transactional import；
- 画像取代判定用 `createdAt` 比较，同毫秒时方向错误（建议用 id/序号）；
- 长期记忆查询全量加载 + claim 恒 UNVERIFIED 无核验/过期机制，随会话数无界增长；
- Stream MAXLEN 1000 截断静默丢消息、消费者单线程串行 + 15s 沙箱读超时（黑洞期 ~4 任务/分钟），容量假设未写进文档；
- `SandboxdClient` 零测试；`/internal/` 端点明文无鉴权无签名（与 worker token 模型不一致）；
- MCP 过滤器路径失配时 NPE/500 而非 401；worker token 用 `String.equals` 非常量时间比较；
- 指标缺口：RUNNING 卡死、唤醒丢失等静默失败无计数器；评估类 gauge 每次 Prometheus 抓取打 DB count；queueDepth gauge 实例级且惰性更新；
- prompt 指令用 `currentAnswer` 而字段名是 `currentDimensionAnswer`（`adaptive-agent-interviewer-system.st` vs `InterviewerContext.java`）；
- `create`/`createForTenant` 双 public 入口掩盖租户链路不完整。

### 2.4 做得好的（简要）

- core 纯领域零 Spring/JPA 依赖，record/密封接口 + `List.copyOf` 贯穿，纯单测可验证；
- "模型建议、代码裁决"执行彻底：轮次上限强制 FINISH、覆盖前禁 FINISH、题库/代码 provenance 反伪造是代码校验而非 prompt 恳求、sandbox_submit 参数与真实提交绑定；
- 事务边界正确：所有 LLM 调用在 `recordDecision` 短事务之前；部分失败不留半截状态（有完整测试矩阵）；
- prompt 双层防注入：data-boundary 声明 + `StructuredOutputInvoker` 统一追加 ANTI_INJECTION_INSTRUCTION；评估/小结/claim 的输出契约校验与 prompt 一一对应；
- 公平性三层守护：schema 契约测试 + 评估上下文隔离测试 + 应用层"推进面试不读长期记忆"测试；
- 报告链路完全无 LLM，双视图同一事实源投影，有"报告只回放原始轮次不读小结转述"守护测试；
- 异步骨架（原子领取、悲观锁、幂等键、superseded 裁决、配额、降级）与设计文档高度吻合；
- 失败日志统一 `adaptive_agent_failed phase=...` 结构，可定位"卡在哪个阶段"；审计/指标不落敏感原文，有测试守护。

---

## 三、距离"优秀的 Agent"还差什么（横向七轴）

| 维度 | 现状 | 关键差距 |
|---|---|---|
| **评测与质量闭环** | **基本为零**（七轴中最弱）：全是 mock 测试，无黄金集；指标只有"量"没有"质" | prompt 改一版无任何手段知道变好变坏；文档承诺的黄金场景回归（20 号文 M0.5）、评级一致性回归（01 号文 :487）未落地 |
| **可调试性与回放** | 业务事实链完整（turns/tool_calls/assessments + 结构化失败日志），能定位 phase | 无"模型调用黑匣子"：渲染后 prompt 快照、raw output、ReAct 中间轨迹、repair 细节都不存；工具调用只存 500 字符摘要 |
| **Prompt 工程化** | 模板外置 + 路径可配 + 统一重试/schema 校验 + 12k 输入预算 fail-fast | 无版本/hash 管理（改了无法归因历史数据）；L0~L4 量规在 `DepthLevel` 枚举和 prompt 文本里各写一份（10 号文 :257 要求单一来源 + include）；adaptive 未接 `PromptSanitizer`；无灰度分流 |
| **交互体验** | 刷新可恢复（GET 全量状态 + 乐观锁）；判题等待有 2s 轮询 UI | 无流式；同步长请求是主矛盾（见 1.2 超时错配）；无候选人身份体系，无"我的历史面试"；面试中心无 adaptive 入口 |
| **成本与容量治理** | 有单轮 12k 输入预算、per-role token 指标、秒级限流 | 无会话级成本累计、无配额熔断、无并发准入（voice 模块有 Semaphore + maxConcurrent=50 但未推广）；JD/简历/项目上下文每轮重复注入是成本大头 |
| **智能化上限** | 追问真自适应；公平性防火墙方向正确 | 评估不反哺编排（见 1.1）；题库检索无相似度阈值/rerank/采用率指标（只有 fallback 计数）；画像维度 key 是自由文本，跨会话匹配不稳 |
| **上线就绪度** | feature flag 默认关（诚实）；配置集中；密钥加密治理；代码分析数据有 30 天保留期 | 无灰度（flag 是全开关）；候选人个人数据无保留期/删除接口（"被遗忘权"无法响应）；租户答题链路断；compose 缺 sandboxd/worker |

### 3.1 下一步最值得投的一个方向

**离线评测 + 决策回放一体化基建（"模型调用黑匣子" + 黄金场景回归），约 2~3 周。**

理由：其他六轴的改进（prompt 调优、动态裁决规则、检索调参、模型选型）全部依赖"怎么知道改好了"这个前提；没有评测与回放，1.1 的动态预算这种核心智能化改造就是盲改，改错了还会侵蚀这个系统最值钱的"可信评估"资产。杠杆位置现成：`TokenUsageAdvisor` 切面、`AssessmentBackfillService` 回填机制、`adaptive-agent-assessment-agents.md` few-shot 样本，三块积木都在，缺的是拼成 harness。

排第二的是**答题异步化 + SSE**（候选人侧体验最大单点），评测基建落地后立刻跟进。

---

## 四、建议路线图（按 ROI 排序）

1. **修 4 个高危缺陷**（2.1）——CODE_FACT 约束迁移 + 契约测试修正、RUNNING 回收、唤醒丢失补偿、S3 key 跨租户校验。都是小改动高爆雷的存量问题。
2. **投"评测 + 回放"基建**（2~3 周）：`agent_model_calls` 黑匣子表（渲染 prompt 快照/hash、raw output、parsed 结果、repair 次数、token、prompt 版本）+ 20~50 场标注黄金会话进 CI。
3. **接通 `recommendSwitchQuestion` + `InterviewPlan.replan`**（1~2 周）：L0 换题不换维、L3+ 提前收维度、结余轮次给弱维度，保留硬上限；顺手修 L4 文案矛盾。设计已在 13 号文 M-B。
4. **答题异步化 + SSE/轮询进度**：submitAnswer 立即返回 ACCEPTED，后端异步推进，一举解决超时错配/重试不一致/等待无反馈。
5. **降级保护小项**（各 1 天内）：小结/claim 失败降级、SAMPLE 空回答保护、quote/probeGap 校验修复重试。
6. **上线就绪**：鉴权（依赖平台登录体系决策）、候选人数据删除接口 + 会话 TTL、compose 补 sandboxd/worker、灰度分流。

---

## 附：审查方式与遗留说明

- 五路审查均为静态代码审查 + 逐文件核对设计文档验收标准；评估与记忆一路额外运行了 `:app:test`（全绿）。
- 2.1-1 的 CODE_FACT 约束失败路径、1.2 的 SAMPLE 空回答 NPE 路径为代码调用链推断，未在生产环境实际复现（推断链条完整，见上文引用）。
- 1.2 中"画像维度 key 不稳定""辅助任务在关键路径"沿用旧评审结论 + 本次 grep 未发现修复证据，未逐行重读 `CandidateAbilityProfileService` 全量代码。
- 后端测试基线：466 tests / 0 failures / 49 skipped（2026-08-16 包重构后）。
