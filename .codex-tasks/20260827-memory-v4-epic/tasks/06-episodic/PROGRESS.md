# Progress Log

## Context Recovery Block

- **Current milestone**: #5 — 实现 QuestionExposure 与双召回视图
- **Current status**: IN_PROGRESS
- **Last completed**: T03 / commit `42ea980`
- **Current artifact**: `docs/design_spec/35-memory-three-layer-tickets.md` T04/T05
- **Key context**: ActionIntent 已保证问题发布和工具执行可恢复，Episode 可以以已落库 turn/assessment/evidence 为事实来源。
- **Known issues**: 旧 Episode 结构与评估替换、能力补偿紧耦合；当前没有未回答问题曝光和双视图召回。
- **Next action**: 建立问题曝光事实、正式面试中性召回和练习诊断召回，再接入出题后的换场景去重。

## Audit

- `candidate_memory_episode_facts` 当前只保存 owner/session/turn/assessment/topic 和可变 enrichment，缺 mode、target、work revision、assistance、closure 和 correction。
- `prepareAction` 与 `recordDecision` 已在回答短事务中生成 Episode，但 Episode 还会直接累加旧 AbilityCounter。
- 晚到沙箱结果通过 `reassessAlgorithmResult → replaceAssessment → AssessmentReconciliationService` 原地覆盖 Assessment、补偿 counter 并重置 Episode enrichment，与不可变事实冲突。
- `agent_tool_result_events` 已能保存晚到工具事实并应用 WorkState Patch，可直接保留这条路径，删除对旧 Assessment/Episode 的回写。
- 当前没有 QuestionExposure/QuestionIdentity/NoveltyPolicy；`vector_store` 已存在，但只有通用知识库 repository，需要候选人 exposure 的独立查询端口。

## T04 Delivery

- Episode 现在绑定真实 turn、会话模式、目标、WorkState 前后 revision、辅助级别、闭环状态和可选 correction 关系。
- Turn、Assessment、Evidence、WorkState Patch 与 Episode 在回答短事务中写入；事务失败时 Episode 与能力计数一并回滚。
- 晚到工具结果只保留 ToolResultEvent 与新 WorkState Patch，不再替换旧 Assessment、重置旧 Episode 或补偿能力计数。
- 删除了旧 `replaceAssessment`、reconciliation、算法结果二次评估及事后证据挂接链。
