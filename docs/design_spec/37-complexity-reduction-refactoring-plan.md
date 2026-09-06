# 复杂度削减重构指南

更新：2026-09-06。依据用户提供的独立架构审计，在当时已有未提交改动的工作区上实施。审计是候选问题清单；每批必须重新核对生产消费者，不以旧文档、类名或测试数量证明机制必要。

## 最小目标与保留边界

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

## 后续批次与验收

| 顺序 | 工作 | 必须验证 |
| --- | --- | --- |
| 4 余项 | Episode 三组已合并；其余有规则门面逐项核对 | 领取、失败、重试与事务回滚；有规则的函数应迁移，不直接删除 |
| 5 余项 | 已内嵌 15 个 record；其余单 owner 模型逐项核对 | JSON 名称、枚举值、HTTP/MCP 契约不变 |
| 6 余项 | 字段复制与 SessionTransition 已修复；目标包装、EpisodeEnrichmentState 待核对 | 字段保真、多轮推进、历史读取 |
| 7 | 删除同一可信边界内明确重复校验 | 外部非法引用、越权、重试和迟到执行仍被拒绝 |
| 8 已实施 | 决策输入只保留当前 WorkingMemory | 工具循环更新后的记忆唯一、最终提交一致；独立标记模型输入变化 |
| 9 | 旧贡献统计逐步转为事实投影；完善记忆恢复 | 保留旧页面输出，校验撤销依赖与混合证据恢复；不把预算耗尽当问题解决 |
| 10 | 删除 Provider 的 null 依赖 legacy CRUD；提交后失效客户端缓存 | 正常依赖构造、并发更新和读取；保留 YAML 语音配置 |
| 11 | 收拢内部创建阶段并清理无生产者 SSE 增量；处理重复事件 | 当前客户端与外部 SSE 契约、历史 CREATED/FAILED 读取；协议调整独立批次 |
| 12 | 核对冗余列与临时负 ID 成本后决定迁移 | 历史数据和读取者调查、新 Flyway 迁移、PostgreSQL 验证 |

语音整实体缓存与写操作边界、异常协议转换等审计项仍需独立核对。代码工作台已按用户确认暂时隐藏，恢复以接通判题为前提。

## 变更纪律

结构清理、已知行为修复、模型输入变化、对外协议和数据库迁移分别组织变更。当前工作区已有前序未提交改动，不把它们计为本批成果，也不整体提交覆盖历史来源。完成每一批时更新本指南及 `AGENTS.md` 的对应提示，避免恢复已删除的旧机制。
