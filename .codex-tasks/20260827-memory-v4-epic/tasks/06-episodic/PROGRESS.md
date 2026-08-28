# Progress Log

## Context Recovery Block

- **Current milestone**: #7 — 运行模块验证并提交 T04/T05
- **Current status**: DONE
- **Last completed**: T03 / commit `42ea980`
- **Current artifact**: `docs/design_spec/35-memory-three-layer-tickets.md` T04/T05
- **Key context**: ActionIntent 已保证问题发布和工具执行可恢复，Episode 可以以已落库 turn/assessment/evidence 为事实来源。
- **Known issues**: 旧 Episode 结构与评估替换、能力补偿紧耦合；当前没有未回答问题曝光和双视图召回。
- **Next action**: 返回 Epic，启动 T06 Semantic 双轨与练习消费。

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

## T05 Delivery

- 最终问题、QuestionExposure 与 turn 在 ASK Intent 的同一事务中发布；未回答曝光也进入去重。
- `EvaluationRecallView` 只提供中性题目与正式未闭环验证点，`PracticeDiagnosticView` 提供完整题答、评级、证据、gap、工具来源和辅助闭环状态。
- vector_store 使用独立 document type，并以 MemoryOwner + TopicKey 元数据检索后回表隔离。
- 初稿重复时在同一绝对 deadline 内重写一次；代码保持 TopicKey、深度、难度和证据目标不变，重写后仍重复则显式失败。
- **Validation**: T04/T05 目标测试、架构测试与 346 个 adaptive Agent 测试通过。
