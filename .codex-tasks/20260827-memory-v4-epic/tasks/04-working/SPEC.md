# Task Spec: memory-v4-working

## Goal

用持久化 `WorkState` 替换 Prompt 临时 Working Memory：每次事件只提交 Typed Patch，Reducer 生成新状态，代码策略从新状态确定下一动作。

## In Scope

- 当前目标、证据、未闭环问题、焦点、剩余预算、状态版本。
- 会话初始化以及回答评估、工具结果、动作完成后的 Patch。
- `NextActionPolicy` 确定 ASK、CALL_TOOL、SWITCH_TARGET、FINISH。
- PostgreSQL 当前状态持久化与乐观锁。

## Out of Scope

- ActionIntent 持久化与故障恢复（T03）。
- Episode 召回、题目去重（T04/T05）。
- Semantic 聚合与消费（T06）。

## Success Criteria

- 业务角色不直接覆盖完整 WorkState。
- 相同 state + patch 得到相同结果；相同 state 得到相同下一动作。
- 外部调用不在数据库事务内。
- 删除被新 WorkState 替代的临时 Working Memory 兼容路径。
