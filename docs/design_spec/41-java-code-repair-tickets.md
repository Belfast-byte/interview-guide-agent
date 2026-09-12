# Java 业务代码改错题实施 Tickets

依据：[40 号规格](40-java-code-repair-spec.md)，工具依赖遵循 [39 号规格](39-interview-agent-tools-spec.md)。

实施分支：`codex/java-code-repair`。实施前工作区基线：`a6ca101`。
基线提交保存既有工作，不代表已通过本次功能验收。

## Tickets 与依赖

| Ticket | 范围 | 依赖 | 状态 | 验收出口 |
| --- | --- | --- | --- | --- |
| CR-00 | 基线、调用链审计、任务拆分 | 无 | DONE | 记录真实入口、消费者及基线检查结果 |
| CR-01 | 题型联合契约与原始任务事实 | CR-00 | DONE | 首题和后续 ASK 使用相同字段组合；根任务冻结；严格校验引用 |
| CR-02 | 八个增量数据库字段与约束 | CR-01 | DONE | 新迁移保留旧沙箱及历史数据；同场引用与题型约束通过 PostgreSQL 验证 |
| CR-03 | 代码答案领取、幂等、失败恢复 | CR-01、CR-02 | DONE | 仅代码也可提交；同 payload 重试复用；不同 payload 冲突；旧执行者隔离 |
| CR-04 | Planner、决策上下文、材料及历史读取工具 | CR-01、CR-03 | DONE | 首题和后续题可生成；按需读取；当前未提交审阅直接传递；工具来源保存与恢复 |
| CR-05 | 逐项审阅与 SourceQuote 定位 | CR-02、CR-03 | DONE | 每项恰好一次；等价解法及无法判断有明确语义；UTF-16 精确定位及 gap 关闭同源 |
| CR-06 | 公开 DTO、SSE、Episode 与报告 | CR-04、CR-05 | DONE | 私有参考不泄漏；评估中隐藏详细反馈；历史提交、评级、证据同源 |
| CR-07 | Java 编辑器、diff、草稿与模式交互 | CR-06 | DONE | 练习新轮修订；评估文字追问；刷新和失败保留草稿；历史只读 |
| CR-08 | 集成验收与真实模型抽样 | CR-07 | IN_PROGRESS | 并发恢复、数据库迁移、公开权限、页面构建与交互检查；如实记录真实模型验证结果 |

## 实施边界

- CR-01 与 CR-02 分开提交协议和迁移；结构清理另行提交，不改已执行迁移。
- 不新增代码执行、沙箱、运行评分、专属次数上限或后台记忆任务。
- 原任务完整事实只存原始 Turn；修订通过同场根引用读取，不覆盖旧答案。
- 39 号工具仅实施本功能需要的依赖，并记录其实际接入状态。
- 所有确定性后端测试命令使用 `timeout 60s`；真实模型质量验证不能以模拟或单测替代。
- 每个 ticket 完成时补充提交、验证命令与结果；未验证项目不得标记完成。

## 调用链与验证记录

- 基线编译：`timeout 60s ./gradlew :app:compileJava :app:compileTestJava --no-daemon`，21 秒通过。首次沙箱执行因用户 Gradle 缓存只读失败，授权执行后成功；尚未运行基线单测。
- 创建：`InitialQuestionProposal → AgentDecision.Ask → AdaptiveCreationTransactionService → AdaptiveAgentTurnEntity`。
- 回答：HTTP / MCP → `CandidateAnswer → AdaptiveAnswerClaimService → Assessor / Loop → AdaptiveAnswerTransactionService`。
- 恢复：`AdaptiveInterviewPersistenceService` 读取正式答案，实体负责执行令牌、租约及回答状态。
- 基线候选人响应仅投影文字；现已贯通公开任务、正式代码、模式反馈、Episode、报告和 MCP，原始评估参考不进入候选人 DTO。
- 原模型 schema 由 Spring AI BeanOutputConverter 从 record 生成，修改 DTO 同时修改提示词和调用方测试。

## 当前验收记录

