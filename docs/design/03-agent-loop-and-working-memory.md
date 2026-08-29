# 面试 Agent 的运行方式：模型、工具与工作记忆

> 状态：当前设计方向。
>
> 本文用自然语言说明面试 Agent 应该如何思考、使用工具和维护工作记忆。它更新了旧设计中“Java 决定大部分面试策略”以及“用 WorkState、Patch、ActionIntent 恢复每个中间步骤”的部分。安全、权限、证据可追溯、沙箱隔离和最大轮次等硬约束仍然保留。

## 1. 我们想要的 Agent

真正的 Agent 不只是调用几次模型，也不是把普通服务包装成 Tool。

它应该能够：

1. 读取本场面试已经发生的事实；
2. 理解当前还缺少什么证据；
3. 自己选择继续追问、切换维度、查询资料或结束；
4. 按需调用工具；
5. 看到工具结果后重新思考；
6. 最终给出下一道问题或结束决定。

整体过程是：

```text
读取本场事实
  ↓
组装当前上下文和 Working Memory
  ↓
模型决定下一步
  ├─ 调用只读工具 → 得到结果 → 更新 Working Memory → 模型继续思考
  ├─ 提出下一道问题
  └─ 建议结束面试
  ↓
Java 检查硬约束
  ↓
短事务保存最终事实
```

模型拥有面试策略的选择权，Java 负责守住不能突破的边界。

## 2. 哪些东西是事实

领域事实表示“已经真实发生了什么”，是系统的最终依据：

- Session：面试是否创建、进行中或完成；
- Plan：这场面试计划考察哪些维度；
- Turn：用户已经看到的问题和已经提交的回答；
- Assessment：模型对某次回答形成的正式评估；
- ProbeGap：根据评估确认还缺少什么能力证据；
- Evidence：候选人原话、代码结果等可追溯证据；
- Episode：过去某次具体面试经历；
- SandboxExecution：代码沙箱的真实执行状态和结果。

用户页面、最终报告、权限判断和数据完整性都必须读取这些事实，不能读取 Working Memory 代替它们。

## 3. Working Memory 是什么

Working Memory 表示：

> Agent 此刻正在关注什么、暂时怎么理解候选人，以及下一步准备验证什么。

它适合保存：

- 当前关注的面试维度；
- 当前最值得验证的 Gap；
- 多个 Gap 的临时优先级；
- Agent 对候选人能力的工作假设；
- 支持或反驳假设的 Evidence 引用；
- 下一步打算验证什么；
- 最近工具结果带来的新信息。

例如：

```text
当前维度：Java 并发
当前 Gap：候选人是否混淆可见性与原子性
工作假设：候选人知道 volatile 的定义，但不理解 happens-before
已有证据：回答中提到“volatile 可以保证线程安全”
下一步意图：通过 i++ 的反例验证其理解边界
```

这是真正会影响 Agent 下一步决策的短期认知状态。

## 4. Working Memory 不保存什么

Working Memory 不应再次复制领域事实。

它只保存引用和当前注意力，不保存另一份完整业务数据：

| 不应复制的内容 | 应从哪里读取 |
|---|---|
| 维度名称、目标和固定 Skill | Plan |
| 问题和回答全文 | Turn |
| 正式深度等级和置信度 | Assessment |
| Gap 的正式描述和来源 | ProbeGap |
| 证据原文 | Evidence |
| 剩余轮次 | 根据 Plan 和 Turn 计算 |
| Session 是否完成 | Session |
| 沙箱是否运行中 | SandboxExecution |

Working Memory 可以保存 `activeTargetId`、`activeGapId` 和 Evidence ID，但不保存这些对象的完整副本。

它也不保存：

- `ACTION_PENDING`；
- 正在执行哪个 Intent；
- Tool 是 RUNNING 还是 SUCCEEDED；
- 为恢复 Java 流程而设计的 phase 和 revision；
- 模型完整的思维过程。

## 5. Gap 有两种含义

需要区分正式 Gap 和工作假设。

### 正式 ProbeGap

它来自某次回答的正式评估，表示本场面试还缺少哪类证据。它有明确来源，需要持久化。

例如：

> 当前回答不足以证明候选人理解 volatile 与原子性的区别。

### Working Hypothesis

它是 Agent 的暂时判断，可以随着新回答或工具结果改变。

例如：

> 候选人可能背过 volatile 定义，但没有实际分析过并发问题。

Agent 可以在 Working Memory 中给 ProbeGap 排序、选择当前 Gap，并围绕它建立 Working Hypothesis。但临时猜测不能直接当成正式评估或 Evidence。

## 6. Working Memory 如何更新

一次 Agent 工作过程中，Working Memory 可以持续在内存中更新：

