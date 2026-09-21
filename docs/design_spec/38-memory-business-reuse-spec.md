# 三层记忆业务复用规格

> 状态：业务复用代码已实现；验收记录见 37 号指南。实际 PostgreSQL 迁移与真实模型的换场景质量尚未验证。
> 更新：2026-09-06。
> 依据：用户明确要求复用回答、评估、gap 和 L0～L4；优先业务价值，不为极低概率故障建设额外校验与恢复。
> 优先级：本规格取代旧版三层记忆目标及 36 号中冲突的记忆消费、长期画像设计。36 号其余 Agent Loop 规则继续适用；37 号记录实际改动和验证范围。

## 1. 一句话目标

记录用户在具体场景中的回答，在下次练习相关知识点时召回，换场景验证薄弱点，并从这些真实回答形成职位画像。

```text
问题 + 回答 → 现有评估：L0～L4、理由、证据、gap
                        ├─ Working：本场接下来验证什么
                        ├─ Episode：这次场景下发生了什么
                        └─ Semantic：这个职位下有哪些不足

下次练习：Semantic 提供重点 → Episode 提供经历 → Working 组织本场验证
```

三个层次共用正式事实和现有提交路径，不各自产生评级、缺口副本或任务状态。复用不等于将整个 WorkingMemory JSON 当成长期记忆回写。

## 2. 约束与非目标

- 正式评级只使用现有 `DepthLevel.L0`～`L4` 和 Assessor 的结果；不修改等级含义。
- 不新建“已掌握、待重验、独立证明”等能力枚举，不增加固定错误标签词表。
- gap、评估理由和后续验证意图使用已有事实及简短自然语言，不新造状态机。
- 不引入指纹、哈希、记忆版本校验、观察修订、记忆队列、专属 worker 或定时恢复。
- 不增加记忆专用模型调用。直接消费原始问答、已有评估理由和证据。
- Episode 每次回答新增一条经历；下次练习不会覆盖前一次回答或更改其评级。
- Semantic 暂时只做职位画像的查询投影，不是第二套能力判定引擎。
- 无评估表示未考察，不能写成 L0；有提示的回答保留当时条件，不能自动解释为独立掌握。
- 本规格不扩展代码执行、语音和其他异步任务，也不要求删除它们已有的恢复机制。

## 3. 唯一事实与复用边界

| 内容 | 复用的事实源 | 三层如何使用 |
| --- | --- | --- |
| 用户、会话、练习模式 | Session 与创建输入 | 查询范围及场景背景 |
| 职位归属与展示名称 | 已选 Skill 的 `skillId` 与名称 | 按职位组织画像，复用现有会话与 Skill 关联 |
| 知识点与当次目标深度 | Plan / Target，`TopicKey(skillId, focusId)` | 关联经历和组织练习 |
| 原始问题、回答、提示与轮次关系 | Turn | Episode 展示及后续出题参考 |
| L0～L4、理由 | Assessment | 当次评价、召回依据、画像展示 |
| 缺口、关闭依据 | ProbeGap / GapResolution / Evidence | 本场验证与历史不足说明 |
| 当前注意力、下一步意图 | WorkingMemory | 本场决策，不作为正式评分 |
| 一次回答的长期入口 | EpisodeFactEntity | 索引上述事实，不再复制一套观察 |

数据库保存正式事实；模型上下文是本次请求中的读取结果。共同的 Episode 查询投影同时供练习召回、记忆页面和画像使用，避免三个消费者各自解释等级和 gap。

## 4. Working Memory：本场待验证内容

保留现有 `WorkingMemory.focus` 和 `deliberation` 的主要职责：

- 当前 Target、本场 active gap、gap 优先顺序。
- 本场假设及对应证据引用；假设不变成长期能力标签。
- 下一步追问意图，例如“换库存场景验证锁竞争过程”。
- 本轮实际引用的历史 Episode，以数据库 ID 引用，不复制完整历史。

