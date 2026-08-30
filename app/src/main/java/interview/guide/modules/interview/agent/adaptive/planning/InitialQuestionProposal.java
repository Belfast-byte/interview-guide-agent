package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageProjector;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import java.util.List;

/** 创建 Agent 在规划同一次模型调用中提出的首题。 */
public record InitialQuestionProposal(
    int targetOrder,
    String content,
    String decisionSummary,
    String nextProbeIntent
) {

  public AgentDecision toDecision(InterviewPlan plan) {
    boolean targetExists = plan.dimensions().stream()
        .anyMatch(dimension -> dimension.order() == targetOrder);
    if (!targetExists) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "首题 Target 不属于 Plan");
    }
    String targetId = CoverageProjector.targetId(targetOrder);
    WorkingMemory memory = new WorkingMemory(
        null,
        new WorkingMemory.Focus(targetId, null, List.of()),
        new WorkingMemory.Deliberation(List.of(), nextProbeIntent, List.of())
    );
    return new AgentDecision(memory, new AgentDecision.Ask(
        targetId,
        null,
        new AgentDecision.QuestionDraft(content, decisionSummary, List.of())
    ));
  }
}
