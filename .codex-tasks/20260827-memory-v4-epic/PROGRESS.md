# Progress Log

## Session Start

- **Date**: 2026-08-27 23:50
- **Task name**: `memory-v4-epic`
- **Task dir**: `.codex-tasks/20260827-memory-v4-epic/`
- **Plan**: See `SUBTASKS.csv` (8 child deliverables)
- **Environment**: Java 21 / Spring Boot 4.1.0 / Gradle / JUnit 5

## Context Recovery Block

- **Current milestone**: #6 — 实现 Episode 召回与题目去重
- **Current status**: IN_PROGRESS
- **Last completed**: #5 — T03 commit `42ea980`
- **Current artifact**: `docs/design_spec/35-memory-three-layer-tickets.md` T04/T05
- **Key context**: ASK/CALL_TOOL 已持久为 ActionIntent，外部执行、结果落库和 Patch 应用可分段恢复。
- **Known issues**: 现有 Episode 仍是旧口径，晚到工具和 reassessment 仍可替换过去评估，且没有未回答问题曝光去重。
- **Next action**: 建立 Child 6 执行文件，先盘点 Episode/assessment/tool-result 现有表与替换路径。

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

## Child 5: 实现 ActionIntent 与恢复

- **Status**: DONE
- **Completed**: 2026-08-28 08:30
- **What was done**: ASK/CALL_TOOL 统一改为先落 Intent 的三段事务，工具执行复用持久幂等键，恢复任务按状态确定继续动作。
- **Validation**: T03 定向、模块回归、架构隔离、Controller 与 Spring AI gateway 测试均通过。
- **Commit**: `42ea980`
- **Next step**: Child 6 — Episode 召回与题目去重

## Shape Promotion

- **Date**: 2026-08-27 23:50
- **From**: compact single (`TODO.csv`)
- **To**: epic
- **Reason**: 用户将范围扩展为规格、tickets 和五个依赖明确的代码模块，单一 TODO 已无法表达交付边界。
