# T03 EpisodeFact 与 AbilityCounter

交付 35 号 tickets 中 T07-T11：同步 EpisodeFact、L0~L4 Counter、新 Assessment 增量和异步判题补偿。

## 成功标准

- 每个 answered turn 在同一短事务内恰有一个 EpisodeFact。
- 重放不重复 Episode 或 Counter 增量。
- Counter 按 owner + TopicKey 隔离，纯代码计算三级能力，永不为负。
- Assessment replace 时原子执行 old-- / new++。