```text
Agent 选择当前 Gap
  ↓
形成一个工作假设
  ↓
调用题库、Rubric 或 Memory 查询工具
  ↓
把工具结果加入当前观察
  ↓
提高、降低或放弃原来的假设
  ↓
生成最终问题
```

不需要把每一次修改都写入数据库。

当 Agent 最终生成下一道问题时，只保存一次简洁的 Working Memory Snapshot，并与下一 Turn 一起提交。Snapshot 记录它基于哪个回答形成，例如 `basedOnTurnIndex`。

如果进程在中间崩溃，就从最近的 Turn、Assessment、Evidence 和上一份 Snapshot 重新运行。系统不需要恢复到某一次模型调用或某一个 Tool Call 的中间位置。

## 7. Rubric 查询为什么可以是真 Tool

Rubric 查询本身不是伪 Tool。关键要看模型是否真的拥有选择权。

如果 Java 已经确定唯一的 Rubric，并且每一轮都必须加载，那么它只是普通上下文装配，不需要让模型调用。

如果 Agent 可以根据当前回答自由决定：

- 是否需要查询评分标准；
- 查询哪个相关领域；
- 想区分哪些能力等级；
- 需要什么样的正反例；

那么它就是真正的 Agent Tool。

例如：

```text
候选人说：“volatile 可以保证线程安全”
  ↓
Agent 不确定应该按哪个能力层级继续验证
  ↓
调用 rubric_search：
“查询 volatile 可见性、原子性和 happens-before 的 L2/L3 区分标准”
  ↓
工具返回相关评分标准
  ↓
Agent 根据结果决定追问 i++ 为什么仍不安全
```

可以把能力分成两类：

- `rubric_get`：根据已经确定的 ID 读取数据，是普通服务；
- `rubric_search`：由模型决定是否查询、查询什么，是 Agent Tool。

Rubric Tool 的结果会进入本次观察，并影响下一步问题。最终 Assessment 只需记录实际采用的 Rubric 版本和条目引用，不需要持久化一次只读查询的执行状态。

## 8. 其他 Tool 的边界

一个能力只有同时满足以下条件，才应该成为 Agent Tool：

1. 是否调用需要模型根据语义决定；
2. 关键参数不能由 Java 直接从业务事实算出；
3. 结果会返回模型，并改变后续决定。

按照这个标准：

| 能力 | 处理方式 |
|---|---|
| 加载已经确定的 Skill | ContextAssembler 自动完成 |
| 根据固定 ID 读取 Rubric | 普通服务 |
| 按语义搜索相关 Rubric | Agent Tool |
| 按语义搜索题库 | Agent Tool |
| 按当前 Gap 搜索历史 Episode | Agent Tool |
| 用户提交代码后执行沙箱 | Application Service |
| 查询 Session 或配置 | 普通业务读取 |

只读 Tool 在一次 Agent Loop 中执行，不需要 Intent、Recovery Scheduler 或执行状态表。失败结果可以作为观察返回模型。

代码沙箱不同。它有真实外部副作用，必须使用稳定业务键并由 SandboxExecution 持久化，但不需要包装成通用 Agent Tool。

## 9. 模型与 Java 怎么分工

模型负责需要语义判断的策略：

- 当前优先验证哪个 Gap；
- 继续深挖还是切换维度；
- 是否需要查询 Rubric、题库或历史记忆；
- Tool 查询参数和调用顺序；
- 下一道问题的目标和内容；
- 在证据已经充分时建议结束。

Java 只负责硬约束：

- 用户和租户权限；
- Session 和 Turn 必须存在；
- 不能回答已经结束的 Session；
- 不能超过最大轮次；
- Target 必须属于当前 Plan；
- Tool 必须在允许列表中；
- Evidence 必须来自真实回答或工具结果；
- 沙箱隔离和稳定幂等；
- 数据库唯一约束和并发提交。

Java 可以拒绝非法提案，但不应在拒绝后偷偷替模型选择另一个 Gap。拒绝原因应返回 Agent，让它重新作出合法决定。

## 10. Episodic 和 Semantic Memory

Episodic Memory 继续保存过去真实发生的经历，包括问题、回答、评估、Evidence 和工具结果。它是可审计历史。

Semantic Memory 从多次 Episode 中总结长期能力和反复出现的问题。它是可以被新证据修订的认识，不是当前面试事实。

正式评估仍然只根据本场证据评分，不能因为历史能力画像而提高或降低当前评级。历史 Episode 可以用于避免重复问题；完整长期能力主要用于练习、成长报告和推荐。

## 11. 最终边界

这套设计只保留三类东西：

- 领域事实记录已经真实发生的事情；
- Working Memory 记录 Agent 当前在关注和准备验证的事情；
- Tool 为 Agent 提供当前上下文中缺少的信息。

最终原则是：

> 模型拥有面试策略和只读工具选择权；Java守住权限、预算、证据和副作用边界；Working Memory 保存当前认知，但不成为第二套业务数据库。
