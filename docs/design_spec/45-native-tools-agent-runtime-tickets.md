# 原生工具迁移实施 tickets

日期：2026-09-21。依据：[44 号规格](./44-native-tools-agent-runtime-spec.md)。
分支：`codex/native-tools-runtime`。起点：`a484560`。每票或完整模块验证后立即提交并推送，不部署、不合并主分支。用户已有规则与其他未提交文件不混入。

| Ticket | 交付 | 验收 | 状态 |
| --- | --- | --- | --- |
| NATIVE-0 | 保存规格、过时文档清理和本票据；建立远端分支 | 本地链接和 diff 检查；推送成功 | DONE |
| NATIVE-1 | 核对框架真实 API，建立原生执行合约测试 | 单步执行、消息历史、绑定差异、顺序 | DONE |
| NATIVE-2 | 七个查询工具原生定义与安全上下文 | 查询真实业务边界、原生参数契约 | DONE |
| NATIVE-3 | Runtime 原生循环和两个最终提案工具 | 拒绝回流、来源、预算、冲突提案 | DONE |
| NATIVE-4 | 删除旧协议，贯通持久化和对外入口 | 并发幂等、正式事实恢复、历史隔离 | DONE |
| NATIVE-5 | 回归及真实环境验收，更新规格现状 | 编译、相关完整回归；真实环境限制单列 | PARTIAL：离线完成，真实验收存在阻塞 |

## 执行记录

记录每票实现、实际命令、结果与远端提交。真实模型和页面未验证时不宣称完成对应验收。

### NATIVE-0 / NATIVE-1

- NATIVE-0 已推送 `17e6d0b`，建立远端分支并保存规格。
- NATIVE-1：以项目实际 Spring AI 2.0.0 验证 manager 只执行一批、顺序和调用 ID、服务端 ToolContext 不进入 schema。
- 框架默认绑定会接受未知字段；后续使用其生成的 schema 校验输入，再由框架绑定，不能直接删除严格校验。
- `:app:test --offline --tests '*SpringAiToolContractTest'`：2 个测试通过。单步模型配置、错误和严格 schema 集成将在 NATIVE-2/3 覆盖。

### NATIVE-2

- 七个查询工具增加 Spring AI 原生定义；ToolContext 携带请求身份、deadline、读取预算与来源登记。原生 callback 仅补足 schema、安全和错误边界，绑定与调用消息交给框架。
- 此票保留旧 Loop 入口使中间提交可运行；NATIVE-3 切换后由 NATIVE-4 删除 Map 兼容入口。
- 原生整数溢出会由框架抛 InputCoercionException，已转为参数拒绝；未知字段、null、类型强转、额外 JSON 被 schema/解析边界拒绝。
- subagent `review_native2` 审查无阻塞问题；按建议补齐执行中关闭、迟到错误、重复调用、预算和故障语义测试。NATIVE-1 也已由独立 subagent 补审。
- 本次工具模块测试 37 项通过；`:app:compileJava` 通过。尚未切换生产 Loop，不能宣称完整迁移已验收。

### NATIVE-3

- NATIVE-2 已推送 `cb3c4ac`。本票切换唯一 Loop：单步 ChatClient + Spring AI ToolCallingManager；原生提案仅登记请求内决定，正式提交服务不变。
- 工具白名单来自实际定义；输入预算包含 schema、工具参数及完整原生消息，参考按完整片段裁剪并保留调用 ID 配对。
- subagent `review_native3` 确认通用 ToolCallbackProvider Bean 会被 MCP 自动发布；已改为具体 QueryTools 集合并用 Spring context 测试锁定外部隔离。该具体集合用于保护 MCP 发布边界，不执行工具或分派名称。
- 补充 CODE_REPAIR 嵌套绑定、冲突提案、同批来源拒绝、未知工具纠错、提案不占查询预算，以及实际 ChatClient 不执行工具/不自动循环的合约测试。
- 本票最终针对 Runtime/模型/上下文/原生工具的 37 项测试全部通过。旧协议和 Map 兼容代码留待 NATIVE-4 删除。

### NATIVE-4

- NATIVE-3 已推送 `6747a01`。删除旧 Gateway、工具调用 DTO/接口、CallReadTools、Interviewer 动作 JSON 输出，以及全部 Map 参数解析入口；业务 Ask/Finish 仅保留为正式提交类型。
- 七个原生方法使用类型化参数，业务读取继续验证归属、Plan、模式与来源；没有数据库迁移或 HTTP/SSE/MCP 字段变更。
- subagent `review_native4` 无阻塞发现；按建议补齐原生 manager 的四种终态、顺序、来源映射测试，并核对所有七个工具拒绝模型传入身份。
- `:app:test --offline --tests 'interview.guide.modules.interview.agent.adaptive.*'`：376 项，374 通过，2 个真实环境测试按条件跳过；包含持久化并发幂等、租约、旧执行者、代码任务、公开 DTO 和评估历史隔离。
- 完整回归发现首题代码标志 codeRepairFirst 的旧反射契约未同步，已修正该断言；不改变 Planner 行为。

### NATIVE-5

