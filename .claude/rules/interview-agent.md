---
paths:
  - "app/src/main/java/interview/guide/modules/interview/agent/**/*.java"
  - "app/src/test/java/interview/guide/modules/interview/agent/**/*.java"
  - "app/src/main/resources/prompts/adaptive-agent-*.st"
---

# Adaptive Interview Agent Rules

适用于自适应面试 Agent。产品与架构意图以 `docs/design/` 为准；Agent Loop、Tool、Working Memory 和迁移目标以 `docs/design_spec/36-agent-loop-working-memory-spec.md` 为准；包边界以 `docs/design_spec/20-implementation-modules.md` 为准。代码与测试描述当前运行事实，不能反向覆盖目标规格。

## 控制权边界

- Agent 决定需要语义推理的策略：当前 Target/Gap、Gap 优先级、继续追问或切换、是否查询资料、只读 Tool 的参数和顺序、下一题以及结束建议。
- Java 只裁决硬边界：权限/归属、Session 与 Turn 合法性、最大轮次、Target 属于 Plan、Tool allowlist/schema/scope、Evidence/ProbeGap/代码 provenance 真实、沙箱隔离、稳定业务幂等和数据库完整性。
- 资源预算只限制 deadline、token 和调用资源，不得预选 Tool、Gap、调用顺序或维度顺序。
- 非法提案把明确 rejection observation 返回 Agent；不得静默替换动作、Gap、题目或证据，不得用硬编码兜底问题伪造成功。

## Agent Loop 与 Tool

- 面试决策统一进入一个 `InterviewAgentLoop`：读取事实和 Working Memory，调用模型，执行 0..N 个只读 Tool，把 Observation 回给模型，直到得到 ASK 或 FINISH 提案。
- 不建立通用 Role Registry 或第二套 Agent Loop。Planner、Interviewer、Assessor 是具体模型职责，不是可配置角色平台。
- 一个能力只有在“是否调用”“关键参数”都需要模型依据当前语义决定，且结果会影响本轮后续决策时，才是 Agent Tool。
- 固定 Skill 由 ContextAssembler 自动加载；固定 ID 的 Rubric/Session/config 查询是普通服务；`rubric_search`、题库语义搜索和按需 Episode 检索可以是只读 Tool。
- 只读 Tool 经 `ToolGateway` 做 allowlist、schema、ownership/scope、provenance、deadline 和 dispatch；模型网关不得绕过网关自动注册工具。只读调用不持久化执行状态，也不需要 invocation idempotency。
- 用户提交代码后的沙箱执行是 Application Service。它不进入通用 Tool executor；`SandboxExecution` 是唯一执行事实源，重投必须复用稳定业务键。

## Working Memory 与事实

- 领域事实包括 Session、Plan、Turn、Assessment、ProbeGap、Evidence、Episode 和 SandboxExecution。页面、权限、报告和一致性判断只读取这些事实。
- Working Memory 只保存当前注意力、工作假设、ProbeGap/Evidence 引用、下一步验证意图和最近 Observation；不得复制问题、回答、正式评级、剩余轮次或执行状态。
- 一次 Loop 内 Working Memory 可在内存持续更新；最终 Snapshot 与采用的下一 Turn 一起提交，并记录 `basedOnTurnIndex`。崩溃后从最近领域事实和 Snapshot 重新运行。
- 不为可重算中间推理建立 `WorkState`、Typed Patch、ActionIntent、只读 ToolExecution、ToolResultEvent 或恢复调度。

## 编排、持久化与并发

- application 层组织“读取事实 → 外部调用 → 硬边界裁决 → 短事务提交”；persistence 只保存事实，不决定下一动作。
- LLM、MCP、S3、外部 HTTP 和沙箱调用不得进入数据库事务。
- 同一回答只推进一次，以稳定业务键、必要的条件更新/乐观锁和数据库唯一约束承担 correctness；不得再叠加持久 Intent 或 WorkState revision 保护同一事实。
- Redis 只承载判题、代码分析等异步 Stream，不是会话或 Working Memory 的事实源。

## 证据、安全与公平性

- Assessment quote、ProbeGap anchor 和代码 provenance 必须命中真实回答、工具结果或分析产物。允许明确且统一的空白/全半角归一化；失败必须显式暴露。
- `TIMEOUT_QUEUED` 和平台内部错误不得成为候选人负面证据；迟到或已被替代的沙箱结果不得污染当前代码版本。
- 沙箱隔离、MCP tenant credential/scope/audit、隐藏用例保密和日志脱敏规则不可放宽。
- 历史 Episode 可用于中性去重或练习；正式 Assessor 不读取历史评级或 Semantic 画像。

## 测试

- 必须覆盖：非法 Target/Tool 被拒绝、Observation 回流后的二次模型决策、同轮并发只推进一次、沙箱稳定幂等、Evidence/provenance 真实性、正式评估历史隔离。
- 删除旧机制时同步删除锁定旧 `WorkState`、Patch、Intent、固定维度顺序和伪 Tool 行为的测试；不得让历史契约测试阻止目标架构落地。
