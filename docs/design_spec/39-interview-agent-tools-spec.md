# 面试 Agent 内部工具扩展规格

> 状态：设计提案，未实施；本次仅记录工具设计，不代表工具已注册或通过运行验证。
> 更新：2026-09-12。
> 依据：内部工具选型及 Java 业务代码改错题联动讨论；优先复用现有业务数据，暂不考虑 MCP。
> 上游：[面试 Agent 的运行方式](../design/03-agent-loop-and-working-memory.md)、[36 号 Loop 规格](./36-agent-loop-working-memory-spec.md)、[38 号记忆业务复用规格](./38-memory-business-reuse-spec.md)。

## 1. 目标与范围

让后续出题 Agent 按需取得当前上下文缺少的材料，结合岗位、项目、题库场景和本场正式评估决定下一道问题。

优先设计 `interview_material_read` 和 `question_search`，其次考虑 `assessment_read`。这三个工具有现成的数据基础；“可以接入”指可以复用现有查询和工具执行链，不代表只添加工具名称就完成业务闭环。

Java 业务代码改错题与上述工具共用一条决策 Loop；本规格定义材料、素材、旧任务和评估的按需读取，[40 号规格](./40-java-code-repair-spec.md) 定义题目、提交及正式评估。新增 `code_task_read` 设计，用于回看本场较早的代码任务和提交；它依赖 40 号规格的正式事实，不能宣称当前即可启用。

`knowledge_search` 仍为有前置条件的候选项。本规格不扩展 MCP 或判题入口，不恢复已删除的推荐生成、曝光向量召回、记忆整理和后台补偿链路。

本轮只调整规格及文档索引。代码实现、数据库迁移、模型质量验证均未开展。

## 2. 当前代码事实

以下为 2026-09-12 工作区源码核对结果，不表示线上配置或数据已验证。

| 现状 | 对工具设计的意义 | 代码依据 |
| --- | --- | --- |
| 主循环白名单只有 `rubric_search`、`memory_recall` | 新工具需要注册、参数说明及实际消费者 | [ContextAssembler][context-assembler] |
| 决策上下文包含计划覆盖、问答、固定 Skill 和 WorkingMemory，不含简历/JD 原文 | 材料读取有明确的信息增量 | [AgentContext][agent-context] |
| Session 已保存简历/JD，历史读取会带出两者 | 材料工具可以直接读取本场已保存文本 | [会话读取][session-reader] |
| 已有 `interview_question` 向量索引及 ACTIVE 题库记录 | 问题检索无需新建索引生产链 | [题目索引][question-indexer]、[题库实体][question-entity] |
| 题库题目 `skillId` 固定为 `knowledge-base` | 不能直接用计划的职位 Skill ID 过滤题库 | [题库实体][question-entity] |
| Coverage 主要提供等级、gap 内容和 Evidence ID，正式评估另有理由与证据详情 | 评估详情读取有增量，但比材料和题库检索优先级低 | [CoverageView][coverage-view]、[评估仓储][assessment-repository]、[证据仓储][evidence-repository] |
| 本轮评估在下一题决策完成后统一提交，决策中有临时负 ID | 不能将本轮未提交引用直接用于数据库查询 | [回答决策][answer-decision]、[临时引用][pending-references] |
| 下一题提交处理 Episode 引用和 Rubric 快照；题库来源尚未接通 | 新工具结果中的来源 ID 不等于已完成持久化 | [回答提交][answer-transaction] |
| Turn 尚无 40 号规格的任务根引用与独立代码答案 | `code_task_read` 须在代码题事实落库后接入，不能读取旧仓库分析产物充当新任务 | [Turn 实体][turn-entity] |

## 3. 工具选择原则与优先级

沿用上游设计：模型决定是否补充信息、补充什么信息，并在得到结果后重新决定问题。Java 已经确定且每轮必需的读取继续由普通代码完成。