- NATIVE-4 已推送 `0f313f1`。本票完成真实 Registry → OpenAI SDK → ChatClient → 原生 manager 的本地 HTTP 集成：3 次受控模型响应、1 次真实工具方法读取，非法目标纠正后返回完整 CODE_REPAIR 提案，逐条验证调用 ID 配对且 JD 只出现一次。
- 根据最终审查修正用户提示词遗留动作要求，恢复共享防注入指令并计入预算，复用模型 token 遥测；删除无消费者的旧 interviewer 提示词/路径配置。校验错误字段同步为原生提案参数路径。
- subagent `review_native5` 无阻塞问题；提出的死提示词和配对断言建议均已落实。
- `:app:test --no-daemon --offline`：691 项，640 通过、51 项按已有条件跳过；耗时 2m03s，未超时。最终小幅接线/提示词修正后，重跑全部 adaptive 与 common.ai：413 项，410 通过、3 项跳过，耗时 35s。
- 本地 HTTP fixture 测试耗时 0.664s；该数字含本地模拟模型，返回的 usage 为合成值，不是线上延迟、token 或成本结论。
- 已在用户提供的测试账号下恢复真实环境：Windows Docker Desktop、独立 PostgreSQL 数据卷副本（15432）、后端 18080、原前端 5173、专用 Edge 浏览器。面试写入与已有会话重试均指向数据库副本；没有部署或修改原数据库中的面试记录。未将账号凭据、token、原始日志或私有参考提交 Git。
- 默认真实模型 `deepseek-v4-flash`：练习模式 / Java 两次创建分别被“规划结果包含重复主题”“练习计划包含范围外主题”拒绝。模型调用约 9.80s / 7.74s（按调用前预算日志至 usage 日志计算，非页面端到端延迟），usage 分别为输入/输出 6343/2338、6343/1809 tokens。
- 评估模式使用明确标注为虚构的 Java JD / 简历，成功在原页面生成首题 CODE_REPAIR，显示需求、假设及代码编辑器。Planner 约 8.90s，usage 2751/2228 tokens。此阶段尚未调用 Interviewer 原生工具，不能据此宣布真实 tool calling 通过。
- 原页面提交未修复代码作为负例，Assessor 首次与“重试原答案”均因“引用未命中指定来源的原文位置”失败，usage 分别为 7911/652、7911/680 tokens。刷新及 GET 正式快照确认仅 1 轮、1491 字符提交代码保留、答案状态 RETRYABLE、codeReview/assessmentFeedback 为空；未重复推进，也未把内部校验失败记作候选人负面反馈。
- 上述创建约束和引用校验在本次迁移中未修改，Planner / Assessor 仍走原有结构化输出。仅凭错误不能确定是 quote、source 还是 offset 不合法；需单独诊断模型提案，不能删除或放宽真实性校验。
- 较长历史会话第 11 轮重试：Assessor 成功（11608/592 tokens），Loop step 0 的输入估计 23155 超过 20000，在 Interviewer 模型请求前明确拒绝。事实投影未改、原生 schema 开销已计入；没有同事实旧版预算对比，不能直接归因为迁移回归。该样例 Interviewer 实际请求 0 次。
- 较短已有会话第 1 轮：从原页面提交标注为验收的文字回答，Assessor（6107/489 tokens）成功；真实 Interviewer 请求 2 次，usage 为 6661/41、7253/499 tokens，约 0.83s、3.12s（预算日志至 usage）。Loop step 0～1 完成并接受原生问题提案，页面成功进入第 2 轮。日志未记录逐项工具名称，不能据两次请求推断具体查询工具及数量，所有查询工具的真实线上覆盖仍未证明。
- 成功样例刷新后第 2 题恢复；正式快照第 1 轮 COMPLETED、第 2 轮 WAITING，评估模式未暴露 assessmentFeedback。通过 HTTP 重放第 1 轮完全相同答案返回 200，仍为 2 轮，没有新增模型 usage 日志。此次仅验证顺序重放幂等，不能代替并发验证。
- **未完成**：代码判题成功反馈、该代码会话后续真实原生查询/提案及完整流程、全部查询工具的真实覆盖。NATIVE-5 保持 PARTIAL；现有 PostgreSQL 迁移条件测试、真实 RAG 回放测试仍未运行。本次无前端实现改动，页面已实际打开和操作，未以构建替代交互验收。

## 远端回滚节点

| 模块 | 提交 |
| --- | --- |
| 规格及清理 / NATIVE-0 | `17e6d0b` |
| 框架合约 / NATIVE-1 | `bbae19b` |
| 原生查询边界 / NATIVE-2 | `cb3c4ac` |
| 原生 Runtime / NATIVE-3 | `6747a01` |
| 删除旧协议 / NATIVE-4 | `0f313f1` |
| 提示安全、观测及 HTTP 集成 / NATIVE-5 | `2ae23c1` |
| 无效提示配置清理 / NATIVE-5 | `0817104` |
| 最终验证记录 / NATIVE-5 | 本记录所在提交（分支最新提交） |

所有节点在 `codex/native-tools-runtime`；未合并主分支。保留用户原有规则、EPIC、忽略文件及记忆文件等未提交改动，不混入上述提交。
