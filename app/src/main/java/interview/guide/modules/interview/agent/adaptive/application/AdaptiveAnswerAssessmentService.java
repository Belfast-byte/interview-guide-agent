package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentContext;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentRequest;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthAssessmentAgent;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceCandidate;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceValidator;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageProjector;
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
            new AssessmentContext(
                dimension.dimension(), dimension.focus(), answeredTurn.question(), answer.content(),
                AssessmentContext.currentAnswer(dimension.dimension(), dimension.focus(),
                    answeredTurn.question(), answer.content()).rubric(),
                answeredTurn.adoptedRubrics().stream()
                    .filter(r -> r.body() != null && !r.body().isBlank()).toList(),
                interview.coverage().openProbeGaps().stream().filter(g -> g.targetId().equals(
                    CoverageProjector.targetId(dimension.order()))).toList(),
                // 只提供本场原始前文来识别提示条件，不传跨场 Episode 或历史评级。
                history.turns().stream().filter(turn -> turn.turnIndex() < answer.turnIndex())
                    .map(turn -> turn.answerContext()).toList()
            ),
            skillService.buildEvaluationReferenceSection(dimension.suggestedSkill())
        ),
        history.llmProvider()
    );
    List<ValidatedAssessmentEvidence> evidences = evidenceValidator.validate(
        history.session().id(),
        answer.turnIndex(),
        answer.content(),
        java.util.stream.Stream.concat(decision.evidenceQuotes().stream(),
            decision.resolvedGaps().stream().map(r -> r.evidenceQuote())).distinct().map(AssessmentEvidenceCandidate::quote).toList()
    );
    return new AnswerAssessment(dimension, decision, evidences);
  }

  /** 回答推进进入 Agent Loop 前的正式事实提案。 */
  public record AnswerAssessment(
      PlannedDimension dimension,
      AssessmentDecision decision,
      List<ValidatedAssessmentEvidence> evidences
  ) {

    public AnswerAssessment {
      evidences = List.copyOf(evidences);
    }
  }
}
