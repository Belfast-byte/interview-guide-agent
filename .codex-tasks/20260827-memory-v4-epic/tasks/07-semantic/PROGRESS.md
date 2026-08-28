# Progress Log

## Context Recovery Block

- **Current milestone**: #7 — 提交 T06
- **Current status**: IN_PROGRESS
- **Last completed**: T05 / commits `bdad221` and `83effc0`
- **Current artifact**: `docs/design_spec/35-memory-three-layer-tickets.md` T06
- **Key context**: Episode 已不可变，正式召回与练习诊断已有分离视图；Semantic 可直接从 Episode 事实生成 contribution。
- **Known issues**: 旧 Topic/Claim 读写链与历史测试资源仍待 T07 物理删除；它们不再参与双轨画像 API。
- **Next action**: 提交 T06，仅纳入 Semantic 双轨实现、旧单轨删除和本任务台账。

## Audit

- Episode 写入事务当前直接调用 `AbilityCounterIncrementStore`，只按 L0～L4 累加，模式信息没有进入长期画像。
- `AbilityProfileSnapshotService` 在会话结束或旧评估修订时复制 counter 快照；该路径与 v4 的可重算 State 冲突。
- `CandidateMemoryQueryService` 和前端画像页直接读取单轨 `candidate_ability_profiles`，需要一次性切到 SemanticState。
- Planner 当前只接收 JD、简历、模式、候选人级别、practice scope 和技能目录；正式模式已经没有历史字段，练习模式尚未装配 planning view。
- `QuestionNoveltyService` 无条件使用正式中性召回；练习完整诊断虽已落地为 `PracticeDiagnosticView`，尚未在 Target 固定后交给 Interviewer。
- enrichment 标签在独立短事务中写入，SemanticState 的 stable pattern 必须在该事务完成后重算，不能由 LLM 直接更新。
- 旧 topic/claim 和对应历史测试资源留到 T07 统一物理删除；迁移/backfill 与旧画像表文件已随 T06 删除。

## Delivery

- Evaluation 和 Practice 分别生成不可变 contribution；State 从 contribution 与 Episode 标签确定性重算。
- 正式能力沿用 L0～L4 公式，练习掌握取最新结果；transfer 只由练习后的正式 Episode 更新。
- Planner 只有练习模式携带 scope 内 Semantic planning view；Target 固定后练习 Interviewer 才读取完整 Episode 诊断。
- 候选人画像 API 与前端已直接切为双轨 State，没有保留旧 response 格式。
- 已删除旧 counter/profile/snapshot 运行代码、端点、测试以及历史 backfill 实现。
- **Validation**: Semantic/Practice/CandidateMemory 目标测试通过，前端生产构建通过；完整 adaptive 测试生成 110 份 XML 报告，failure/error 均为 0。