历史 Episode 通过工具结果或上下文提供正文，WorkingMemory 只保留使用意图及引用。沿用现有引用机制，移除已经没有消费者的能力版本引用；不新增引用注册表。

跨会话的历史 gap 只是参考，不能直接填进本场 `activeGapId`。本场评估发现相同不足时，使用本场正式 gap；不制造一条“历史 gap 已恢复”的任务。

WorkingMemory 沿用当前 Turn 快照保存与恢复方式。不增加长期 WorkingMemory 表，也不逐轮复制全部 Episode。

## 5. Episode：一次场景中的回答

### 5.1 最小读取内容

```text
EpisodeView
  episodeId / sessionId / turnIndex
  topic: skillId / focusId
  question / answer
  depthLevel / rationaleSummary
  gaps: 来源、缺失内容、已有关闭证据
  answerConditions: 当时的追问、提示及相关上下文
  answeredAt
```

以上是读取视图，不要求为每个字段新增数据库列。问答来自 Turn，评级和理由来自 Assessment，gap 来自现有关系，场景首先由原始问题表达。

不得仅为生成“场景摘要”或“回答摘要”再次调用模型。已有 `rationaleSummary` 足以表达评估结论；需要原话时读取回答和证据。

### 5.2 写入与更新

- 正式评估提交时，沿用同一回答事务创建 Episode，并关联本轮 Assessment。
- 无论本次选择 ASK 还是 FINISH，都保存本次经历。
- 同一回答重试不重复创建 Episode，沿用现有 session/turn 唯一约束和回答幂等。
- 后续练习新增 Episode。所谓“更新记忆”是增加新的真实经历，并重新读取画像，不重写过去的回答。
- 同场追问可补足原 gap，沿用已有关闭证据；跨场新的表现作为新的经历，不为了更新画像去修改旧会话。

### 5.3 召回

练习模式按当前用户、允许的练习范围和 `TopicKey` 查询已有 Episode，提供问题、表现、等级、gap 及当时条件。

第一版“相关”明确指同一目录知识点，不实现文本哈希、向量索引或跨知识点自动聚类。Agent 从历史场景中判断怎样换场景验证；不由代码通过关键词、指纹或新枚举裁决“迁移成功”。

读取沿用现有分页和模型上下文预算，不额外增加记忆专属阈值、检索轮次上限或独立性计数规则。旧题曝光仍可供避免原题重复使用。

## 6. Semantic：职位下的全局画像

### 6.1 输出与计算

画像按用户和 `skillId`（职位，例如 Java 开发、测试开发）组织，在该 Skill 下按 `focusId` 列出知识点表现；完整知识点标识仍为 `TopicKey(skillId, focusId)`：

- 最近一次正式评估的 L0～L4、时间、场景、来源 Episode。
- 对应的评估理由与 gap，明确它们属于哪次场景。
- 有明确职位要求时，展示其已有目标深度及本次表现的差异。
- 必要时展开其他历史 Episode，保留不同场景或提示条件下的差异。

第一版不生成平均等级、最高等级、全局掌握率或新的综合评分。画像中的“最近表现”不宣称该知识点永久达到该级别；历史 gap 也不自动变成当前开放任务。

没有回答记录的知识点显示“未考察”。没有职位目标深度时只展示已有表现和不足，不编造一个默认要求。

画像通过 Session/Plan/Episode/Assessment/Gap 查询计算。暂不新增画像生成模型、画像事件、聚合缓存或第二份持久化能力状态。

### 6.2 职位归属

Skill 已包含 Java 开发、测试开发等职位，直接复用现有 `skillId` 作为职位归属，展示名称读取 Skill。不新增 `roleName`、会话职位标签、职位枚举或 Skill 到职位的映射表。

会话和历史回答沿用已有 Skill 关联；每条经历通过其 `TopicKey.skillId` 进入对应职位画像。同场涉及多个 Skill 时，各条经历按自身 Skill 分组，不强行给整场另定一个职位。JD 原文继续保留，不用于推断另一套职位分类。

