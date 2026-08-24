# T02 Working Memory 与 Turn Trigger

交付 35 号 tickets 中 T01-T06：稳定 TopicKey、TurnTrigger、turn provenance、ProbeGap 持久化与选择、WorkingMemorySnapshot。不得引入 Redis。

## 成功标准

- 同一 plan 的 TopicKey 唯一。
- trigger/source/parent 由代码裁决并可从 PG 恢复。
- gaps 按稳定顺序选择且有使用追溯。
- snapshot 不可变，follow-up depth 从父链计算。
- 所有行为有针对性单测，既有 application/persistence 测试不回归。