- CR-01/02：事实实现 `fbc2428`；迁移独立提交 `4a81b95`。原任务 JSON 往返、根引用、代码原文和旧文字题回归通过。
- CR-02：`scripts/verification/verify_code_repair_migration.py` 使用 `/tmp` 原生 PostgreSQL 12.22 临时集群，19 项真实增量约束检查通过；不代表完整历史 Flyway 链或生产版本演练。
- CR-03：`CodeRepairTransactionTest` 6 项通过，使用真实 JPA 服务和不同 token 双线程领取/提交；包括失败与过期恢复、不同 payload 冲突、练习修订、评估边界、跨场/链引用拒绝。
- CR-05：逐项审阅、UTF-16 精确引用、gap 关闭 locator、历史空 locator 及练习已公开前文验证通过；CODE → TEXT → CODE 的文字反馈也进入下一次练习评分上下文。
- CR-06：公共集成检查 21 秒通过，覆盖 API/Episode 模式权限、原始参考隔离、旧文字 gap 被后续代码关闭时的延迟公开、报告证据和严格解析错误隐私；MCP/HTTP 定向 31 项通过。
- CR-04：四个工具已注册；严格参数与 owner 校验、按指定轮次读取、当前请求内审阅投影、本题采用来源保存及跨请求恢复均已接通。真实抽样发现的 schema 可空字段问题已通过声明注解修复，仍拒绝未知字段、额外 JSON 和无效引用。
- CR-07：最终 31 项前端交互测试通过，生产构建通过。原始材料、未提交草稿与正式提交明确区分；已公开文字追问反馈在练习 Episode 中保留，评估报告反馈不会冒充提前提示。
- 所有后端验证命令均带 `timeout 60s`；联合回归结果和提交记录见下方。

## CR-08 尚未完成的真实验证

[完整报告与合成样例](../../scripts/verification/code-repair-validation.md)保留成功和失败响应。三个真实模型评估通过 schema、实际 Java 领域校验和原文引用检查，能区分完整修复、单实例锁错误和遗漏拒绝。首题通过结构与领域校验，但人工发现 JPQL 字段错误、依赖和隔离前提不清；已修正提示词；用户批准后完成一次真实复测，结构及真实领域校验通过，但人工审阅仍发现参数前提和事务参与条件缺口，质量尚未通过。

此前向 DeepSeek 外发的审批阻断已由用户明确批准解除；随后仅执行一次获准请求，39.90 秒、2415 输出 tokens。原始响应及人工审阅结果已归档到验证报告。此项仍不标记 DONE，原因是质量和应用端到端验证缺口。

模型来自环境 provider 的 Anthropic 兼容接口，未走应用配置的 Spring AI 完整链路。首题 81.17 秒、评估 23.04～46.99 秒，部分超过 application.yml 的 Planner 60 秒 / Assessment 30 秒默认值。没有更改业务超时，真实应用端到端质量及延迟尚未验收。

初次 PostgreSQL 验证只覆盖 V20261007 增量；Docker 启动后已在 PostgreSQL 16.14 补齐完整 Flyway/JPA 测试：空库 57 个迁移、旧基线 46+11 个迁移均通过。修复了测试随机 schema 与历史 public 引用不一致的问题，历史迁移未修改。前端构建存在原有 Browserslist 数据与 CSS `:where` 警告，不影响本次构建通过。

## 最终本地回归与提交

- 后端：`timeout 60s ./gradlew :app:test --tests 'interview.guide.modules.interview.agent.adaptive.*' --tests 'interview.guide.common.ai.StructuredOutputInvokerTest' --no-daemon`，32 秒；338 项，337 通过、0 失败、1 项完整 PostgreSQL 环境测试跳过。
- 前端：`cd frontend && pnpm run test:code-repair`，31 项通过；`pnpm run build` 通过。
- 测试结构拆分后：`timeout 60s ./gradlew :app:test --tests '*AdaptiveAnswerProgressionTest' --tests '*AdaptiveAnswerReportProgressionTest' --no-daemon`，20 秒，7 项通过。
- `git diff --check`、新增验证脚本语法和文档链接检查通过；本次改动后的 Java/TypeScript/Python 源文件均不超过 300 行。
- `eeaf361`：模型契约、逐项审阅、精确证据、内部工具、API/MCP 与读取权限。
- `8031db3`：Java 编辑器、diff、草稿、练习/评估交互、历史与报告。
- `5beda83`：答案推进测试按职责拆分，独立于行为提交。

CR-00～CR-07 已完成。CR-08 保留进行中，任务 CSV 同步保留；外发审批已解除、一次复测已执行，不能将结构通过当作人工质量或应用端到端验收通过。

### Docker 启动后复测

PostgreSQL 16.14 上的完整迁移/JPA 定向测试 16.92 秒通过。随后带数据库连接执行上述联合回归，**338 项通过，0 失败、0 跳过，33.59 秒**，替代此前“337 通过、1 跳过”的最新结果。临时容器和数据卷已清理，开发库未写入。

修正仅涉及迁移测试的隔离方式：历史 SQL 使用 public schema，因此改用独立临时数据库验证空库与旧基线升级；已执行迁移不变。CR-08 的数据库环境缺口已关闭，题目质量和应用模型端到端验证缺口仍保留。
