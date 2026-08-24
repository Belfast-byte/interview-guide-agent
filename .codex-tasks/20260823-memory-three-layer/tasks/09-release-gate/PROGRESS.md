# Progress Log

## Context Recovery Block

- **Current milestone**: #5 — 提交与发布状态核对
- **Current status**: IN_PROGRESS
- **Last completed**: #4 — 最终前端门禁
- **Current artifact**: `ThreeLayerMemoryInvariantTest` / 现有分层测试证据
- **Key context**: 完成判定必须逐条匹配 spec §9，窄测试不能证明全局门禁。
- **Known issues**: 工作树包含其他已完成任务的未提交改动，提交时必须按主题精确暂存。
- **Next action**: 精确核对工作树归属并按主题提交记忆系统改动。

## 2026-08-24 21:37 — 最终前端门禁

- 候选人记忆组件 Vitest 4/4 通过。
- TypeScript 检查与 Vite 生产构建通过；仅有既有 CSS/Browserslist warning。

## 2026-08-24 21:36 — 完整后端门禁

- 单 fork 在 60 秒硬超时内未完成；无测试断言失败。
- 根据 2 GiB/fork 和 8 GiB 开发环境，将 Gradle test 明确配置为两个并行 fork。
- 完整后端测试 `BUILD SUCCESSFUL in 41s`。

## 2026-08-24 21:33 — §9 证据审计

1. answered turn 唯一 Episode 且追溯 Assessment：`ThreeLayerMemoryInvariantIntegrationTest`。
2. Counter 等于有效 Assessment 等级分布：同一发布不变量测试。
3. Assessment 修正同步收敛 Counter/Profile/Tag：发布不变量与 `AssessmentReconciliationServiceTest`。
4. Assessment 历史隔离、Interviewer 白名单：`CandidateMemoryFairnessContractTest`。
5. owner 隔离：`EpisodePromptFactRepositoryTest` 与 `CandidateMemoryControllerTest`。
6. 请求重放不重复事实和画像：发布不变量测试。
7. enrichment 失败显式可见：`EpisodeEnrichmentServiceTest` 与恢复集成测试。
8. 缺稳定映射时迁移失败：`ThreeLayerMemoryHistoryBackfillIntegrationTest`。

- 八类组合门禁共 32 项通过。
- 修正所有引用 `EpisodeFactPersistence` 的 DataJpaTest 对原子 Counter store 的显式装配。
- T23 回填测试固定到 V20260921；完整 V20260918-V20260922 链由生产迁移集成测试通过。
