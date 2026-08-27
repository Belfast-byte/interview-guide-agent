# Task Specification：T01 Session / Plan

## Task Shape

- **Shape**: `single-full`

## Goals

- session 持久化 EVALUATION/PRACTICE、候选人级别和明确练习 TopicKey scope。
- 计划 Target 固化级别深度边界、证据目标和三类预算。
- 正式 Planner 完全移除跨场 topic/claim 输入。
- 前端创建表单支持模式、级别和练习范围。

## Non-Goals

- 本任务不实现 Semantic planning view；PRACTICE 先按用户明确 scope 规划。
- 不实现 WorkState、ActionIntent、Episode 召回或旧数据迁移。
- 不保留双 request/response 格式。

## Constraints

- 复用 `PlanningTaxonomy`、`InterviewSkillService` 和现有创建链。
- 只在 API 输入边界校验 mode/scope 组合，不重复数据库约束。
- LLM 调用仍在事务外；后端测试 60 秒，前端必须 build。

## Deliverables

- Session mode/level/scope domain、API、持久化和前端表单。
- CapabilityTarget 深度/预算模型、Planner contract 和持久化字段。
- 正式 Planner 历史输入链删除。

## Done-When

- [ ] EVALUATION 创建不读取候选人历史。
- [ ] PRACTICE 只按明确 scope 规划。
- [ ] 三种 level 产生规格固定的 expected/depth ceiling/follow-up 上限。
- [ ] 后端定向测试和前端 build 通过。
- [ ] 无本任务产生的死代码、兼容分支或未提交文件。

## Final Validation Command

```bash
timeout 60s ./gradlew :app:test --no-daemon --console=plain --tests '*SessionMode*' --tests '*CapabilityTarget*' --tests '*Planning*' && cd frontend && pnpm run build
```
