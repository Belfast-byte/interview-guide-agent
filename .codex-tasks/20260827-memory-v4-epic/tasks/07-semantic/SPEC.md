# Task Spec: memory-v4-semantic

## Goal

把正式能力与练习掌握拆成两条长期状态轨道，并让长期记忆只在练习模式内影响规划和训练。

## In Scope

- Evaluation/Practice 各自产生不可变 SemanticContribution。
- 按 owner、topic、track 重算 SemanticState。
- 正式能力、练习掌握、稳定模式和能力迁移的确定性聚合。
- 练习 scope 内 planning view，以及 Target 固定后的完整 Episode 诊断。
- 候选人画像 API 与页面直接展示双轨状态。

## Out of Scope

- 历史数据回填、双写、兼容旧画像响应。
- 正式 Planner、正式 Assessor 消费任何长期记忆。
- 快照、outbox、策略版本和通用知识图谱。

## Success Criteria

- 两种模式的 contribution 和 state 不跨轨。
- 练习结果不会覆盖正式能力，transfer 只由后续正式 Episode 更新。
- 稳定模式至少来自两个不同 Episode。
- 练习规划不能超出请求 scope，正式规划请求不含 Semantic。
- API、前端和数据库只使用双轨 Semantic 模型。