`interview_material_read` 的材料类型、`assessment_read` 的历史轮次由模型根据当前问题选择。这是本提案将它们作为可选工具的依据；不能仅因为底层有 Repository 方法就包装成工具。若实际消费者总是读取固定材料或固定轮次，应重新比较直接上下文装配的成本。

| 优先级 | 工具 | 典型触发 | 预期变化 |
| --- | --- | --- | --- |
| 高 | `interview_material_read` | 需要核对候选人的项目经历或岗位要求 | 问题围绕真实项目、职责和约束展开 |
| 高 | `question_search` | 已明确考察目标，但需要具体场景或不同问法 | 使用题库素材组织一道适合本场的问题 |
| 中 | `assessment_read` | 回到之前的维度，需要确认当时判断的依据 | 依据已有正式证据决定如何继续验证 |
| 中，依赖代码题事实 | `code_task_read` | 需要回看本场较早代码题或对照两次修订 | 取得准确的原题和指定轮次代码，再决定是否继续验证 |

这些工具供后续决策 Loop 按需调用。Planner 继续一次生成计划和首题；Assessor 继续根据本场回答及必要前文形成正式评估，不在这里增加模型调用或评分入口。

暂不新增 `skill_read`、`gap_list`、`turn_history`：固定 Skill、开放 gap 和本场问答已经进入上下文。代码题的历史轮次保留题型、根引用、题面和说明，较早的完整代码按需读取；当前关联任务及最近提交仍直接装配，边界见 §6.3。

## 4. 优先工具契约

### 4.1 `interview_material_read`

**职责：** 读取本场创建时保存的简历或 JD 文本，辅助出题。材料中的能力声明仍是待验证背景，不能自动成为能力 Evidence。

输入示例：

```json
{"source":"resume"}
```

| 字段 | 契约 |
| --- | --- |
| `source` | 必填，只接受 `resume` 或 `jd` |

成功结果的 `data` 包含 `source` 和 `text`。文本来自当前会话的已保存字段，不重新解析上传文件，不调用模型生成材料摘要。

读取与校验：

- 会话、候选人和租户身份从 `ReadToolRequest.context` 获取，模型不能指定另一用户或会话。
- 复用 Session 仓储中按 candidate/tenant 归属读取的路径，只返回所选文本，不向模型暴露整份实体。
- 缺失或非法 `source`、未知参数通过现有校验 Observation 返回；读取异常显式失败。
- 历史记录中材料为空时明确返回 `TOOL_EMPTY`，不生成默认简历或 JD。
- 沿用现有模型输入预算；不静默截断材料或新增材料专属兜底摘要。超出预算时由已有错误路径暴露。

业务示例：考察幂等性时，先确认简历中是否涉及订单与支付回调，再围绕候选人实际描述的链路提问，不编造流量、职责或技术栈。

### 4.2 `question_search`

**职责：** 从现有题库查找出题素材。最终题目仍由 Agent 结合当前 Target、gap、回答和曝光历史组织。

输入示例：

```json
{"query":"缓存失效与数据库更新并发"}
```

| 字段 | 契约 |
| --- | --- |
| `query` | 必填、非空字符串，表达需要的考察场景或问题 |
| `difficulty` | 可选；提供时为非空字符串，沿用现有题库值，不新建难度分类 |

成功结果的 `data.hits` 返回 `questionId`、`question`、`topicSummary`、`category`、`difficulty`。参考答案、解析和完整题库实体不随结果返回；评分量规继续由现有 `rubric_search` 承担。

查询过程：

1. 使用已有 `VectorStore`，按 `document_type = interview_question` 检索候选。
2. 用返回的题目 ID 批量回查数据库，确认记录仍存在且为 ACTIVE；指定难度时按数据库值筛选。
3. 保留候选相关性顺序，返回当前可用题目；复用 `ToolProperties` 已有题库结果数和相似度配置。
4. 无可用命中返回 `TOOL_EMPTY`。向量服务或数据库异常返回失败，不用低质量默认题模拟命中。

