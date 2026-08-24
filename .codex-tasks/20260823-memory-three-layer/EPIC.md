# 三层记忆系统实现 Epic

## Goal

- 将 `docs/design/34-memory-three-layer-spec.md` 补全为无未决实现语义的 v3 事实源。
- 将 v3 拆成具备 Goal、实现边界、验收命令和依赖关系的 tickets。
- 实现 Working / Episodic / Semantic Memory，完成历史迁移、候选人展示和公平性回归。

## Non-Goals

- 不引入 Redis Working Memory。
- 不改题库 pgvector 语义去重。
- 本期不让能力画像反哺 Planner。
- 不改企业侧平台资产记忆。

## Constraints

- Spring Boot 4.1.0、Java 21、Spring AI 2.0.0、React。
- 遵循 `api → application → {core, runtime}` 及 adaptive 包隔离规则。
- LLM、外部调用不得进入数据库事务。
- Assessment 只使用当前问题、当前回答和当前工具事实，禁止读取历史记忆。
- 后端测试单次执行硬超时 60 秒。
- 不覆盖工作树中不属于本 Epic 的改动。

## Risk Assessment

- 数据库迁移会改变画像身份和历史聚合语义。
- 异步 enrichment 必须保留持久化失败状态，不能丢 EpisodeFact。
- 异步判题会替换 Assessment，需要原子补偿计数器和画像快照。
- Episode 进入 Interviewer 后必须保持 Assessment 公平性硬隔离。

## Child Deliverables

- T01：关闭 spec 并形成 tickets。
- T02：Working Memory、ProbeGap 与 Turn Trigger。
- T03：EpisodeFact 与 AbilityCounter 原子写入。
- T04：Episode enrichment 与标签。
- T05：Semantic Profile 与异步评估补偿。
- T06：Episode 选择、Prompt 安全投影与公平性。
- T07：候选人 Episode/Profile API 与前端展示。
- T08：历史数据迁移和兼容验证。
- T09：端到端验收、文档同步和发布门禁。

## Dependency Notes

- T02 依赖 T01。
- T03 依赖 T01、T02。
- T04 依赖 T03。
- T05 依赖 T03、T04。
- T06 依赖 T03、T05。
- T07 依赖 T04、T05、T06。
- T08 依赖 T03、T05。
- T09 依赖全部前置任务。

## Done-When

- [x] `SUBTASKS.csv` 全部为 `DONE`。
- [x] v3 spec 与 tickets 文档没有影响实现的未决项。
- [x] 定向测试、迁移测试、前端构建和 60 秒后端门禁均有记录。
- [x] 所有主题改动已分主题提交到 `feat/memory-three-layer`。
