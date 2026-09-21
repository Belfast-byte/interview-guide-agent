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
| NATIVE-5 | 回归及真实环境验收，更新规格现状 | 编译、相关完整回归；真实环境限制单列 | PARTIAL：离线完成，真实环境待验收 |

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
- **未完成**：真实 Provider 与原页面验收。本机 8080/5173/5432/6379 均拒绝连接；真实回放的账号、会话、轮次未配置；没有可用浏览器工具或浏览器程序。已询问测试账号/会话，未擅自创建或修改真实业务数据。2 个 adaptive 条件跳过项是真实 RAG 回放和 PostgreSQL 迁移校验。
- 本次无前端实现改动，因此未以构建替代页面交互验收。恢复真实环境后按 44 号规格第 12 节继续验收；本票保持 PARTIAL，不将离线通过标为真实验收完成。

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