查询范围沿用当前共享审核题库的适用范围。主题和分类用于相关性匹配，不能将计划中的 `java-backend` 等 Skill ID 直接匹配题库固定的 `knowledge-base`。

Agent 可参考近期曝光和本场问答避免重复。这里不增加题目指纹、哈希或相似度去重门禁，也不恢复旧推荐生成服务。

用于 Java 改错题时，`query` 可描述“库存预留的并发问题”等考察点；题库只提供问法或技术考点，实际业务背景仍取自本场 JD / 简历或明示假设。现有索引没有改错题专属分类，不增加没有数据支撑的 `language`、`questionType` 过滤，也不把命中解释为已生成完整代码任务。

**采用来源：** 命中可提供基于现有题目 ID 的 `question:<ID>` 引用，只有实际用于下一题的来源才进入 `adoptedSourceRefs`。它表示出题来源，不表示候选人已证明相应能力。

来源保存还需补齐第 6 节的提交与恢复链路；不能把返回题干和 ID 当作整个工具已经交付。

### 4.3 `assessment_read`

**职责：** 回看本场某轮已经提交的正式评估及证据，不重新评分，不查询跨场能力画像。

输入示例：

```json
{"turnIndex":3}
```

| 字段 | 契约 |
| --- | --- |
| `turnIndex` | 必填、正整数，指向当前会话中存在的轮次 |

成功结果的 `data` 包含 `assessmentId`、`turnIndex`、`targetId`、`depthLevel`、`rationaleSummary` 和 `evidences`。证据保留既有 ID、类型、原句及对应来源；代码或沙箱证据沿用既有来源字段，不伪造文字引用。

联动 40 号规格后补 `questionType`；有关联任务时带 `codeTaskTurnIndex`，代码改错评估另带该次正式 `codeReview`。普通文字题不带这两项，关联代码的文字追问只带根引用。Evidence 保留原文与经过校验的 locator；代码全文由 `code_task_read` 按同一 `turnIndex` 读取。

读取与一致性：

- 从当前会话和 `turnIndex` 定位 Assessment，再按该 Assessment 读取 Evidence；沿用会话归属检查。
- 等级、理由和证据必须来自同一次正式评估，不选择最高等级后拼接另一轮理由。
- 轮次不存在属于参数校验失败；轮次存在但正式评估尚未提交，明确返回 `TOOL_EMPTY` 并说明原因，不解释为 L0。
- 第一版不读取临时负 ID，不为本轮尚未提交的评估增加 Pending 状态、轮询或提前写库。
- 若需要让决策模型查看本轮完整评估，应另行复用请求内已有 `AnswerAssessment`；不能建立第二份正式评估或改变原提交顺序。
- 已有记录读取异常是工具失败，不能作为“没有评估”返回。

该工具仅补充理由和证据详情。当前问答和开放 gap 已经在上下文中，不借此再包装整场历史查询。

### 4.4 `code_task_read`

**职责：** 读取本场某个已发布轮次关联的代码任务和该轮已接受答案。工具只查事实，不生成代码、不评审修复、不创建新轮次。

```json
{"turnIndex":4}
```

`turnIndex` 必填、正整数，选择原始代码题、某次修订或有关联任务的文字追问；会话及 owner 来自 `ReadToolRequest.context`。成功结果：

| `data` 字段 | 契约 |
| --- | --- |
| `codeTaskTurnIndex` | 服务端解析得到的本场原始任务轮次 |
| `originalQuestion` | 原始任务轮的业务场景与任务说明 |
| `codeTask` | `initialCode / requirements / assumptions`，不返回 `reviewGuide` |
| `turn` | 指定轮次的 `turnIndex / questionType / question / answer / submittedCode`；无说明或无已接受代码时相应字段为 null |

