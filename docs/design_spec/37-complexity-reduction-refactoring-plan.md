# 复杂度削减重构指南

更新：2026-09-06。依据用户提供的独立架构审计，在当时已有未提交改动的工作区上实施。审计是候选问题清单；每批必须重新核对生产消费者，不以旧文档、类名或测试数量证明机制必要。

记忆的新目标已按用户要求改为 [38 号业务复用规格](./38-memory-business-reuse-spec.md)，本批已实现业务复用。下文先前批次及其整理、观察恢复边界是历史记录，不是继续保留这些机制的依据。

## 最小目标与保留边界

2026-09-12 创建与记忆校验修正：首题的 `availableEpisodeRefs` 从当前 owner 的规划历史贯穿应用服务与创建校验，不从模型声明构造可信来源；合法历史可以采用，未提供的引用仍在落库前拒绝。WorkingMemory 区分可空的单值字段与不可含空元素的引用数组，Observation 和正反 Evidence 数组中的 `null` 反馈为校验拒绝，由现有 Loop 重新决策，不在持久化阶段静默过滤。回归覆盖应用入口到创建校验的历史传递、伪造引用拒绝，以及非法记忆不进入下一步上下文和最终快照。

2026-09-12 报告评级修正：同一维度按最大 `turnIndex` 选取最近一次正式评估，等级、置信度、理由和证据来自同一条记录，未解决 gap 单独展示；不再取历史最高评级或预算耗尽时的评级。预算 Observation 继续用于出题调整，移除 `targetBudgetExhausted` / `budgetExhaustedFinal` 的评估写入与报告读取链及无消费者的最高分选择器。旧 `agent_assessments.budget_exhausted_final` 列和历史值保留，现有迁移已定义 `NOT NULL DEFAULT FALSE`，新写入省略该列；没有改写迁移或删除历史数据。

使用按业务组织的 Spring Boot 单体：账号与模型配置、面试、题库与量规、报告与历史、学习记忆、外部执行。语音和传统问答保留独立入口。直接使用具体业务服务和 Spring Data Repository，只保留有真实事务或外部协议职责的边界。

- 创建：边界校验 → 事务外生成计划及首题 → 校验模型结果 → 一个短事务提交 → 返回快照。
- 回答：短事务接受答案并领取租约 → 事务外评估和决策 → 短事务验证归属、答案和执行令牌 → 提交正式事实及下一题。
- 记忆：数据库待处理记录 → 短事务领取 → 事务外整理 → 验证来源仍有效 → 短事务保存观察版本。
- 查询：按资源归属读取数据库事实，计算报告、覆盖率和当前能力视图，再返回对外 DTO。

数据库中的已接受答案、已发布题目、评估与证据、量规快照、观察版本和恢复状态是正式记录。保留回答幂等、行锁、租约、旧执行者隔离、独立外部入口校验和后台资源隔离。历史推荐与旧贡献统计仍有读取者，不随无调用生成链一起删除。

## 第一批：已实施

本批只处理无调用实现、无收益写入与两组纯转发层，另单独修正一处错误测试基线。没有数据库迁移、SSE 协议修改或评分规则修改；没有部署。下面的“无调用”指仓库内生产代码引用核对结果，不代表检查过外部调用日志。

### 0. 完成数测试基线

`AdaptiveInterviewResponseTest` 中首题已发布但尚未回答，完成轮数应为 0。将旧的 1 断言改为 0，保持生产统计实现不变。对应测试先独立运行通过。

### 1. 删除不可达子图

| 子图 | 删除内容 | 保留内容 |
| --- | --- | --- |
| 维度简报 | `memory/brief` 六个实现/模型、`DimensionBrief`、专属提示词与配置、`PlannedInterview.dimensionBriefs`、专属测试 | 原计划、覆盖率和面试历史；当前生产读取本来只传空 brief 列表 |
| 旧 coaching 与召回 | `PracticeCoachingMemoryAssembler` 及其上下文/请求/owner 查询、`JpaEpisodeRecallSource`、旧召回视图和接口、`QuestionNoveltyPolicy`/`QuestionNoveltyDecision` | `PracticeMemoryService`、观察版本、新 `MemoryRecallTool`、数据库题目历史与旧统计页面 |
| 旧推荐生成 | `PracticeRecommendationService`、专属 facts 模型及查询实现、`QuestionBankSearchSource`/`QuestionBankSemanticSearch`/`QuestionBankQuestion` | `PracticeRecommendation`、`PracticeStatus`、历史推荐实体/仓储及报告读取；量规搜索、题库索引和知识库能力 |

