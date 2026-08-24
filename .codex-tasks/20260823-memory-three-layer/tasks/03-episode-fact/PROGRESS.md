# Progress Log

## Context Recovery Block

- **Current milestone**: #6 — T03 回归门禁
- **Current status**: DONE
- **Last completed**: #5 Assessment replace 补偿
- **Current artifact**: T03 目标测试集合
- **Key context**: EpisodeFact 只存权威索引，不复制题答、等级或 provenance；唯一键是 session + turn。
- **Known issues**: `AdaptiveInterviewPersistenceService` 是既有超大文件，本阶段新增写入逻辑应抽出按职责协作者。
- **Next action**: 进入 Epic T04，先实现 enrichment 状态机。

## 2026-08-23 21:04

- T03 扩展门禁 72 项在 31 秒内通过。
- 首次失败是并发测试提交数据与固定 candidate fixture 共享 Counter；改为测试专属 owner 后复验通过。

## 2026-08-23 21:02

- Assessment replace 前按 Episode owner+TopicKey 补偿 Counter。
- 同级修订 no-op；旧计数不足直接失败。
- 双 EntityManager 测试证明 `@Version` 拒绝陈旧并发提交。

## 2026-08-23 20:59

- EpisodeFact 创建与 Counter 增量由同一 persistence 协作者在回答事务中完成。
- 跨 session 同 TopicKey 的 L2/L4 合并测试通过。
- 相同回答重放保持单 Episode、单次 L2 计数；并发回归通过。

## 2026-08-23 20:56

- AbilityCounter 纯领域模型覆盖 L0-L4、平均 3/2 阈值和下溢失败。
- Counter entity 使用乐观锁；tenant owner 查询隔离。
- `V20260918` 补齐 turn provenance、ProbeGap、EpisodeFact、AbilityCounter 生产 schema。

## 2026-08-23 20:53

- EpisodeFact 在 Assessment/gaps 后、其他回答事实之前同步写入同一事务。
- 过期回答零写入；相同回答重放被 session 裁决且 Episode 数保持 1。
- 修复 `recordDecision` 校验顺序：任何 plan 变更前先执行 session `assertCanAnswer`。

## 2026-08-23 20:50

- `candidate_memory_episode_facts` 仅包含权威索引、enrichment 状态和审计时间。
- JPA 测试证明 session-turn 唯一、owner 隔离和 PENDING 初始状态。