Skill 名称变化不改变基于 ID 的画像归属，也不改变历史评估。练习范围仍由用户选择的 Skill 与知识点决定，画像不能擅自扩大范围。

## 7. 生产与消费时序

### 7.1 开始练习

1. 从当前用户、所选 `skillId` 和知识点范围计算 Semantic 视图，供 Planner 选择重点。
2. 按计划相关知识点读取 Episode，提供曾问场景、回答表现和待验证内容。
3. Agent 产生新的场景题和本场 WorkingMemory；不照搬历史 gap ID。
4. 沿用现有创建事务保存计划、首题和 WorkingMemory。

### 7.2 回答与继续练习

1. 沿用原回答路径接受答案。
2. Assessor 使用当前问题、回答、量规及必要的本场提示上下文进行评估，不读取历史能力画像给当前回答定级。
3. 将新评估、gap 与已有 WorkingMemory 交给当前 Agent Loop。练习模式可读取历史 Episode，决定追问、换场景、切换知识点或结束。
4. 在现有回答提交事务中保存评估、证据、gap、Episode 和最终动作。无需额外记忆模型或事后整理步骤。
5. 下次查询 Semantic 时即可看到新事实，无需发送“画像已更新”事件。

当前实现会在轮次耗尽时跳过决策模型。Episode 写入必须绑定评估提交，不能依赖下一题的生成或下一份 WorkingMemory，否则最后一题会漏记。

### 7.3 模式区别

EVALUATION 同样记录回答和正式评估，并可进入职位画像，但本场规划、出题和评分不消费历史能力结论。PRACTICE 消费 Semantic/Episode 安排练习；本次评分仍依据本次表现。

### 7.4 伪代码

```java
// 创建：只有练习模式使用历史安排问题。
var history = practice ? memory.query(owner, skillId, scope) : emptyHistory();
var planAndQuestion = planner.generate(currentInput, history);
creation.commit(planAndQuestion);

// 回答：只有一份正式评估，不再调用记忆整理模型。
var assessment = assessor.evaluate(currentQuestion, answer, currentRubric);
var decision = decideOrFinish(assessment, currentWorkingMemory, practiceHistory);
answerTransaction.commit(() -> {
    saveAssessmentEvidenceAndGaps(assessment);
    saveEpisodeForAnsweredTurn();
    applyDecisionAndSaveNextTurnIfAny(decision);
});

// 画像与历史都读取已经提交的事实。
var episodes = memory.findEpisodes(owner, topics);
var profile = memory.projectSkill(owner, skillId);
```

伪代码省略现有答题幂等、执行令牌和事务边界的细节，不表示删除它们，也不为三层记忆重新实现这些机制。

## 8. 校验与恢复：先证明需要，再决定是否保留

每次准备新增或保留一项校验、重试、恢复、锁、版本或兜底前，先回答：

1. 不加它，正常业务与明确验收用例能不能通过？先读调用者和已有约束，必要时暂时移除做对照。
2. 它防止什么具体错误？有真实调用路径、已复现问题或外部边界要求吗？
3. 原回答事务、数据库约束或其他入口是否已经处理？能否直接复用？
4. 出错后显示错误、由用户重试已有答题流程，是否已经足够？
5. 收益是否值得新增代码、状态、测试与排错成本？

没有明确收益就不加。不能只因为“理论上有可能”建设机制，也不能因为旧代码和旧测试存在就继续保留。正常用例通过不是取消用户归属或正式数据约束的理由；这类必要边界用对应反例验证。

| 情况 | 处理 |
| --- | --- |
| 记忆查询属于谁 | 复用当前认证用户和 owner 查询，不接受模型提供用户 ID |
| 同一回答重试 | 复用现有回答幂等与数据库唯一约束，不新增记忆幂等键 |
| 写入中断 | 复用回答事务回滚及原答题重试路径，不建立 Episode 恢复队列 |
| 模型伪造正式评级或证据引用 | 复用现有评估边界，不新增一套记忆校验器 |
| 已经校验的内部 DTO 再逐字段校验 | 默认删除，除非存在新的不可信入口 |
| 历史场景是否“足够不同” | 给 Agent 原始场景作参考，不写指纹、相似度门禁或固定次数规则 |
| 画像偶尔读到刚提交前的状态 | 下次读取更新，不为这个低成本现象增加版本协调或后台补偿 |