删除专门维持这些死实现的测试。两个混合场景测试只移除旧召回/去重部分，保留仍使用的 scope 规划与贡献聚合断言。同步移除旧召回独占的无分页曝光查询和 `findByTurnIdIn`。

### 2. 删除无消费者的曝光向量写入

原链路为：创建/回答事务 → `QuestionExposurePersistence.save` → 发布 `QuestionExposurePublished` → 提交后 `QuestionExposureIndexListener` → `QuestionExposureVectorStore` → embedding。

唯一相似度读取链依赖上述旧召回子图；当前 `JpaMemoryEvidenceService` 从数据库读取曝光记录。因此删除事件、监听器、向量适配器及相似度接口，`save` 只保存数据库曝光记录。

兼容边界：历史表 `agent_question_exposures.embedding_document_id` 仍有 `NOT NULL` 和唯一约束，本批保留原身份值生成及实体字段，不触碰历史迁移，不清理已有向量或曝光数据。保留身份字段不代表仍执行向量写入。删列/清理历史向量须另做数据核对和增量迁移。

### 3. 删除传统问答会话缓存

生产入口包括 `InterviewController` 和知识库面试创建。审计时所有查询已经走数据库，但创建、恢复、报告仍更新 Redis；恢复又经 `CachedSession` 做一次序列化/反序列化。

本批：

- 删除 `InterviewSessionCache`、`CachedSession`、缓存写入与无实际提交后语义的 `cacheAfterCommit`。
- 直接由 `InterviewSessionEntity`、问题 JSON 和已保存答案组装原 `InterviewSessionDTO`。
- 保留已保存答案覆盖题目视图的行为；题目量规、来源、参考答案等字段完整保留。
- 本人会话读取直接使用带 candidate 条件的查询；缺失/越权不加载答案。
- 删除已无作用的局部答案副本和纯转发的 `persistSubmittedAnswer`。
- 保留 `InterviewPersistenceService.persistAnswer` 的锁、进度校验与原子提交；保留 Redis 评估消息队列及数据库投递恢复标记。

旧会话缓存键不主动清除，按原 TTL 过期。缓存删除不意味着异步评估投递不再依赖 Redis。

### 4. 合并两组纯转发 Bean

- 删除 `AdaptiveCreationRepositories`，创建事务直接注入原 Session/Plan/Turn Repository。
- 删除 `AdaptiveAnswerCoreRepositories`，回答事务直接调用原锁查询和 `saveAndFlush`。
- 保留 `@Transactional` 所在服务、原锁查询、原执行令牌检查、保存顺序和 flush 时点。
- 同步修改 JPA 测试装配。`AdaptiveAssessmentRepositories` 有缺口处理逻辑，未按纯转发层删除。其余 Episode 依赖分组待下一批核对。

## 验证记录

- 删除子图后：`:app:compileJava :app:compileTestJava` 通过。
- 定向回归：27 项，全部通过，无跳过。包含传统会话/持久化、创建事务、回答推进、报告与当前记忆召回。
- 新增传统会话测试：答案覆盖与题目字段保真、未完成会话恢复、归属查询失败、答案提交失败不投递、最终答案提交后投递、报告使用数据库答案与 Provider。
- 全量后端：596 项，546 通过、50 跳过、0 失败（`./gradlew :app:test --no-daemon`）。随后将创建测试切换到生产 `create` 入口并新增无外层事务的失败回滚测试，补充定向 2 项均通过。
- 本批未修改前端。JPA 回归使用测试数据库，不能据此声称真实 PostgreSQL 并发、生产装配或真实模型调用已验收。

## 第二批：内嵌模型、移除包装与上下文修复

依据 `detect.txt` 再次追踪使用方，在本轮开始快照上新增以下修改。结构调整、行为修正和模型输入调整分别列明；没有数据库迁移或部署，未提交包含前序改动的混合工作区。

### 结构调整

