# 自适应面试 Agent 改进方案 Spec(2026-08-16 评审落地)

> 维护：Agent；上游设计决策以 `docs/design/` 为准。
>
> 状态：历史实施基线；Agent 策略与恢复机制不得继续按本文实现
>
> 权威输入：2026-08-16 综合审查结论（原报告已移除，下称“评审”）、[13-adaptive-optimization.md](./13-adaptive-optimization.md)、[20-implementation-modules.md](./20-implementation-modules.md)
>
> 最后更新：2026-08-29
>
> 2026-08-29 校准：本文保留当时的问题证据和非 Agent 治理项。IM-2 的确定性 replan、ToolResultEvent、完整模型中间态持久化以及任何与 [36-agent-loop-working-memory-spec.md](./36-agent-loop-working-memory-spec.md) 冲突的方案均已失效。

## 1. 文档目的

把 2026-08-16 评审的发现转换为可独立实现、可独立验收、依赖明确的改进项(IM-x)。每个改进项给出：现状证据、方案、改动点(文件级)、验收标准、测试要求。

已完成、不在本 spec 范围内的事项:

- 评审 §2.1 四个高危缺陷(CODE_FACT 约束迁移 V20260909、RUNNING 卡死回收、唤醒丢失补偿、S3 key 跨租户前缀强制)——2026-08-16 已修复,491 测试全绿;
- adaptive 包二级子包重构——2026-08-16 已完成,§3.2 已回写本文档。

## 2. 全局约束(所有改进项必须遵守)

1. 沿用 20/36 号文边界：模型决定面试语义策略，Java 只裁决硬边界；只有 application 短事务修改领域事实；外部调用不进事务；用户可见事实和证据不可后置。
2. 新增持久化一律 Flyway 迁移 + 短事务写入服务;禁止 Agent/Controller/Tool 直接写业务状态。
3. 观测数据不得记录回答/代码等敏感原文(评审 §三.2 的"黑匣子"是唯一例外,见 IM-3 的脱敏与边界约定)。
4. 每个改进项的完成定义 = 编译通过 + 新增/相关测试全绿 + 验收标准逐条可演示。
5. 包归属遵循 20 号文 §3.2 的二级子包约定。

## 3. 改进项总览

| ID | 改进项 | 来源 | 优先级 | 依赖 | 预估 |
|---|---|---|---|---|---|
| IM-1 | 降级保护包(SAMPLE 空回答 / 小结与 claim 失败降级 / quote 与 probeGap 修复重试) | 评审 §1.2、§2.2-11 | P0 | 无 | 1~2 天 |
| IM-2 | ~~确定性评估驱动 replan~~ → 被 36 号 Agent 策略取代 | 评审 §1.1；13/36 号文 | 已取代 | IM-1 | 不执行旧方案 |
| IM-3 | 模型调用黑匣子 `agent_model_calls` + prompt 版本 hash | 评审 §三.1/2/3 | P1 | 无 | 1 周 |
| IM-4 | 黄金场景离线评测 harness + CI 门禁 | 评审 §三.1 | P1 | IM-3 | 1~2 周 |
| IM-5 | 答题异步化 + 进度推送(消除 45s/90s 超时错配) | 评审 §1.2、§三.4 | P1 | 无 | 1~2 周 |
| IM-6 | 中危修复包(handleToolResult 预留泄漏 / 限流头伪造 / 代码分析 Job 状态机守卫 / pending_rejudge 重判) | 评审 §2.2-5/6/7/8 | P1 | 无 | 2~3 天 |
| IM-7 | 租户链路收敛(二选一:补写路径 or 移除 createForTenant) | 评审 §2.1(旧)、§2.2 | P2 | 业务决策 | 0.5~2 天 |
| IM-8 | 鉴权与候选人身份(阻断级) | 评审 §1.2 | P0 | 业务决策(登录体系) | 视方案 |
| IM-9 | 数据合规:候选人数据删除接口 + 会话 TTL | 评审 §三.7 | P1 | IM-8(身份) | 3~5 天 |
| IM-10 | 部署配套:compose 补 sandboxd / 代码分析 worker | 评审 §1.2 | P2 | 无 | 1~2 天 |
| IM-11 | 上下文压缩(JD/简历去重注入)+ 成本护栏(会话级 token 配额) | 评审 §三.3/5 | P2 | IM-3 | 1 周+ |
| IM-12 | 练习闭环:COMPLETED 流转 + 复测降权 | 评审 §1.2 | P2 | 无 | 3~5 天 |

