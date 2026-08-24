# T04 Episode enrichment 与标签

交付 35 号 tickets 中 T12-T15：显式 enrichment 状态机、规范化标签与来源、事务外结构化 LLM worker、显式重试和 Assessment 修订后的再 enrichment。

## 成功标准

- 状态迁移非法时失败；FAILED 不自动伪装成功。
- worker 先 claim，再在事务外调用 LLM，最后短事务 replace。
- 标签只接受白名单且 source 必须属于 Episode 权威链。
- 重复消费幂等；Assessment 修订清除旧贡献并回 PENDING。
