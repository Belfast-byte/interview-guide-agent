# 文档中心 (Documentation)

欢迎查阅 **AI Interview Platform** 的架构与设计文档。

---

## 核心演进与设计蓝图 (`docs/design/`)

本目录为自适应面试 Agent（Adaptive Agent）重构与演进的**唯一事实源**：

| 文档 | 描述 | 关键内容 |
|---|---|---|
| [00-terminology.md](./design/00-terminology.md) | 领域术语表 | 统一名词定义（ReAct、Rubric、Evidence、ProbeGap 等） |
| [01-platform-design.md](./design/01-platform-design.md) | 平台演进设计基线 | 星型多 Agent 拓扑、客观验证工具链、MCP 跨边界、证据不变量 |
| [02-auth-permission.md](./design/02-auth-permission.md) | 认证与权限边界设计 | JWT 登录、candidateId 生产者、角色与数据归属防线（设计稿） |
| [10-text-interview.md](./design/10-text-interview.md) | 自适应文本面试设计 | 有界 ReAct 循环内核、分层记忆与上下文装配、状态机裁决 |
| [11-algorithm-interview.md](./design/11-algorithm-interview.md) | 算法面试与沙箱设计 | 现场代码执行协议、Redis Stream 异步判题、沙箱证据链 |
| [12-code-analysis-service.md](./design/12-code-analysis-service.md) | 代码分析服务设计 | 候选人仓库分析、代码事实核验、场景卡（Scenario Card） |
| [13-adaptive-optimization.md](./design/13-adaptive-optimization.md) | 自适应调度与优化设计 | 维度预算再分配、动态追问策略与换题机制 |
| [14-assessment-probe-gaps.md](./design/14-assessment-probe-gaps.md) | 评估与探测间隙设计 | L0～L4 深度量规与 Probe Gaps 校验 |
| [20-implementation-modules.md](./design/20-implementation-modules.md) | **实施模块与交付切片** | **架构与分包权威规范**、M0～M5 切片定义、持久化所有权 |
| [30-improvement-spec-2026-08-16.md](./design/30-improvement-spec-2026-08-16.md) | 改进方案规范 | 综合审查问题修复与长期增强项（IM-1～IM-12） |
| [31-candidate-provider-and-interview-history-spec.md](./design/31-candidate-provider-and-interview-history-spec.md) | 候选人 Provider 与面试历史规格 | 用户私有 Provider、Adaptive 模型接入、本人历史会话分页与验收标准 |
| [32-adaptive-agent-remediation-spec.md](./design/32-adaptive-agent-remediation-spec.md) | 代码治理与体验改进规范 | 死代码清理、失败语义软化、自适应能力通道、异步链路可靠性、首题提速与异步创建（T-1～T-7） |

---

## 评审与专题架构

- [adaptive-agent-review-2026-08-16.md](./adaptive-agent-review-2026-08-16.md)：自适应 Agent 综合架构与代码审查报告。
- [adaptive-agent-review-2026-08-15.md](./adaptive-agent-review-2026-08-15.md)：前期落地审查纪要。
- [voice-interview-architecture.md](./voice-interview-architecture.md)：实时流式语音面试架构设计。

---

## 历史归档 (`docs/archive/`)

- 早期 MVP 与初版探索设计文档均归档于 [archive/](./archive/) 目录，仅作历史溯源参考。