实施顺序建议:IM-1(快赢清雷)→ IM-3 → IM-4(评测基建先行,后续所有改动的验证手段)→ IM-2(用 IM-4 验证)→ IM-5 / IM-6 并行 → IM-8 之后 IM-9。IM-7/10/11/12 按资源插入。

---

## IM-1 降级保护包(P0,1~2 天)

### 背景与证据

三处"辅助产物失败 = 候选人回答提交失败"的过度耦合(评审 §1.2、§2.2-11):

- SAMPLE 判题结果先于回答落库时,`AdaptiveAlgorithmResultReadyHandler.handle()` 对 DONE 无条件调 `reassessAlgorithmResult`,后者直读 `turn.answer()` 为 null,`AssessmentEvidenceValidator.validateQuote`(`assessment/evidence/AssessmentEvidenceValidator.java:65`)对 null 调 `contains` → NPE;
- `AdaptiveInterviewApplicationService.submitAnswer()`(`application/AdaptiveInterviewApplicationService.java:231-249`)中 `dimensionBriefService.summarize` / `candidateClaimExtractionService.extract` 无降级,维度完成轮一次 LLM 失败 = 提交失败;
- `DepthAssessmentAgent.validateProbeGaps`(`assessment/depth/DepthAssessmentAgent.java:61-79`)与 quote 校验在 `StructuredOutputInvoker` 重试循环之外,LLM 全半角改写/空白压缩即整体失败。

### 方案

1. **SAMPLE 空回答保护**:`reassessAlgorithmResult` 入口校验 `turn.answer() == null` 时跳过重评,仅挂工具证据(证据链不断,评级等回答到达后的正常评估)。拒绝"吞异常"姿势——这是显式分支,不是 catch。
2. **小结/claim 失败降级**:两者是"可后置"的导航/记忆产物(20 号文 §2.3 只要求证据不可后置),失败时记 warn + telemetry(`brief.degraded`/`claim.degraded` counter),本轮以 `dimensionBrief = null` / `claims = List.of()` 继续推进。**评估(depth)不降级**,仍快速失败——它是证据链的一部分。
3. **quote/probeGap 校验反馈**：把明确校验原因返回产生提案的模型重新生成；耗尽已有调用预算后显式失败。不得静默丢弃、截断 Gap 或伪造 Evidence。

### 改动点

- `application/AdaptiveAlgorithmResultReadyHandler.java`:空回答分支;
- `application/AdaptiveInterviewApplicationService.java`:小结/claim 降级 + 观测;
- `assessment/depth/DepthAssessmentAgent.java`:`validateProbeGaps` → `sanitizeProbeGaps`;
- `assessment/evidence/AssessmentEvidenceValidator.java` + 生成器:quote 修复重试接线;
- `observability/AdaptiveAgentTelemetry.java`:降级计数。

### 验收标准

- SAMPLE 结果先于回答到达时,会话状态正常推进,不产生 NPE;回答到达后的评估能引用该工具证据;
- 小结/claim 生成失败时回答正常提交、状态推进,指标有计数;
- probeGaps 含非法条目时其余合法条目仍进入面试官上下文;quote 首次非法时观察到一次修复重试。

### 测试要求

- 三个场景各自的失败路径单测(中文 @DisplayName);
- 保留既有"评估模型失败时不推进状态"语义不变;
- ProbeGap 的 null、schema、锚点真实性和总体 payload 边界；不再测试“超 2 条截断”。

---

## IM-2 Agent 决策通道（旧确定性 replan 已取代）

问题证据仍成立：`recommendSwitchQuestion` 没有消费者，静态 Plan 无法表达真正的语义自适应。但解决方案不再是在 Java 中增加 L0/L4、置信度、每维度题数和固定覆盖顺序。

当前方案是：Plan 保持不可变；Coverage 从 Turn/Assessment/ProbeGap/Evidence 推导；InterviewAgentLoop 读取全部合法 Target/Gap，自主决定深挖、切换或结束。Java 只校验 Plan 成员关系、最大轮次和证据/Tool 安全边界。详细改动和测试以 36 号规格为准。

