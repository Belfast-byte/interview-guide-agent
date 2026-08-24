# T08 历史数据迁移

交付 35 号 tickets 中 T23：仅通过 session + dimensionOrder 回连 plan，确定性回填 LEGACY_UNENRICHED Episode、Counter 和 counter-v1 Profile。

## 成功标准

- 缺 plan、suggestedSkill、focusId 或 Assessment 时迁移显式失败。
- 不根据 dimension/focus 展示文本猜 TopicKey，不调用 LLM。
- answered turn 产生唯一 LEGACY_UNENRICHED Episode。
- Counter 等于历史 Assessment 聚合；Profile 使用同一 Counter 快照。
- 重复执行结果不变，由唯一身份和测试证明幂等。