- 15 个独立 record 移至其所属边界：HTTP 请求/响应 5 个、MCP 工具参数/响应 4 个、应用服务输入/结果 4 个、报告工具结果 1 个、Episode tag 投影 1 个。公开字段、枚举值及校验规则保留；仅应用服务私有输入收窄为 private。
- 删除 `EpisodeEnrichmentServiceDependencies`、`EpisodeEnrichmentGeneratorDependencies`、`EpisodeEnrichmentRepositories`，原消费者直接注入对应依赖。上下文读取仍为只读短事务，模型调用仍在领取/提交事务之间执行，完成/失败的令牌校验保留。
- `AdaptiveInterviewSession.apply` 直接返回新会话，删除没有生产消费者的 `SessionTransition.appliedAction` 副本，以及未调用的 `advanceAfterAnswer`。
- 删除旧 `AgentAction`/`ToolCallAction` 层；当前模型工具调用继续使用 `AgentDecision.CallReadTools`。`RuntimeDeadline` 替换为同语义的局部纳秒截止时间，保留截止时间执行器。
- 合计减少 22 个独立生产 Java 文件，其中 15 个是内嵌迁移，不计为功能删除。

### 行为及模型输入修正

- 回答上下文用完整构造器复制轮次，替换回答正文时保留 `adoptedRubrics`、`answerStatus`、`answerError` 及原有全部字段，不改变已持久化快照。
- `DecisionModelContext` 删除重复的顶层 `workingMemory`，每次模型调用将当前记忆放入 `agentContext.workingMemory`。校验失败后的再决策保留最新有效记忆；事实、工具权限和 Observation 仍保留。此项是模型输入形状变化，需要与纯结构调整区分。

### 已确认的页面行为

用户明确选择“暂时隐藏代码工作台，后续接通判题再恢复”。移除面试页代码工作台及其独占状态、输入处理、结果轮询和样式组件，同时删除始终为 false 的创建流引用。保留文字回答、已接受答案重试、断流后恢复及历史 CREATED 快照轮询。后端代码执行接口与历史代码结果读取保留；SSE delta 兼容处理留待协议批次。

### 第二批验证

- 全量后端：598 项，548 通过、50 跳过、0 失败（`./gradlew :app:test --no-daemon`）。包含上下文元数据保真和循环内当前记忆唯一性回归。
- HTTP/MCP 与上下文补充定向回归：22 项全部通过，0 跳过。覆盖公开 JSON 字段、嵌套请求级联校验、MCP 工具扫描和租户权限。
- 前端：面试页 7 项答题恢复测试通过；`pnpm run build` 通过。本机通过 `corepack pnpm` 使用项目指定版本。构建仍报告滚动条 CSS 的空 `:where()` 警告及 Browserslist 数据过旧，本批未改相关样式规则。
- 已核对被删除包装类型无代码残留引用。本轮新增内容无空白错误；`git diff --check` 剩余两处为本轮基线已存在的 `CLAUDE.md` 和 `.claude/rules/interview-agent.md` 末尾空行。
- 测试中的数据库与模型替身不能代替真实 PostgreSQL 并发、真实模型效果和外部消费者验收；没有执行数据库迁移或部署。

## 第三批：场景记忆消融实验（2026-09-06）

用户要求按个人项目的业务价值删除无收益机制。本批以回答事实写入、后台整理、有效观察召回、撤销与重建为验收对象；没有修改提示词、数据库结构、HTTP 字段或旧统计页面。

### 消融与对照结果

| 实验 | 改动 | 结果与决定 |
| --- | --- | --- |
| 基线 | 运行记忆、记忆持久化、enrichment 与召回测试 | 52 项通过，0 跳过 |
| A：结构消融 | 统一创建入口；收回单子类生成器基类；内嵌五个边界专属小类型 | 原 52 项全部通过；保留删除结果 |
| B：校验消融与恢复修正 | 删除独立状态机、内部创建参数校验、召回工具重复 catch；允许依赖失效的最新观察重建 | 扩展定向测试 53 项通过；包含完整的上游重建、下游重新生成和能力恢复 |
| C：必要性对照 | 临时删除完成/失败提交的 `ownsEnrichment` 检查 | 过期租约提交、旧 worker 提交两个测试均失败；已恢复这两处检查 |

实验 C 的临时文件修改由脚本 `finally` 恢复；最终工作区不包含关闭执行隔离的代码。测试均使用 `timeout 60s`。这是代码路径与数据一致性实验，未调用真实模型，不代表模型效果或线上并发性能比较。

最终全量后端：595 项，545 通过、50 跳过、0 失败，耗时 43 秒；`git diff --check` 通过。删除两份只维持内部构造器/状态机的专属测试（6 项），新增两项观察重建回归；其余测试保留业务断言。

### 删除内容及边界