---

## IM-3 模型调用黑匣子 + prompt 版本化(P1,约 1 周)

### 背景与证据

评审 §三.2/3:每次 LLM 调用的渲染 prompt、raw output、repair 轨迹全部丢弃;`agent_tool_calls` 只存 500 字符摘要;模板无版本号,历史数据无法归因 prompt 版本。这是 IM-4 和所有 prompt 调优的前置。

### 方案

1. 新表 `agent_model_calls`(Flyway 迁移):session_id、turn_index、role、prompt 模板路径 + 内容 SHA-256、渲染后 prompt 全文(或超阈值时 S3 引用 + hash)、raw output、parsed 结果摘要、repair 次数、token 三项、耗时、outcome。
2. 切面位置:`common/ai/StructuredOutputInvoker`(统一入口)+ `role/SpringAiAdaptiveAgentModelGateway`(interviewer 双通道)。写入走异步、失败不影响主链路(日志 + 指标),不进业务事务。
3. 模板加载处(`AdaptiveAgentProperties` 各 promptPath)计算 SHA-256 作为版本标识,随调用落库并进 telemetry tag。
4. **边界约定(全局约束 3 的例外)**:prompt 渲染含 JD/简历/回答,属敏感原文——该表默认仅内部排障用途,不进任何对外响应/MCP/报告;加保留期(建议 30 天,随 IM-9 的 TTL 机制统一实现);访问走内部查询,不加 REST 端点。

### 改动点

- 新迁移 + `persistence/session/`(或新 `persistence/telemetry` 子包)Entity/Repository/写入服务;
- `common/ai/StructuredOutputInvoker.java`、`role/SpringAiAdaptiveAgentModelGateway.java` 接线;
- `observability/AdaptiveAgentTelemetry.java`:prompt 版本 tag。

### 验收标准

- 一场完整面试后,能按 sessionId 查出每轮每次模型调用的渲染 prompt(或引用)、raw output、版本 hash、token;
- 黑匣子写入失败不影响面试主链路(有测试);
- 敏感原文不出现在任何对外接口与指标标签中。

### 测试要求

- 写入服务落库/失败不阻断主链路;
- 版本 hash 与模板内容一致的契约测试;
- 端到端:一场桩模型面试后黑匣子行数与调用点一一对应。

---

## IM-4 黄金场景离线评测 harness(P1,1~2 周,依赖 IM-3)

### 背景与证据

评审 §三.1:无评测集,文档承诺的黄金场景回归(20 号文 M0.5、01 号文 §487 评级一致性)未落地;`assessment` 的 few-shot 示例(`prompts/adaptive-agent-assessment-agents.md`)和回填服务(`assessment/backfill/AssessmentBackfillService`)是现成积木。

### 方案

1. **评测资产**:新增 `app/src/test/resources/eval/`(或独立目录),20~50 场标注会话:固定问题 + 各档(L0~L4)标注回答 + 期望评级区间 + 期望证据可提取性。首批可从 few-shot 示例改造。
2. **执行器**:离线 runner(JUnit 独立 task 或 Gradle task,不进 `:app:test` 主门禁),对黄金集跑 assessor,产出:评级一致率、证据校验通过率、probeGaps 合法率;按 prompt 版本 hash(IM-3)分组对比。
3. **CI 门禁**:评级一致率 ≥ 阈值(初始建议 80%,随数据积累上调)才允许合并 prompt/评估链路改动;结果报告落 `build/reports/eval/`。
4. 多模型对比:runner 参数化 provider,同一黄金集对比评级分布(不做门禁,只做报表)。

### 验收标准

- `./gradlew :app:eval`(或等价入口)可复现地产出上述三项指标与报告;
- 改坏评估 prompt(例如删掉 anchor 要求)时,评测指标显著下降并可被门禁拦截(用一次实验验证);
- 评测不依赖外部网络时可跑桩模式(指标退化为契约校验),接真实 provider 时跑完整模式。

### 测试要求

- runner 自身的单测(标注解析、指标计算);
- 黄金集 schema 校验测试(防标注文件腐化)。

