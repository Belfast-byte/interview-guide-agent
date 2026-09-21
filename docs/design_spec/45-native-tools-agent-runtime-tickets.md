# 原生工具迁移实施 tickets

日期：2026-09-21。依据：[44 号规格](./44-native-tools-agent-runtime-spec.md)。
分支：`codex/native-tools-runtime`。起点：`a484560`。每票或完整模块验证后立即提交并推送，不部署、不合并主分支。用户已有规则与其他未提交文件不混入。

| Ticket | 交付 | 验收 | 状态 |
| --- | --- | --- | --- |
| NATIVE-0 | 保存规格、过时文档清理和本票据；建立远端分支 | 本地链接和 diff 检查；推送成功 | DONE |
| NATIVE-1 | 核对框架真实 API，建立原生执行合约测试 | 单步执行、消息历史、绑定差异、顺序 | DONE |
| NATIVE-2 | 七个查询工具原生定义与安全上下文 | 查询真实业务边界、原生参数契约 | DONE |
| NATIVE-3 | Runtime 原生循环和两个最终提案工具 | 拒绝回流、来源、预算、冲突提案 | TODO |
| NATIVE-4 | 删除旧协议，贯通持久化和对外入口 | 并发幂等、正式事实恢复、历史隔离 | TODO |
| NATIVE-5 | 回归及真实环境验收，更新规格现状 | 编译、相关完整回归；真实环境限制单列 | TODO |

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
