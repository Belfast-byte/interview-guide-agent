# 自然语言设计

本目录由用户主导，用自然语言记录产品与架构的意图。文档重点是目标、范围、非目标、业务约束、关键裁决、取舍和行为级成功标准。

以类名、包路径、API、表结构、迁移、测试命令、实施计划或 tickets 为主体的文档放在 [`../design_spec/`](../design_spec/README.md)。框架设计可保留用于解释裁决的技术示例，但示例不构成当前实现规格，也不由用户负责同步。Agent 先读取本目录的相关设计，再生成或更新技术规格；未经明确要求，不向这里回填代码级细节。

## 文档索引

| 文档 | 内容 |
|---|---|
| [00-terminology.md](./00-terminology.md) | 领域术语与语义边界 |
| [01-platform-design.md](./01-platform-design.md) | 平台级目标、架构判断、工具/MCP/多 Agent 边界与演进路线 |
| [02-memory-design.md](./02-memory-design.md) | 三层记忆职责、正式评估与练习模式的消费边界 |
| [03-agent-loop-and-working-memory.md](./03-agent-loop-and-working-memory.md) | 真正的 Agent Loop、Working Memory、Rubric Tool 与模型/Java 边界 |
