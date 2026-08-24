# Progress Log

## Context Recovery Block

- **Current milestone**: 全部完成
- **Current status**: DONE
- **Last completed**: #4 T05 回归门禁
- **Current artifact**: T05 测试报告
- **Key context**: Profile 是 owner + TopicKey 的不可变 Counter 快照，不保存单一 sourceAssessmentId。
- **Known issues**: 历史 legacy profile 数据保留待 T23 确定性回填。
- **Next action**: 进入 T06 Episode 选择与 Prompt 公平性。

## 2026-08-23 21:45

- T16-T18 定向门禁、真实完成链路和 T05 组合门禁全部通过。
- current 唯一、Counter 快照、完成态幂等、修订 supersede 均有自动化证据。

## 2026-08-23 21:40

- 旧 dimension/depth/sourceAssessment 可变画像已替换为 owner + TopicKey Counter 快照模型。
- 增加 current 部分唯一索引、supersededAt 历史和 revision reason。
- 完成会话与 Assessment 修订生成路径均已实现；进行中修订不生成 Profile。
- 上一次全测试源码编译在 API 模型修正后通过；其后的 T18 增量仍待 Gradle 验证。
