# Progress Log

## Session Start

- **Date**: 2026-08-28 00:02
- **Task name**: `memory-v4-session-plan`
- **Task dir**: `.codex-tasks/20260827-memory-v4-epic/tasks/03-session-plan/`
- **Spec**: See `SPEC.md`
- **Plan**: See `TODO.csv` (8 milestones)
- **Environment**: Java 21 / Spring Boot 4.1.0 / React / Gradle / pnpm

## Context Recovery Block

- **Current milestone**: #8 — 按主题提交 T01
- **Current status**: IN_PROGRESS
- **Last completed**: #7 — 定向验证与清理
- **Current artifact**: T01 后端、DDL、Prompt、前端和测试改动
- **Key context**: EVALUATION Planner 不再读取长期记忆；PRACTICE 只允许显式 TopicKey 范围；深度与预算由 candidate level 确定性裁决。
- **Known issues**: 全量后端测试超过仓库规定的 60 秒硬上限；终止前无失败，T01 定向集合 21 秒全部通过。
- **Next action**: 仅暂存 T01 与任务追踪文件，检查 staged diff 后提交。

## Implementation Summary

- 会话固定保存 `mode`、`candidateLevel` 和 `practiceScope`，REST/MCP/响应/前端完整往返。
- `CapabilityTarget` 保存 TopicKey、目标深度、上限、轮次/追问/工具预算与证据目标。
- Planner 上下文删除 `coveredTopics`、`unverifiedClaims`；正式评估保持独立，练习范围由代码校验。
- 直接修改目标建表脚本，不增加迁移、回填、双写或兼容重载。
- 验证：T01 定向后端测试通过；`pnpm run build` 通过；`git diff --check` 通过。
