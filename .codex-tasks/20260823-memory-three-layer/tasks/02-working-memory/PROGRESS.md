# Progress Log

## Context Recovery Block

- **Current milestone**: #7 — T02 回归门禁
- **Current status**: DONE
- **Last completed**: #6 WorkingMemorySnapshot 与应用接入
- **Current artifact**: `memory/working/WorkingMemorySnapshot.java`、`ContextAssembler.java`
- **Key context**: 复用 CoveredTopic 的语义，但新增 TopicKey 作为统一身份；TurnTrigger 是纯领域值对象。
- **Known issues**: turn 当前没有任何 parent/source 字段；ProbeGap 只在 AssessmentDecision 调用内存存在。
- **Next action**: 进入 Epic T03，建立 EpisodeFact 和 AbilityCounter。

## 2026-08-23 20:48

- 首题和普通下一题都通过不可变 WorkingMemorySnapshot 进入 request。
- Assessment ID 在事务保存后绑定，snapshot 只保留 trigger type，避免伪造持久化 ID。
- 根问题、二级评估追问、工具追问和缺父链失败测试通过。
- T02 扩展门禁在 18 秒内通过。

## 2026-08-23 20:43

- `ProbeGapSelector` 以纯函数执行 TopicKey 过滤、已使用过滤和稳定排序。
- 四类针对性测试通过，不依赖缓存或持久化实现。

## 2026-08-23 20:41

- 新表 `agent_assessment_probe_gaps` 保存稳定 order/code 与原始锚点/缺口。
- Assessment 和 gaps 在 `recordDecision` 同一事务内写入。
- 唯一约束、Assessment 隔离、顺序和下一 turn assessment 引用均通过测试。

## 2026-08-23 20:38

- turn 新增 parent、trigger 和两类 source 字段。
- 首题、普通下一题、assessment gap、tool result 四条路径均由代码赋值。
- 实体测试、JPA 往返和持久化服务目标测试通过。

## 2026-08-23 20:36

- TopicKey 组合身份和 plan 去重已实现。
- TurnTrigger 三种来源约束已实现。
- 目标测试 18 项通过；旧计划夹具产生真实重复 TopicKey，修正夹具后通过。
