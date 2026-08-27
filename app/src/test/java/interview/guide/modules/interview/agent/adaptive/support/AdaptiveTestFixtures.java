package interview.guide.modules.interview.agent.adaptive.support;

import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.application.TenantInterviewCreationCommand;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import java.util.List;

/** 自适应面试测试使用的显式会话输入。 */
public final class AdaptiveTestFixtures {

  public static final InterviewSessionSettings EVALUATION_SETTINGS =
      new InterviewSessionSettings(
          SessionMode.EVALUATION,
          CandidateLevel.CAMPUS,
          PracticeScope.none()
      );

  private AdaptiveTestFixtures() {}

  public static AdaptiveInterviewSession testSession(String id, int maxTurns) {
    return AdaptiveInterviewSession.create(id, maxTurns, EVALUATION_SETTINGS);
  }

  public static InterviewPlan testPlan(String sessionId, PlanProposal proposal) {
    return InterviewPlan.decide(sessionId, proposal, EVALUATION_SETTINGS);
  }

  public static TenantInterviewCreationCommand testCreation(String candidateId) {
    return new TenantInterviewCreationCommand(
        null,
        candidateId,
        "JD",
        "Resume",
        null,
        EVALUATION_SETTINGS
    );
  }

  public static PlannedDimension testDimension(
      DimensionProposal proposal,
      int order,
      int completedTurns
  ) {
    String skillId = proposal.suggestedSkill() == null
        ? "question-bank"
        : proposal.suggestedSkill();
    CapabilityTarget target = new CapabilityTarget(
        new CapabilityTarget.Identity(
            order,
            proposal.dimension(),
            proposal.focus(),
            new TopicKey(skillId, proposal.focusId())
        ),
        new CapabilityTarget.Budget(
            proposal.suggestedTurns(),
            proposal.suggestedTurns(),
            2,
            proposal.suggestedTools().size()
        ),
        new CapabilityTarget.Depth(DepthLevel.L2, DepthLevel.L3),
        List.of(new CapabilityTarget.EvidenceObjective(
            proposal.focus(),
            CapabilityTarget.EvidenceMethod.CANDIDATE_ANSWER
        )),
        proposal.suggestedTools()
    );
    return new PlannedDimension(target);
  }
}