---

## IM-5 答题异步化 + 进度推送(P1,1~2 周)

### 背景与证据

评审 §1.2/§三.4:前端 `MODEL_CALL_TIMEOUT_MS = 45_000`(`frontend/src/api/adaptiveInterview.ts:14`),后端维度完成轮串行最坏 ~90s;断链后重试报轮次不一致;前端 2s 轮询判题(`AdaptiveInterviewPage.tsx:122,140`)。

### 方案

1. `POST /{sessionId}/answers` 改为立即返回 `202 ACCEPTED`(带当前轮次),后端异步执行"评估→简报/声明→决策→落库"链路;并发与一致性仍由既有 expectedTurn + @Version 保证。
2. 进度通道:优先 SSE(`SseEmitter`),失败降级前端轮询 `GET /{sessionId}`(已存在,返回全量状态)。事件粒度:ASSESSING / DECIDING / DONE / FAILED。
3. 异步执行器:显式配置 `ThreadPoolExecutor`(项目规范禁止 `Executors.newXxx`),配合 IM-11 的并发准入;LLM 调用仍在事务外,落库仍是短事务——本项只改触发方式,不改事务语义。
4. 失败语义:异步链路失败时状态不推进(既有语义),会话可查到一个"上一轮失败可重试"的状态;前端据此允许原样重试同 turnIndex。

### 改动点

- `api/AdaptiveInterviewController.java`(202 + SSE 端点);
- `application/AdaptiveInterviewApplicationService.java`(submitAnswer 拆出异步入口);
- `frontend/src/api/adaptiveInterview.ts`、`frontend/src/pages/AdaptiveInterviewPage.tsx`(提交后订阅进度,移除长超时同步等待);
- 配置:异步线程池、SSE 超时。

### 验收标准

- 提交回答的 HTTP 响应 < 1s;维度完成轮也能在 UI 上看到阶段进度;
- 前端断网/刷新后用 sessionId 恢复,进度与结果不错乱;
- 并发双提交仍只有一个推进(既有并发测试改异步姿势后保持绿)。

### 测试要求

- 异步入口的完成/失败/超时三条路径;
- SSE 断连与重连;
- 前端构建通过(`cd frontend && pnpm run build`)。

---

## IM-6 中危修复包(P1,2~3 天)

### 内容与改动点

1. **ToolResult 两阶段窗口**：删除 reserve/complete 状态机；SandboxExecution 终态、唯一 Evidence 和 `consumedAt` 在一个事务提交，PENDING 重投复用同一 executionId。
2. **限流头伪造**(评审 §2.2-6):`common/aspect/RateLimitAspect.java:215-242` 增加 `trusted-proxies` 配置,仅在可信代理后取 XFF;`X-User-Id` 只认认证过滤器的 request attribute,删除 header 回退;补伪造头绕过测试。
3. **代码分析 Job 状态机守卫**(评审 §2.2-7):`codeanalysis/job/AnalysisJobEntity.java:73-95` complete/markRunning/fail/timeout 加合法迁移校验;FAILED/TIMED_OUT 的 job 允许重新投递(新建或重置重投);补迟到回调翻转测试。
4. **pending_rejudge 重判回填**(评审 §2.2-8):补消费方——调度器扫描 `pendingRejudge=true` 且超稳定期的执行重新投递判题(有上限与退避);或若决定不做,在 11 号文降级该承诺。二选一,不留半成品。

### 验收标准

- 四项各自有失败场景测试;既有 491 测试基线保持绿。

---

## IM-7 租户链路收敛(P2,依赖业务决策)

评审 §2.1:MCP 能 `createForTenant` 创建会话,但所有写路径 `findByIdAndTenantIdIsNull`,租户会话无法推进。二选一:

- **方案 A(打通)**：所有领域事实写入和只读 Tool scope 查询都按租户 ownership；删除旧 reserveToolResultEvent/toolResultFollowUps 路径。补租户端到端测试。
- **方案 B(移除)**:`createForTenant` 下线,MCP 暂不暴露创建;文档标注租户链路待企业侧立项。约 0.5 天。

禁止维持现状(半链路是最差状态)。无论选哪个,先用一个测试钉住选定行为。

---

