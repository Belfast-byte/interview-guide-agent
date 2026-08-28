# Progress Log

## Context Recovery Block

- **Current milestone**: #1 — 盘点旧 Episode 与可变评估路径
- **Current status**: IN_PROGRESS
- **Last completed**: T03 / commit `42ea980`
- **Current artifact**: `docs/design_spec/35-memory-three-layer-tickets.md` T04/T05
- **Key context**: ActionIntent 已保证问题发布和工具执行可恢复，Episode 可以以已落库 turn/assessment/evidence 为事实来源。
- **Known issues**: 旧 Episode 结构与评估替换、能力补偿紧耦合；当前没有未回答问题曝光和双视图召回。
- **Next action**: 核对 EpisodeFact 实体/DDL/生成器、assessment replacement、tool result 晚到路径和现有 vector_store 用法。
