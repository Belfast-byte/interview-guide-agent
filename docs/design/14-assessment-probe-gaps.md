# 14. 评估追问缺口（probeGaps）方案

> 状态：已落地。
> 范围：候选人侧自适应面试的「回答评估 → 下一轮出题」链路，不涉及企业 MCP、租户链路、报告评分体系改造。

## 1. 目标

当前评估 Agent 与出题 Agent 完全分离：评估只产出 `DepthLevel` 等评级结论，出题 Agent 只能看到回答原文，不知道应该追问哪个具体缺口。本方案在**不把评级结论送给出题 Agent** 的前提下，增加一条中性追问信号：

- 评级结论继续用于画像、报告和动态轮次裁决；
- 追问缺口单独传递给同一维度的下一轮 interviewer；
- 评估判断以 Skill 知识基线为依据，并用少量 few-shot 校准输出形态。

## 2. 核心数据模型

### 2.1 ProbeGap

新增共享值对象 `core/ProbeGap`：

```java
public record ProbeGap(
    String anchor,
    String missingPoint
) {}
```

约束：

- `anchor`：必须逐字来自本轮候选人回答；
- `missingPoint`：只描述“回答提到 X，但未说明 Y”，不含评级语言。

### 2.2 AssessmentProposal / AssessmentDecision

各增加一个字段：

```java
List<ProbeGap> probeGaps
```

- 最多 2 条；
- 可为空，空表示当前回答已有足够追问素材；
- 本轮不持久化 `probeGaps`，只通过同请求内存链路传给下一题生成。

### 2.3 AssessmentRequest

增加：

```java
String skillReferenceSection
```

由当前维度 `suggestedSkill` 动态生成，作为评估 Agent 的知识基线，放入 system prompt。

### 2.4 InterviewerContext

增加：

```java
List<ProbeGap> currentAnswerGaps
```

只允许在当前维度未完成时传递；维度切换后清空。

## 3. 数据流

```text
submitAnswer
  ↓
currentDimension.suggestedSkill
  → InterviewSkillService.buildEvaluationReferenceSectionSafe(skillId)
  → AssessmentRequest.skillReferenceSection
  ↓
DepthAssessmentAgent.assess
  → AssessmentDecision {
      depthLevel / confidence / rationaleSummary / recommendSwitchQuestion,
      evidenceQuotes,
      probeGaps
    }
  ↓
评级字段 → 持久化 → 画像 / 报告 / replan
probeGaps → 同维度下一轮 interviewer.currentAnswerGaps
  ↓
interviewer 结合 currentDimensionAnswer + currentAnswerGaps 生成追问
```

## 4. Skill 知识与 few-shot

### 4.1 Skill reference

`InterviewSkillService.buildEvaluationReferenceSectionSafe(skillId)` 已存在，按 `skill.meta.yml` 的 `ref` 动态加载 references，上限 6000 字符。评估 Prompt 将其作为知识基线，但明确：

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

- `probeGaps` 最多 2 条；
- `anchor` 必须逐字来自 `answer`；
- `missingPoint` 只写“回答提到 X，但未说明 Y”；
- `probeGaps` 不得出现 L0-L4、深浅、好坏、分数等评级语言；
- 回答覆盖充分时 `probeGaps` 可为空。

### 5.2 出题 Agent

`adaptive-agent-interviewer-system.st` 增加：

- `currentAnswerGaps` 存在时优先围绕其中一条 `missingPoint` 追问；
- 必须结合 `currentAnswer` 中的 `anchor` 发问；
- 将 `missingPoint` 转成自然问题，不出现“缺口、未说明、评级”等词；
- `currentAnswerGaps` 为空时沿用原规则自行判断。

## 6. 代码校验

模型输出只做结构校验，不做领域关键词硬编码：

```text
probeGaps == null 或 size > 2            → AI_SERVICE_ERROR
anchor 为空 / 长度 > 80                  → AI_SERVICE_ERROR
missingPoint 为空 / 长度 > 120           → AI_SERVICE_ERROR
answer 不包含 anchor                     → AI_SERVICE_ERROR
```

## 7. 公平性边界

允许进入 interviewer：

- `currentAnswer`
- `currentAnswerGaps`

禁止进入 interviewer：

- `depthLevel`
- `confidence`
- `rationaleSummary`
- `recommendSwitchQuestion`
- `evidenceQuotes`

该边界由 `CandidateMemoryFairnessContractTest` 和上下文装配测试共同锁定。

## 8. 落地清单

```text
新增：
  app/src/main/java/interview/guide/modules/interview/agent/adaptive/core/ProbeGap.java
  app/src/main/resources/prompts/adaptive-agent-assessment-agents.md

修改：
  assessment/AssessmentRequest.java
  assessment/AssessmentProposal.java
  assessment/AssessmentDecision.java
  assessment/DepthAssessmentAgent.java
  assessment/SpringAiAssessmentProposalGenerator.java
  application/AdaptiveAgentProperties.java
  application/AdaptiveInterviewApplicationService.java
  core/InterviewerContext.java
  memory/ContextAssembler.java
  resources/prompts/adaptive-agent-assessment-system.st
  resources/prompts/adaptive-agent-interviewer-system.st
```

## 9. 成功标准

1. “只说名词”的回答能产出锚定原文的 `probeGaps`；
2. 下一轮 interviewer prompt 包含 `currentAnswerGaps`，但不包含评级字段；
3. 评级、画像、报告链路行为不变；
4. `./gradlew :app:test --no-daemon` 全绿；
5. 前端构建不受影响。
