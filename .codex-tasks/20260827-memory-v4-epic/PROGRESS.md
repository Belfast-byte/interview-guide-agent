# Progress Log

## Session Start

- **Date**: 2026-08-27 23:50
- **Task name**: `memory-v4-epic`
- **Task dir**: `.codex-tasks/20260827-memory-v4-epic/`
- **Plan**: See `SUBTASKS.csv` (8 child deliverables)
- **Environment**: Java 21 / Spring Boot 4.1.0 / Gradle / JUnit 5

## Context Recovery Block

- **Current milestone**: #8 — 删除旧实现并完成全量验证
- **Current status**: DONE
- **Last completed**: #8 — Cleanup commit `18ac158`
- **Current artifact**: `docs/design_spec/35-memory-three-layer-tickets.md` T07
- **Key context**: Working、Intent、Episode、曝光去重和 Semantic 双轨均已落地；正式 Planner 不读历史，练习只消费 scope 内长期状态。
- **Known issues**: 当前环境无 Docker/psql，PostgreSQL 空库 Flyway 未实跑；DDL 已完成静态依赖审计。
- **Next action**: Epic 完成，无剩余实施步骤。

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

## Child 6: Episode 召回与题目去重

- **Status**: DONE
- **What was done**: Episode 改为不可变事实，新增曝光召回、正式中性视图、练习诊断视图与保持 TargetEnvelope 的场景改写。
- **Validation**: Episode/QuestionExposure/QuestionNovelty 定向和模块回归测试通过。
- **Commits**: `bdad221`, `83effc0`

## Child 7: Semantic 双轨与练习消费

- **Status**: DONE
- **What was done**: Evaluation/Practice contribution 与 state 分轨聚合，练习 scope 消费、完整诊断和双轨画像 API/前端落地，旧单轨画像删除。
- **Validation**: 目标测试、前端构建和完整 adaptive 测试通过。
- **Commit**: `a5554ea`

## Child 8: 删除旧实现并完成全量验证

- **Status**: DONE
- **What was done**: 删除 Topic/Claim、单轨画像、backfill、legacy 枚举和失效 Prompt；补齐正式、练习和 Intent 恢复场景。
- **Validation**: 后端全集三个互斥分片、前端构建、旧引用检索、迁移版本唯一性和 diff 检查通过。
- **Commit**: `18ac158`

## Shape Promotion

- **Date**: 2026-08-27 23:50
- **From**: compact single (`TODO.csv`)
- **To**: epic
- **Reason**: 用户将范围扩展为规格、tickets 和五个依赖明确的代码模块，单一 TODO 已无法表达交付边界。
