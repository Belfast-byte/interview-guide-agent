# 三层记忆 v4 完整交付

## Goal

- 按 `docs/design/02-memory-design.md` 完成技术规格、实施票据和全部代码交付，使 Working、Episodic、Semantic Memory 在正式评估与练习模式下按约定运行。

## Non-Goals

- 不迁移或兼容旧数据、旧 API 行为和旧记忆读链。
- 不建设通用记忆平台、租户安全框架或与三层记忆无关的功能。
- 不保留双写、legacy 枚举、失效表或未使用抽象。

## Constraints

- Spring Boot 4.1.0、Java 21、Spring AI 2.0.0，沿用现有 PostgreSQL 与 pgvector。
- 代码遵循项目 `AGENTS.md` 和 `.claude/rules/`；函数不超过 50 行，文件不超过 300 行。
- 每个子任务独立验证并按主题提交；后端测试最长 60 秒。
- 失败显式暴露，不加入 mock、静默 fallback 或无真实使用者的配置项。

## Risk Assessment

- 这是破坏性重构：旧记忆表与调用链会被直接删除，开发数据库需要重建。
- Working、Intent、Episode 和 Semantic 有顺序依赖，必须按 `SUBTASKS.csv` 交付。
- 正式评估的历史隔离需要通过依赖和 DTO 验证，不能只依赖 Prompt 文案。

## Child Deliverables

- 定稿三层记忆 v4 技术规格。
- 从规格拆分可独立验收的 tickets。
- 交付会话模式与计划输入。
- 交付 WorkState、Typed Patch 和确定性策略。
- 交付 ActionIntent 与恢复。
- 交付 Episode 召回与问题去重。
- 交付 Semantic 双轨与练习消费。
- 删除旧实现并完成全量验证。

## Dependency Notes

- tickets 依赖规格定稿。
- 代码模块按 session → working → intent → episode → semantic 顺序交付。
- 最终清理与全量验证依赖所有模块完成。

## Done-When

- [x] `SUBTASKS.csv` 全部为 `DONE`。
- [x] 后端全集按三个互斥分片验证，每个分片在 60 秒内通过。
- [x] 旧记忆读链、兼容分支、失效表和死代码已删除。
- [x] 工作树只剩用户原有的无关改动，本 Epic 的改动均已提交。