- 指定轮次不存在是参数错误；存在但未关联代码任务时明确返回 `TOOL_EMPTY`。任务引用损坏或数据库读取失败属于错误，不能解释为“没有任务”。
- 按当前会话和 owner 读取指定轮，再解析原始根；不能传另一会话 ID、仓库 ID 或任意文件路径。代码只取数据库已接受原文，不读取浏览器草稿。
- 返回的代码严格属于指定轮次。文字追问自身无代码时保持 null，不能偷偷替换为后来的修订；需要比较时由 Agent 分别选择两个轮次读取。
- 本轮已在上下文中的任务和提交不要求再调用工具。较早代码的读取提供明确增量，不能为了增加工具调用把当前题从上下文移走。
- 沿用 ToolGateway 的结果和资源边界；代码不裁剪、不改写、不经模型摘要。超出已有预算时显式失败。

`adoptableSources` 为空：复用原任务通过 ASK 的 `codeTaskTurnIndex` 表达，服务端校验归属并持久化引用；不增加 `code-task:*` 来源协议、工具日志或任务镜像。正式评估继续从该轮原始答案生成 Evidence，读取行为不产生能力证据。

## 5. 有前置条件的候选工具

| 工具 | 预期返回 | 可复用能力 | 前置条件与未解决项 |
| --- | --- | --- | --- |
| `knowledge_search` | 知识库原文片段、文档和片段来源 | [KnowledgeBaseVectorService][kb-vector] | 当前会话没有选用知识库的关联，需明确共享资料或本场指定资料的检索范围；现有检索异常回退行为也需按错误契约核对 |

`knowledge_search` 应返回原始检索资料。不能直接包装 [KnowledgeBaseQueryService.answerQuestion][kb-query]，因为该入口还会生成回答并更新问答统计，具有额外模型调用和写入。

该候选项不因写入本规格就加入主循环白名单，不在本次记录中承诺可立即运行。旧仓库分析专项已撤回，不再保留依赖分析产物的项目读取或代码搜索候选工具。

## 6. 接入与来源保存

### 6.1 工具执行

沿用现有执行链：

```text
Agent 输出 CALL_READ_TOOLS
  → ToolGateway 校验当前白名单
  → ReadOnlyAgentTool.validate / execute
  → DecisionObservation
  → Agent 再决定 CALL_READ_TOOLS、ASK 或 FINISH
  → 原有短事务保存正式结果
```

每个工具作为真实输入边界实现 `ReadOnlyAgentTool`，注入现有查询依赖。简单读取直接使用已有 Spring Data 接口，不增加纯转发 Service、依赖分组 Bean 或通用工具框架。

接入必须同时覆盖 Spring 装配、`allowedReadTools`、决策提示词中的参数/结果/使用时机，以及对应输入校验。仅有名称列表不足以让模型正确构造参数。

返回结果沿用 `Success / Empty / Timeout / Error` 和现有 Observation。参数错误回流模型，业务权限错误和执行异常沿现有路径明确暴露；不将异常归为“无资料”，不增加隐藏重试或默认成功结果。

### 6.2 来源与事务

- 工具只读已有事实，不创建 Assessment、Evidence、gap 或 Episode；工具材料不自动成为候选人能力证据。
- WorkingMemory 只保存已支持的事实引用和短期意图，不复制材料全文、题库答案或正式评估。
- 请求内 Observation 可重算，不新增工具执行状态表、日志式恢复、队列、租约或补偿任务。
- 原回答幂等、执行令牌、短事务提交和旧执行者隔离继续沿用；工具调用期间不持有长数据库事务。
- 现有 `adoptedSourceRefs` 校验可接受本轮成功 Observation 的来源，但跨轮保留与保存尚非通用实现；当前主要支持 Episode 引用和 Rubric 快照。
- `AdaptiveAgentTurnEntity` 存在单值 `questionSourceId` 和对应写入分支，但当前 Loop 的 ASK 提交未传入题库来源。需追踪从模型采用、正式提交到历史读取的完整链路。
- 题库单一来源优先评估复用现有字段。若允许采用多条题目来源，需明确保存语义，不能默默取第一条或丢弃其余来源。本规格不据此预设新表、哈希或版本状态。
- 新来源只有在相应恢复路径支持后才能作为跨轮引用；不能仅放进 WorkingMemory 就宣称支持恢复。

