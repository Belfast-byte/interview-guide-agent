# 场景记忆

一个 Episode 是一次已评估回答的长期索引，原题、原回答、L0～L4 和 gap 都复用答题事实。

| 入口 | 职责 |
| --- | --- |
| `EpisodeFactPersistence` | 在原回答事务中关联 Turn、Assessment、Skill 和知识点，最后一题同样保存 |
| `EpisodeFactEntity` / `EpisodeFactRepository` | 保存经历索引；创建参数内嵌为 `Creation` |
| `CandidateMemoryEpisodeQueryRepository` | 按当前用户和知识点联查原问答与正式评估，选择每个知识点最近一次表现 |
| `EpisodeQueryService` | 批量补齐已有 gap、关闭证据和当时前文，生成页面与 Agent 共用的视图 |
| `exposure/` | 沿用题目曝光事实，为避免重复原题提供历史问题 |

生产：回答接受 → 当前评估 → 原事务提交评估、证据、gap、Episode 和下一动作。

消费：`PracticeMemoryService` 按 `skillId` 形成职位画像，并为练习规划读取近期经历；`MemoryRecallTool` 按当前 Target 召回对应知识点。Agent 参考原场景换场景追问，采用的 `episode:ID` 留在 Working Memory。

Episode 不覆盖旧回答，画像不另造能力评级。未考察保持无评估；提示前文与已有关闭证据一起展示。问答前文使用内嵌的 `AdaptiveInterviewTurn.AnswerContext`，与本场评估共用。

不再存在 `enrichment`、`tag`、观察修订、记忆模型调用、队列、worker 或恢复任务。历史表列保留原值，新代码停止读写停用字段；V20261006 仅解除旧字段的非空约束。

完整业务约定与协议变化见 [38 号规格](../../../../../../../../../../../docs/design_spec/38-memory-business-reuse-spec.md)。