不吞异常，不用空记忆、伪造摘要或默认 L0 假装成功。错误在已有入口明确暴露，不为删除恢复系统另造通用恢复系统。

## 9. 必要注释与代码组织

注释保留业务原因和容易误改的边界，尤其是：

- 为什么历史只用于练习安排，不能影响本次评分。
- 为什么最后一题仍要保存 Episode。
- 为什么历史 gap 不能直接成为本场 active gap。
- 为什么未考察不是 L0，以及提示条件必须随历史保留。
- 为什么旧迁移和历史问答暂时保留。

不注释显然的赋值或给每个 getter 写说明，不保留已经失效的指纹、worker、修订设计注释。专属小类型继续内嵌；新增接口、Bean 或包装必须说明它承担的实际职责。

目标包结构按业务职责组织，沿用现有 `adaptive` 根路径：

```text
core/context/       WorkingMemory 与当前上下文，沿用现有位置
memory/episode/     经历事实、实体、Repository、写入与查询
memory/semantic/    职位画像查询投影
tool/              memory_recall 的工具边界
```

`episode/enrichment` 与 `episode/tag` 随消费者迁移删除；题目曝光仍按实际消费者保留。Episode 查询结果等单 owner 小 record 内嵌到使用它的类；不为这次重构新增通用 memory framework、依赖分组 Bean 或纯转发 service。包目录表示职责，不要求每个职责再拆 domain/application/infrastructure。

## 10. 改动范围与迁移

| 范围 | 目标改动 |
| --- | --- |
| WorkingMemory、ContextAssembler、快照读取和 Agent 输出 | 沿用当前注意力模型，历史引用改为 Episode，不保存另一份能力评级 |
| AssessmentDecision、评估流程 | 复用现有字段和 L0～L4；不额外调用模型生成记忆 |
| 回答提交、EpisodeFactPersistence | 保存同一份评估关系，停止发布 enrichment 事件 |
| memory_recall、PracticeMemoryService | 读取 Episode 和职位查询投影，删除旧 CapabilityBelief 路径 |
| Episode 查询与展示 DTO | 联查问答、评估、gap 和必要上下文，代替后台生成的摘要/标签 |
| Semantic 读取与页面 | 改为职位下的事实画像，撤掉旧贡献统计和自定义能力状态的当前展示 |
| enrichment、tag、observation 相关实现 | 消费者切换后删除专属模型、提示词、队列、恢复、标签与观察修订代码 |
| 会话/API/前端 | 复用已有 Skill 选择和关联展示职位画像，不新增职位字段；移除不再适用的记忆整理重试、观察撤销和重建入口 |

不把改动限定为后端删类：旧页面、公开 DTO、HTTP 入口、测试和文档必须随生产消费者一起迁移。移除公开接口时明确列出协议变化，不留只返回成功的空实现。

实施分三批：先切换查询与页面消费，再切换写入并删除死链路，最后依据真实数据和读取者核对结果做增量数据库迁移。批次间保持可运行，不同时维护两套正式评级。

保留历史 Turn、Assessment、Evidence、gap、Episode 和当时量规。旧观察/标签/统计表先停止读写，删表删列前核对实际历史数据及剩余消费者；不改写已执行的 Flyway 文件，不为一次迁移保留长期兼容框架。

### 10.1 风险与接受的取舍