- 生产与测试统一到 `EpisodeFactCreation`，删除 `AgentEpisodeFactCreation` 及 Ownership/Source/Evaluation 三组参数包装、Entity 的第二套初始化逻辑。同一回答事务已经建立的创建参数约束不再逐层重复校验。
- 删除只有一个子类的 `AbstractSpringAiMemoryGenerator` 和 12 字段 `GenerationSpec`。具体生成器直接渲染提示词并调用既有模型适配器；输入预算、deadline、遥测和失败异常保留。
- `EpisodeEvidenceFact`、`EpisodeProbeGapFact`、`EpisodeSourceFacts` 内嵌到 enrichment 输入；`EpisodeTagProposal` 内嵌到模型输出；`ValidatedEpisodeTag` 内嵌到标签校验器。JSON 字段与枚举语义不变。
- 删除 `EpisodeEnrichmentState` 及其专属测试。状态直接写实体，完成/失败仍由持有行锁的提交服务检查执行令牌、租约和 PROCESSING 状态。领取、显式重试与恢复仍由持久化/恢复集成测试覆盖。
- 删除 `EpisodeFactCreation` 的内部参数验证及仅断言旧纠正引用参数的测试；保留数据库唯一约束、外部来源归属和模型证据引用验证。
- 删除 Claim 中没有消费者的整份 Episode 副本。worker 继续在领取后从数据库加载权威输入。
- 删除 `MemoryRecallTool` 吞掉所有运行时异常的 catch。异常交给既有 `ToolGateway` 记录根因并做协议转换，不再丢失数据库或代码错误的原因。

共删除 8 个生产 Java 文件，未新增生产文件；`memory/episode` 从 35 个文件降到 28 个。生产代码净减少 268 行；五个小类型属于内嵌搬迁，不能计为五项业务功能删除。

### 行为修正与保留项

回归先复现了上游观察版本替换后下游重建被 `requireCurrent` 拒绝的问题。重建改为验证归属，在 Episode 行锁内检查请求版本仍为最新且未撤销；依赖失效不再阻止从原始回答生成新观察。旧版本和撤销记录仍不能重建。回归覆盖下游新版本引用更新后的上游、重新加入有效观察以及恢复能力判断；此处允许显式重建，不宣称自动级联重建。

仍保留数据库事实、历史观察版本、模型证据校验、执行令牌与租约、短事务及独立后台线程。旧贡献统计与标签仍有页面读取者，本批没有删除或修正其统计口径；生产练习贡献固定 UNRESOLVED 的已知问题仍属于后续统计行为修正范围。

## 第四批：Episode 按业务分包及内嵌（2026-09-06）

用户确认范围为场景记忆 `episode`。在第三批未提交结果上继续整理；本批不改变记忆生成、统计、撤销或重建行为。

### 目录与类型归属

```text
memory/episode/
  EpisodeFact、EpisodeFactEntity、EpisodeFactPersistence
  EpisodeFactRepository、CandidateMemoryEpisodeQueryRepository
  enrichment/  投递、生成、短事务提交、恢复及其持久化实现
  tag/         标签值、来源、校验与持久化
  exposure/    题目身份、曝光事实与持久化
```

- 从 application 和 persistence 移入本业务所属的实现，相关测试同步迁移。Entity、Repository、Spring 服务仍为顶层类型，表名、实体名和 Bean 简名不变。
- 另外内嵌 18 个类型：Episode 创建参数与三个枚举；模型输出、完成参数、恢复任务、提交后事件；标签分类与两组词表、来源类型；题目身份与发布参数；两个持久化输入、两个查询投影。
- 第三批已经内嵌的证据、缺口、来源集合、标签建议及已校验标签随所属类型移动；不重复计入本批 18 个。
- 本业务原先分散在三个技术目录的 48 个生产 Java 文件收拢为 30 个；不是仅比较旧 episode 目录的 28 个文件。所有整理后的 Java 文件均不超过 300 行。
- 新增包内 `README.md`，列明入口、后台执行顺序及内嵌归属；工作记忆、能力观察服务与旧统计继续保留原位置。

### 验证与架构规则

生产与测试源码编译通过。第一次全量回归暴露原架构断言与新业务分包冲突：它假定所有 memory 包都不得依赖 persistence。该断言现在仅排除按业务聚合的 episode，其他仍按技术层分包的能力和领域内核隔离断言保持不变；没有为旧断言新增转发层。全量命令达到 60 秒硬超时，超时后的 XML 可能残留旧报告，因此不作为完整验收依据；改按源码中的 140 个顶层测试类分批验证，不提高超时限制。架构测试单独运行通过（8 秒）。

曾尝试额外提出全局缩短类型名，但自动审批审查拒绝了该操作；该脚本未执行，保留明确的原类型名，已完成的包迁移与内嵌不受影响。