### 6.3 与 Java 改错题联动

| 工具 | 在代码题中的用途 | 进入正式事实的方式 |
| --- | --- | --- |
| `interview_material_read` | 核对 JD / 简历中的业务背景与约束 | 题面依据本场材料生成；声明不成为能力证据 |
| `question_search` | 寻找适合当前目标的技术考点或问法 | 实际采用的 `question:<ID>` 按 §6.2 保存 |
| `rubric_search` | 查找与并发、事务、边界处理等目标相关的现有量规 | 保存采用的量规快照；题内 `reviewGuide` 仍由出题提案给出 |
| `memory_recall` | 帮助练习选题、避免重复，沿用模式过滤 | 采用既有 Episode 引用；跨场画像不进入当前评分 |
| `code_task_read` | 回看旧题、旧代码或比较指定修订 | 复用题目记录 `codeTaskTurnIndex`，不重存原任务 |
| `assessment_read` | 回看该轮修复判断及证据，决定如何追问 | 使用原 Assessment；不重新评分或关闭 gap |

首题 Planner 已有 JD / 简历，继续一次生成计划与首题。后续 Loop 常驻 Plan、Skill、Coverage、问答索引、当前轮关联的原任务与最近代码提交，以及请求内刚生成的审阅；材料全文和较早代码详情通过上述工具按需补齐。同一材料已在本次上下文时直接使用，不再默认每轮注入两份全文；这属于明确的角色投影，不是超限时的截断策略。

一个可能的选择过程是：

```text
发现缺少业务背景 → interview_material_read → 确认库存场景
需要考点或量规 → question_search / rubric_search → ASK(CODE_REPAIR)
收到代码 → 现有 Assessor 评估 → 当前结果直接进入决策 Loop
需要对照早期代码 → code_task_read + assessment_read → 再决定修订或追问
```

工具是否调用、参数和顺序均由 Agent 决定，信息足够即可直接 ASK / FINISH。生成仍由 ASK 提案完成，评分仍由 Assessor 完成；不再包装一次模型生成或评分为 `code_generate` / `code_review` 工具，也不增加代码专属工具链状态机。

`PRACTICE` 可据反馈提出引用原任务的新修改轮；`EVALUATION` 可读取本场正式审阅来设计文字追问，但 Tool Observation、评估参考和详细修复提示不进入进行中的候选人响应。Assessor 的当前答案、原任务和必要前文由服务端直接装配，不通过这些出题工具取得跨场评估。

## 7. 验收要求

以下为实施时的验收标准，本次文档提交未执行这些业务验证。

