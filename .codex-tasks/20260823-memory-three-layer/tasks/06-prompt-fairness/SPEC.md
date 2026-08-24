# T06 Episode 选择与 Prompt 公平性

交付 35 号 tickets 中 T19-T20：只选择同 TopicKey/同 skill 的完成态历史 EpisodePromptFact，在 2000 tokens 内稳定装配 Interviewer；Planner 和 Assessment 输入保持隔离。

## 成功标准

- 只查询 COMPLETED Episode，排除当前 session，owner 严格隔离。
- 选择优先同 TopicKey，再同 skill；排序稳定，超长单项 skip-continue。
- Prompt 只包含 DepthLevel、规范化标签和 createdAt；禁止历史文本字段。
- Planner/Assessment 输入结构和 prompt 不出现 Profile、Counter、Episode 或标签。
