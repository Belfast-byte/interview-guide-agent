# T07 候选人记忆 API 与 UI

交付 35 号 tickets 中 T21-T22：候选人只能查询自己的 Topic Profile、等级分布、标签计数和 Episode 追问链，并在前端明确展示补全状态。

## 成功标准

- API owner 只来自认证主体，tenant/candidate 严格隔离。
- Response 不暴露 Entity、题答、摘要、评估理由、证据或内部来源 ID。
- Topic、标签与 Episode 排序稳定，Episode 支持分页。
- 前端按 sessionId + parentTurnIndex 组合追问链，明确展示全部 enrichment 状态。