| 风险 | 处理与接受的边界 |
| --- | --- |
| 最近一次表现受场景或提示影响 | 同时展示来源、时间和提示条件；接受它只是最近表现，不增加能力融合算法 |
| 同一知识点用了不同目录 ID | 第一版可能漏召回；接受目录内关联，不建设自动语义归并 |
| 同名 Skill 使用不同 ID | 按实际 `skillId` 分组，不按名称自动合并；复用现有目录管理，不建设另一套职位归一化机制 |
| 旧标签、观察或统计被页面和 API 使用 | 消费者随查询一起切换，明确协议变化；历史表删除单独核对 |
| 联查增加读取开销 | 先复用索引与分页，以实际查询结果判断是否优化，不预建缓存和异步画像 |
| 删除后台整理后没有生成式摘要 | 原始问答与已有评估理由满足当前业务；接受少一层润色 |
| Agent 仍可能换汤不换药地出题 | 用历史场景和真实模型验收评估效果；不增加指纹门禁或专属恢复流程 |

## 11. 验收标准

| 场景 | 必须看到的行为 |
| --- | --- |
| 回答 synchronized 机制不完整 | Episode 能读到原题、原回答、现有评估等级及相应 gap，没有第二份评级 |
| 本场继续追问 | WorkingMemory 聚焦本场待验证 gap，有简短追问意图 |
| 下一场练习同知识点 | 能召回旧场景作为参考，Agent 可提出不同场景的问题 |
| 新场景回答改善 | 新增 Episode，旧经历不覆盖，画像读取到新的真实表现 |
| 有提示后答对 | 保留提示上下文，不生成新的“独立掌握”等级 |
| 本次未考察某知识点 | 不制造 L0，不宣称对应不足已解决 |
| 本场最后一题 | 即使直接 FINISH，回答、评估和 Episode 仍完整保存 |
| 相同回答重试 | 不重复推进，不重复生成 Episode |
| 用户或 Skill 不同 | 不串用户记录；Java 开发与测试开发按各自 `skillId` 分组，同场多 Skill 的经历各归其组 |
| Skill 名称变化 | 画像仍按原 `skillId` 关联，不因展示名称变化产生新分组 |
| 记忆或数据库读取失败 | 明确暴露错误，不伪造空结果或后台成功 |
| 删除旧整理链路 | 练习创建、答题、召回、画像仍通过；没有遗留 worker、轮询或无消费者状态 |

结构/事务回归使用自动化测试，后端测试每次硬超时 60 秒，超时则合理分批，不增加业务恢复代码让测试通过。

“换场景后问题质量是否合适”需要真实模型场景验收；单元测试只能证明历史输入被正确消费，不能把模型替身的固定输出算作语义效果证据。

## 12. 已实施的协议与数据变化

- `GET /api/adaptive-agent-interviews/me/memory` 返回 `skills`（职位下的知识点与最近 Episode）及问答分页；旧 `topics.evaluation/practice/beliefs`、整理状态和 `ancestors` 被移除，前文直接内嵌在每条 Episode 的 `priorTurns`。
- 删除 `/me/memory/episodes/{episodeId}/enrichment/retry`，以及 `/me/memory/observations/{revisionId}` 的读取、`retract` 和 `rebuild` 操作；上述路径前缀均为 `/api/adaptive-agent-interviews`。没有空成功兼容接口。
- `memory_recall` 仅接收 `targetId`，删除固定 `purpose` 用途枚举；EVALUATION 只返回曝光问题，PRACTICE 返回相关原问答。沿用原有近期读取数量与整体模型输入预算，不再增加记忆专属裁剪或版本门禁。
- 首题输出新增 `adoptedEpisodeRefs`，只允许引用本次规划输入提供的经历；后续沿用 `adoptedSourceRefs`。Working 的既有 `adoptedObservationRefs` 字段保存 `episode:ID`，跨请求保留 Episode，清除请求内工具引用，不复制第二份记忆。
- `AssessmentContext.priorTurns` 与 Episode 共用原始问答投影；只提供本场前文用于理解提示，不传跨场历史等级。
- V20261006 只解除停用状态字段的非空约束，不删历史表列；旧 Flyway 文件没有改写。代码不再读写这些状态，也不回填虚假的整理成功。
