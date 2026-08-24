# Progress Log

## Context Recovery Block

- **Current milestone**: DONE — T19-T20 Episode Prompt 公平性
- **Current status**: DONE
- **Last completed**: #5 — T06 回归门禁
- **Current artifact**: `EpisodePromptSelector.java` / `InterviewerContext.java` / Prompt contract tests
- **Key context**: Interviewer 只接收 2000 tokens 内六字段 Episode 历史；DimensionBrief 不进入历史 prompt；Assessment/Planner 无历史评级输入。
- **Known issues**: `DepthLevel` 唯一枚举仍位于 assessment 包，后续需机械迁入 core 以消除包级窄依赖，不改变行为。
- **Next action**: 执行 T07/T08/T09 组合门禁与最终依赖方向清理。
