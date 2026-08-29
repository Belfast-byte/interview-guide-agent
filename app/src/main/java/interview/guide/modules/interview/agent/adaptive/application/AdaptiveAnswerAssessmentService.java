package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentContext;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentRequest;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthAssessmentAgent;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceCandidate;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceValidator;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.skill.InterviewSkillService;
import java.util.List;
import org.springframework.stereotype.Service;

/** 只根据当前回答与当前 Target 量规生成正式评估事实。 */
@Service
public class AdaptiveAnswerAssessmentService {

  private final DepthAssessmentAgent assessmentAgent;
  private final AssessmentEvidenceValidator evidenceValidator;
  private final InterviewSkillService skillService;

  public AdaptiveAnswerAssessmentService(
      DepthAssessmentAgent assessmentAgent,
      AssessmentEvidenceValidator evidenceValidator,
      InterviewSkillService skillService
  ) {
    this.assessmentAgent = assessmentAgent;
    this.evidenceValidator = evidenceValidator;
    this.skillService = skillService;
  }

  public AnswerAssessment assess(PlannedInterview interview, CandidateAnswer answer) {
    var history = interview.history();
    var answeredTurn = history.turns().get(answer.turnIndex() - 1);
    PlannedDimension dimension = interview.plan().dimension(answeredTurn.dimensionOrder());
    AssessmentDecision decision = assessmentAgent.assess(
        new AssessmentRequest(
            history.session().id(),
            answer.turnIndex(),
            AssessmentContext.currentAnswer(
                dimension.dimension(),
                dimension.focus(),
                answeredTurn.question(),
                answer.content()
            ),
            skillService.buildEvaluationReferenceSection(dimension.suggestedSkill())
        ),
        history.llmProvider()
    );
    if (decision.depthLevel().ordinal() > dimension.depthCeiling().ordinal()) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "评估深度超过 Plan 上限");
    }
    List<ValidatedAssessmentEvidence> evidences = evidenceValidator.validate(
        history.session().id(),
        answer.turnIndex(),
        answer.content(),
        decision.evidenceQuotes().stream().map(AssessmentEvidenceCandidate::quote).toList()
    );
    return new AnswerAssessment(dimension, decision, evidences);
  }
}
