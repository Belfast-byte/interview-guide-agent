# Progress Log

## Session Start

- **Date**: 2026-08-27 23:50
- **Task name**: `memory-v4-epic`
- **Task dir**: `.codex-tasks/20260827-memory-v4-epic/`
- **Plan**: See `SUBTASKS.csv` (8 child deliverables)
- **Environment**: Java 21 / Spring Boot 4.1.0 / Gradle / JUnit 5

## Context Recovery Block

- **Current milestone**: #5 — 实现 ActionIntent 与恢复
- **Current status**: IN_PROGRESS
- **Last completed**: #4 — T02 commit `29100db`
- **Current artifact**: `docs/design_spec/35-memory-three-layer-tickets.md` T03
- **Key context**: WorkState、Typed Patch 和确定性策略已成为当前运行状态唯一写链，旧临时 Working 路径已删除。
- **Known issues**: ASK/CALL_TOOL 尚未以 ActionIntent 先落库，进程故障后仍缺少确定性恢复入口。
- **Next action**: 建立 Child 5 执行文件，核对现有 turn/tool result 持久化边界并接入 ActionIntent。

## Child 1: 定稿三层记忆 v4 技术规格

- **Status**: DONE
- **Completed**: 2026-08-27 23:52
- **What was done**: 直接重写 34 号规格，收敛为个人项目需要的最小表、最小恢复链和明确读侧隔离。
- **Validation**: `git diff --check`、300 行限制、设计/代码/Flyway 定向核对 → exit 0
- **Next step**: Child 2 — 拆分 v4 实施 tickets

## Child 2: 拆分 v4 实施 tickets

- **Status**: DONE
- **Completed**: 2026-08-28 00:02
- **What was done**: 用 7 个完整模块 tickets 覆盖旧票据，补充前端交付和完成标准映射。
- **Validation**: 文档静态检查、行数、结构计数和 v3 内容检索 → exit 0
- **Next step**: Child 3 — 实现会话模式与计划输入

## Child 3: 实现会话模式与计划输入

- **Status**: DONE
- **Completed**: 2026-08-28 00:34
- **What was done**: 打通 Evaluation/Practice、候选人阶段、显式练习范围和代码裁决后的 CapabilityTarget，删除正式 Planner 历史输入。
- **Validation**: T01 定向后端测试、测试源码编译、前端构建、staged diff 检查均通过。
- **Commit**: `82a4759`
- **Next step**: Child 4 — WorkState Typed Patch 与确定性策略

## Child 4: 实现 WorkState Typed Patch 与确定性策略

- **Status**: DONE
- **Completed**: 2026-08-28 06:06
- **What was done**: 以持久 WorkState、Typed Patch Reducer 和确定性策略替换临时 Prompt Working Memory，并移除旧双轨与模型结束裁决。
- **Validation**: 架构隔离、WorkState、NextActionPolicy 定向测试和测试编译通过；全量套件触及 60 秒硬超时，留作最终门禁处理。
- **Commit**: `29100db`
- **Next step**: Child 5 — ActionIntent 与恢复

## Shape Promotion

- **Date**: 2026-08-27 23:50
- **From**: compact single (`TODO.csv`)
- **To**: epic
- **Reason**: 用户将范围扩展为规格、tickets 和五个依赖明确的代码模块，单一 TODO 已无法表达交付边界。
