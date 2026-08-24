# Progress Log

## Context Recovery Block

- **Current milestone**: 全部完成
- **Current status**: DONE
- **Last completed**: #7 T04 回归门禁
- **Current artifact**: T04 组合测试命令
- **Key context**: 同步 EpisodeFact 已以 PENDING 创建；enrichment 只写摘要和规范化标签，不写 outcome。
- **Known issues**: 无。
- **Next action**: 进入 T05 Semantic Profile。

## 2026-08-23 21:33

- T04 组合门禁 19 秒通过。

## 2026-08-23 21:32

- Assessment 修订每次都清除旧摘要和标签，等级变化时另做 Counter 补偿。
- Episode 的 Assessment 引用不变，事务提交后只登记一次重补全事件。
- LEGACY_UNENRICHED 明确拒绝在线状态重置。

## 2026-08-23 21:24

- 结构化 LLM 调用位于 claim/complete 两个短事务之间，集成测试确认调用点无活跃事务。
- 权威上下文读取器从真实 JPA 关系组装 evidence/gap/tool result source ID。
- 非法单标签过滤、重复消费空操作、LLM/空摘要失败落库均通过。
- `AFTER_COMMIT` 事件唤醒后台 worker，队列拒绝错误不吞掉。

## 2026-08-23 21:14

- `claim` 使用 Episode 行锁，重复消费只有首次能进入 PROCESSING。
- completion 在独立事务替换摘要和标签；空摘要明确失败。
- FAILED 仅显式 retry，超时 PROCESSING 可恢复并返回待重排队 ID。

## 2026-08-23 21:10

- 三类标签 source 均按当前 Episode 权威关系校验。
- 非法来源或非白名单标签只丢弃单项，合法项保留并确定性去重。
- `EpisodeTagValidatorTest` 定向门禁通过。

## 2026-08-23 21:08

- 错误模式和回答习惯各 8 个固定枚举。
- 标签关系按 Episode/category/tag/source 唯一，V20260919 增加规范化表。
- 分类和值不匹配在领域边界失败。

## 2026-08-23 21:06

- 状态机覆盖 claim、complete、fail、FAILED 显式 retry、stale recovery。
- LEGACY_UNENRICHED 终止在线迁移；error 与 FAILED 强绑定。
