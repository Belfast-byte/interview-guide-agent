package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.assessment.practice.PracticeRecommendation;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.NextTurnProvenanceDraft;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;
import interview.guide.modules.interview.agent.adaptive.memory.claim.CandidateClaim;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecution;
import java.util.List;
import java.util.Objects;

/** 一次答题事实短事务的完整不可变输入。 */
public record AdaptiveDecisionPersistenceInput(
    MemoryOwner owner,
    String sessionId,
    CandidateAnswer answer,
    RespondAction proposedAction,
    List<ToolExecution> toolExecutions,
    DimensionBrief dimensionBrief,
    List<CandidateClaim> candidateClaims,
    AssessmentDecision assessmentDecision,
    List<ValidatedAssessmentEvidence> assessmentEvidences,
    List<PracticeRecommendation> practiceRecommendations,
    NextTurnProvenanceDraft nextTurnProvenance,
    List<WorkStatePatch> workStatePatches
) {

  public AdaptiveDecisionPersistenceInput {
    Objects.requireNonNull(owner, "owner 不能为空");
    toolExecutions = List.copyOf(toolExecutions);
    candidateClaims = List.copyOf(candidateClaims);
    assessmentEvidences = List.copyOf(assessmentEvidences);
    practiceRecommendations = List.copyOf(practiceRecommendations);
    Objects.requireNonNull(nextTurnProvenance, "nextTurnProvenance 不能为空");
    workStatePatches = List.copyOf(workStatePatches);
    if (workStatePatches.isEmpty()) {
      throw new IllegalArgumentException("回答决策必须包含 WorkState Patch");
    }
  }
}
