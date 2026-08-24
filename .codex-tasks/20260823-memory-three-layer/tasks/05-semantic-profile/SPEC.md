# T05 Semantic Profile 与评估补偿

交付 35 号 tickets 中 T16-T18：按 owner + TopicKey 保存不可变计数快照，会话完成生成 SESSION_COMPLETED Profile，已完成会话修订生成 ASSESSMENT_CORRECTED Profile。

## 成功标准

- current Profile 对同 owner + TopicKey 唯一，历史快照保留。
- 快照保存完整 L0-L4 计数并由确定性阈值计算 ability。
- 会话完成为涉及主题生成；total=0 跳过；重复完成幂等。
- 已完成会话修订 supersede，进行中会话不生成修订快照。