## IM-8 鉴权与候选人身份(P0,依赖业务决策)

评审 §1.2:`candidateId` 客户端裸传,画像/报告/会话知道 ID 即可读(`api/AdaptiveInterviewController.java:111-119`)。这是上线阻断项,但方案依赖平台登录体系决策(是否接入既有用户体系、MCP 租户与候选人身份的关系)。

Spec 只锁验收,不锁方案:

- 所有 `/api/adaptive-agent-interviews/**` 读路径必须验证调用者对该 candidateId/会话的归属;
- MCP 侧已有租户凭证与 scope,候选人维度需补齐等价语义;
- 未认证请求一律 401/404(不泄露存在性),有审计。

---

## IM-9 数据合规(P1,3~5 天,依赖 IM-8)

评审 §三.7:简历原文、回答原文、记忆、画像均无保留期与删除接口;仅代码分析产物有 30 天保留期(`codeanalysis/repo/CodeAnalysisRetentionScheduler`)。

### 方案

1. `DELETE /api/adaptive-agent-interviews/candidates/{candidateId}`（或管理侧入口）：按外键级联删除 Session/Turn/Assessment/Evidence/Plan/Working Memory Snapshot/Episode/SemanticContribution/SandboxExecution 和合规审计记录；不再把只读 Tool 调用当业务数据。
2. 进行中会话 TTL:超过 N 天(建议 7 天)未推进的 IN_PROGRESS 会话定时回收(状态置 EXPIRED 或删除,与产品确认)。
3. 删除动作写审计(谁、何时、删了什么范围),不存原文。

### 验收标准

- 删除后该候选人在所有表(含黑匣子)无残留,有逐表断言的集成测试;
- 删除接口有鉴权与审计;TTL 回收有调度测试。

---

## IM-10 部署配套(P2,1~2 天)

- `docker-compose.yml` / `docker-compose.dev.yml` 增加 sandboxd 与代码分析 worker 服务定义(镜像、环境变量、依赖顺序),`SandboxdClient` 的 `http://sandboxd:8090` 默认指向才能成立;
- 补一份"本地全链路启动"验证清单(README 或 SETUP 文档):从 compose up 到完成一场含算法题的面试。

---

## IM-11 上下文压缩 + 成本护栏(P2,1 周+,依赖 IM-3)

评审 §三.3/5:JD/简历/项目上下文每轮全文重复注入,12k 预算 fail-fast;无会话级成本。

1. **压缩**：ContextAssembler 可对大输入建立显式导航摘要，但 AgentContext 必须包含全部合法 Coverage/Gap 的索引，不得因 Java 预选“当前维度”隐藏其他合法 Target。报告/证据仍引用原文。
2. **会话级成本护栏**:`agent_sessions` 加累计 token 列(从 IM-3 黑匣子聚合),超阈值拒绝新轮次并返回明确错误;Redis 按 candidateId 日配额(复用现有限流设施)。
3. **并发准入**:照抄 voice 模块 Semaphore 模式,配 `maxConcurrentSessions`。

### 验收标准

- 长 JD(>12k token 等价)面试不再被 fail-fast 打挂;
- 单会话成本可在黑匣子/会话行上直接读出;超配额会话被拒绝且有指标。

---

## IM-12 练习闭环(P2,3~5 天)

评审 §1.2:`PracticeStatus.COMPLETED` 无流转入口,练习推荐是断头路。

1. 练习完成入口(前端练习记录页 + 后端状态流转),`practice_records` 状态机补全;
2. 复测时把用户 scope 内的 Semantic planning view 交给 Planner，优先级由模型决定，Java 不扩大 scope；
3. 验收：面试 → 推荐 → 练习 → 完成 → 复测计划全链路可走通，有端到端测试。

---

## 4. 非目标(本 spec 明确不做)

- 企业侧控制台/能力模型权重配置(13 号文已声明另行立项);
- 候选人侧反作弊(评审 §1.4,蓝图未覆盖,需先立项);
- 报告措辞 Agent(10 号文标注 M5 后可选);
- git URL 形式的仓库授权与 worker SSRF 加固(评审 §2.1-4 遗留,需与 worker 协议一起设计);
- 旧 MVP(agent-loop)已于 2026-08-22 删除，本条作废。