| 场景 | 预期结果 |
| --- | --- |
| 模型请求简历或 JD | 返回当前 owner、本场保存的对应原文，不读取另一会话 |
| 请求非法材料类型、未知参数或不存在的轮次 | 明确校验失败，经既有 Observation 回流 |
| 材料为空或轮次评估尚未提交 | 明确说明缺少哪项事实；不伪造文本、等级或成功命中 |
| 材料超过已有模型输入预算 | 显式暴露预算错误，不静默截断或生成摘要 |
| 题库语义命中已停用或已删除记录 | 数据库回查后排除，不依据旧向量宣布其仍可用 |
| 计划 Skill 与题库固定 Skill 不同 | 仍能通过主题和语义检索相关题目，不使用错误的 Skill 等值过滤 |
| 提供难度条件 | 返回题目符合现有数据库难度值，条件不被静默忽略 |
| 读取已提交评估 | 等级、理由、证据保持同一 Assessment 来源，仅限本场 |
| 读取本轮尚未提交的评估 | 不查询临时负 ID，不提前写库，不影响当前已接受答案 |
| 数据库或向量检索失败 | 工具显式失败，不伪装成无历史或默认题目 |
| ASK 采用题库来源后恢复会话 | 问题与约定的采用来源可追溯；未采用来源不持久化 |
| Agent 认为当前信息已足够 | 可以直接 ASK 或 FINISH，没有每轮必调新工具的要求 |
| PRACTICE / EVALUATION 使用新工具 | 保持当前模式边界；历史画像不能通过新工具进入当前评分 |
| Java 改错题调用材料、题库和量规工具 | 技术素材服务于本场业务背景；不新增虚构分类或返回执行结果 |
| 按轮次回看代码与评估 | 代码、说明、locator 和评审对应指定正式轮次；原始任务引用同源 |
| 指定文字追问或尚未提交的代码轮 | 如有关联任务则返回任务；本轮没有代码就明确为 null，不代取另一轮 |
| 本轮评估未提交、立即连续修改 | 直接使用请求内代码与审阅，无提前写库、临时 ID 查询或重复评分 |
| EVALUATION 内部回看详细评审 | 可用于追问，候选人 API / SSE 不泄漏工具原始结果和修复提示 |

实现验证优先覆盖真实查询范围、状态变化、未提交评估和来源恢复。模型场景验证分别检查项目背景是否有原文依据、题库素材是否服务于当前目标，以及评估回看是否提供额外依据；不以调用次数增加证明工具有价值。

## 8. 待明确的设计项

1. `question_search` 采用多条题目来源时的正式保存与历史读取语义。单值历史字段不能自行决定模型只能采用一条。
2. 新的非 Episode 来源是否需要跨轮再次引用；若没有实际消费者，不为它们扩展通用记忆存储。
3. `knowledge_search` 的资料范围与检索错误契约。条件明确前，保留为候选设计。

[context-assembler]: ../../app/src/main/java/interview/guide/modules/interview/agent/adaptive/memory/ContextAssembler.java
[agent-context]: ../../app/src/main/java/interview/guide/modules/interview/agent/adaptive/core/context/AgentContext.java
[session-reader]: ../../app/src/main/java/interview/guide/modules/interview/agent/adaptive/persistence/session/AdaptiveInterviewPersistenceService.java
[question-indexer]: ../../app/src/main/java/interview/guide/modules/interview/agent/adaptive/tool/QuestionBankVectorIndexer.java
[question-entity]: ../../app/src/main/java/interview/guide/modules/knowledgebase/model/KnowledgeBaseQuestionEntity.java
[coverage-view]: ../../app/src/main/java/interview/guide/modules/interview/agent/adaptive/core/context/CoverageView.java
[assessment-repository]: ../../app/src/main/java/interview/guide/modules/interview/agent/adaptive/persistence/assessment/AdaptiveAgentAssessmentRepository.java
[evidence-repository]: ../../app/src/main/java/interview/guide/modules/interview/agent/adaptive/persistence/assessment/AdaptiveAgentEvidenceRepository.java
[answer-decision]: ../../app/src/main/java/interview/guide/modules/interview/agent/adaptive/application/AdaptiveAnswerDecisionService.java
[pending-references]: ../../app/src/main/java/interview/guide/modules/interview/agent/adaptive/application/PendingAssessmentReferences.java
[answer-transaction]: ../../app/src/main/java/interview/guide/modules/interview/agent/adaptive/persistence/session/AdaptiveAnswerTransactionService.java
[kb-vector]: ../../app/src/main/java/interview/guide/modules/knowledgebase/service/KnowledgeBaseVectorService.java
[kb-query]: ../../app/src/main/java/interview/guide/modules/knowledgebase/service/KnowledgeBaseQueryService.java
[turn-entity]: ../../app/src/main/java/interview/guide/modules/interview/agent/adaptive/persistence/session/AdaptiveAgentTurnEntity.java