最初分批误用了报告中的展示名，覆盖核对发现遗漏，因此不计为完整回归；最终按源码包名和类名重新划分为每批 35 个类，并从成功命令的 testcase@classname 核对实际覆盖。`git diff --check` 通过，另检查了新增目录文件的尾随空白。

最终四批均正常成功：153 项（18 秒）、130 项（20 秒）、136 项（26 秒）、176 项（21 秒）。合计 595 项、545 通过、50 跳过、0 失败；已核对完整覆盖源码中的 140 个顶层测试类，无遗漏、无跨批重复。

## 第五批：删除记忆指纹（2026-09-06）

按用户明确要求删除场景记忆的指纹与 SHA-256，不替换为其他摘要算法。

- 删除 `QuestionFingerprint`、`QuestionIdentityFactory`、题目身份/实体中的两个指纹字段，以及独立验证中的题目指纹比对。题目历史仍作为模型输入；题目身份直接从 Target 的业务字段构造。
- 删除 enrichment 提交时重新读取输入并比较摘要的路径，以及其专属 ContextSource Provider 依赖。提交使用 worker 已读取的输入，仍核对执行令牌和观察引用归属。
- 删除观察的 input fingerprint、机会哈希和 skill reference hash。新能力使用起始 Episode ID，模型明确关联的观察沿用来源能力 ID；不再依靠相同目标文案的哈希自动合并能力。独立机会在每个能力组内按 session ID 计数，版本直接使用观察 ID 列表。
- 保留已存能力键作为历史不透明标识，不重新散列或迁移历史引用；其他模块的 SHA-256 工具不属于本次删除范围。
- 新增 V20261005，仅解除旧 scenario/wording/input fingerprint 和 opportunity key 列的非空约束。新实体不映射这些列，也不写占位值；历史数据保留，旧 Flyway 文件未改写。迁移需随应用版本部署，本轮未操作实际数据库。
- 新增无指纹提交回归和 H2 迁移回归，验证提交不重新读取上下文、新记录可省略旧字段且历史值保留。

定向回归 59 项全部通过、0 跳过，耗时 23 秒，命令硬超时 60 秒；覆盖记忆、创建、回答推进与召回。`git diff --check` 通过，记忆生产 Java 范围内的指纹、SHA-256 和机会哈希引用均为零。迁移只在 H2 验证，未声称实际 PostgreSQL 迁移已执行。

## 后续批次与验收

| 顺序 | 工作 | 必须验证 |
| --- | --- | --- |
| 4 余项 | Episode 三组已合并；其余有规则门面逐项核对 | 领取、失败、重试与事务回滚；有规则的函数应迁移，不直接删除 |
| 5 余项 | 已内嵌 15 个 record；其余单 owner 模型逐项核对 | JSON 名称、枚举值、HTTP/MCP 契约不变 |
| 6 余项 | 字段复制与 SessionTransition 已修复；EpisodeEnrichmentState 已消融；其余目标包装待核对 | 字段保真、多轮推进、历史读取 |
| 7 | 删除同一可信边界内明确重复校验 | 外部非法引用、越权、重试和迟到执行仍被拒绝 |
| 8 已实施 | 决策输入只保留当前 WorkingMemory | 工具循环更新后的记忆唯一、最终提交一致；独立标记模型输入变化 |
| 9 已实施 | 按 38 号规格复用三层记忆 | 切换页面与召回消费者，复用回答评估和 L0～L4，删除专属整理与恢复；不把预算耗尽当问题解决 |
| 10 | 删除 Provider 的 null 依赖 legacy CRUD；提交后失效客户端缓存 | 正常依赖构造、并发更新和读取；保留 YAML 语音配置 |
| 11 | 收拢内部创建阶段并清理无生产者 SSE 增量；处理重复事件 | 当前客户端与外部 SSE 契约、历史 CREATED/FAILED 读取；协议调整独立批次 |
| 12 | 核对冗余列与临时负 ID 成本后决定迁移 | 历史数据和读取者调查、新 Flyway 迁移、PostgreSQL 验证 |

语音整实体缓存与写操作边界、异常协议转换等审计项仍需独立核对。代码工作台已按用户确认暂时隐藏，恢复以接通判题为前提。

## 变更纪律

结构清理、已知行为修复、模型输入变化、对外协议和数据库迁移分别组织变更。当前工作区已有前序未提交改动，不把它们计为本批成果，也不整体提交覆盖历史来源。完成每一批时更新本指南及 `AGENTS.md` 的对应提示，避免恢复已删除的旧机制。
