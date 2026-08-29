# 14. 评估追问缺口（probeGaps）方案

> 维护：Agent；上游设计决策以 `docs/design/` 为准。
>
> 状态：现状已落地；目标语义已按 2026-08-29 Working Memory 方向校准。
> 范围：候选人侧自适应面试的「回答评估 → 下一轮出题」链路，不涉及企业 MCP、租户链路、报告评分体系改造。

## 1. 目标

ProbeGap 是正式评估确认的“尚缺哪类证据”，不是一次 Prompt 的临时字段。它必须能够被后续多轮选择、关闭和追溯：

- 评级结论继续用于画像和报告；
- ProbeGap 与来源 Assessment/Turn 一起持久化；
- Agent 从当前会话全部 open Gap 中自主选择下一步关注点；
- 评估判断以 Skill 知识基线为依据，并用少量 few-shot 校准输出形态。

## 2. 核心数据模型

### 2.1 ProbeGap

当前共享值对象只有 `anchor/missingPoint`；目标领域事实至少需要：

```java
ProbeGap
  gapId / sessionId / targetId
  sourceAssessmentId / sourceTurnIndex
  anchor / missingPoint
  closedByAssessmentId? / closureReason?
```

约束：

- `anchor`：必须逐字来自本轮候选人回答；
- `missingPoint`：只描述“回答提到 X，但未说明 Y”，不含评级语言。
- Gap 创建后保持可追溯；后续 Assessment 明确证明缺口已解决或不再适用时，记录关闭事实。

### 2.2 AssessmentProposal / AssessmentDecision

评估输出增加 Gap 创建/关闭提案：

```java
List<ProbeGapProposal> openedGaps
List<GapClosureProposal> closedGaps
```

- 可为空，空只表示本轮没有新确认的缺口；
- 不设置“最多 2 条”这类面试策略上限；通用模型输出大小边界仍然有效；
- 校验通过后与 Assessment/Evidence 同一短事务持久化。

### 2.3 AssessmentRequest

增加：

```java
String skillReferenceSection
```

由当前 Target 的固定 Skill 自动装配，作为评估 Agent 的知识基线；不通过 Agent Tool 加载。

### 2.4 InterviewerContext

增加：

```java
List<ProbeGapView> openGaps
```

ContextAssembler 提供当前 Plan 内全部 open Gap 及其 Evidence 引用。维度切换只改变 Working Memory 的 `activeTargetId/activeGapId`，不删除正式 Gap。

## 3. 数据流

```text
submitAnswer
  ↓
currentDimension.suggestedSkill
  → InterviewSkillService.getEvaluationReferenceSection(skillId)
  → AssessmentRequest.skillReferenceSection
  ↓
DepthAssessmentAgent.assess
  → AssessmentDecision {
      depthLevel / confidence / rationaleSummary,
      evidenceQuotes,
      openedGaps / closedGaps
    }
  ↓
Assessment / Evidence / ProbeGap → 同一短事务持久化
  ↓
CoverageProjector → 全部合法 Target 和 open Gap
  ↓
WorkingMemorySnapshot + InterviewAgentLoop
  ↓
Agent 自主选择 Gap、切换 Target、调用只读 Tool或结束
```

## 4. Skill 知识与 few-shot

### 4.1 Skill reference

Skill service 按 `skill.meta.yml` 的 `ref` 加载 references。加载失败明确暴露，不使用 `Safe` 方法静默返回空基线。通用 AgentContext/token 边界负责总输入大小，不再为 ProbeGap 单独维护魔法长度。

- references 是知识基线，不是逐字标准答案；
- 合理方案即使不在 references 中，也不能判错；
- 不因措辞不同降低评级。

### 4.2 few-shot

资源文件：`resources/prompts/adaptive-agent-assessment-agents.md`

包含三个校准示例：

1. 只给方案名词 → L1 + 一条机制缺口；
2. 有机制但缺失败与边界 → L2 + 一条边界缺口；
3. 有机制、场景、取舍和边界 → L3 + 空缺口。

few-shot 只校准输出形态和追问粒度，不承载领域知识。

## 5. Prompt 契约

### 5.1 评估 Agent

`adaptive-agent-assessment-system.st` 增加：

- `openedGaps/closedGaps` 必须引用本轮真实事实；
- `anchor` 必须逐字来自 `answer`；
- `missingPoint` 只写“回答提到 X，但未说明 Y”；
- `probeGaps` 不得出现 L0-L4、深浅、好坏、分数等评级语言；
- 回答覆盖充分时 `probeGaps` 可为空。

### 5.2 出题 Agent

`adaptive-agent-interviewer-system.st` 提供选择空间：

- 展示当前 Plan/Coverage 中全部合法 open Gap，而不是只给 Java 预选的一条；
- Agent 自行决定当前 Gap、继续深挖或切换 Target；
- 选用某 Gap 时，问题必须能追溯到它的 `anchor/Evidence`；
- Agent 可以不选任何 Gap，按当前事实调用只读 Tool 或建议结束。

## 6. 代码校验

模型输出只做结构校验，不做领域关键词硬编码：

```text
anchor 为空或 answer 不包含 anchor        → validation observation
missingPoint 为空                         → validation observation
closedGapId 不属于本 Session/Plan         → validation observation
整体输出超过统一 schema/token 边界         → 明确失败
```

## 7. 公平性边界

允许进入本次 InterviewAgentLoop：

- `currentAnswer`
- 本场 Assessment / Evidence / 全部 open ProbeGap
- 上一份 Working Memory Snapshot

禁止进入本轮 Assessor：

- 跨会话历史评级；
- Semantic 能力画像；
- 上一轮 Agent 的未验证 Working Hypothesis。

公平性保护的是“本轮评分不被历史结论污染”，不是禁止 Interview Agent 读取本场已确认事实。该边界由 `CandidateMemoryFairnessContractTest` 和上下文装配测试共同锁定。

## 8. 落地清单

```text
演进：
  ProbeGap value object → 带来源与关闭事实的持久领域模型
  InterviewerContext.currentAnswerGaps → AgentContext.openProbeGaps
  Turn source gap 唯一约束 → 仅保留外键

修改：
  assessment/AssessmentRequest.java
  assessment/AssessmentProposal.java
  assessment/AssessmentDecision.java
  assessment/DepthAssessmentAgent.java
  assessment/SpringAiAssessmentProposalGenerator.java
  application/AdaptiveAgentProperties.java
  application/AdaptiveInterviewApplicationService.java
  core/context/AgentContext.java
  memory/ContextAssembler.java
  resources/prompts/adaptive-agent-assessment-system.st
  resources/prompts/adaptive-agent-interviewer-system.st
```

## 9. 成功标准

1. “只说名词”的回答能产出锚定原文的 `probeGaps`；
2. ProbeGap 与来源 Assessment/Turn 一起持久化，并能明确关闭；
3. CoverageProjector 能返回当前 Plan 内全部 open Gap；
4. Working Memory 只保存 activeGapId、临时优先级和假设，不复制 Gap 正文；
5. Agent 可以选择任一合法 Gap，也可以切换 Target；Java 不固定优先级；
6. 历史评级与 Semantic 画像仍不进入本轮 Assessor；
7. `./gradlew :app:test --no-daemon` 全绿，前端构建不受影响。
