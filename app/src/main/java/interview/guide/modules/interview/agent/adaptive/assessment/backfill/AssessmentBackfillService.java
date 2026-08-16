package interview.guide.modules.interview.agent.adaptive.assessment.backfill;

import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmAssessmentEvidenceService;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentContext;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentRequest;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthAssessmentAgent;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceCandidate;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceValidator;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.memory.profile.CandidateAbilityProfileWriter;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 评估回填服务，为历史会话补写缺失的评估结果。
 */
@Service
public class AssessmentBackfillService {

  private final AssessmentBackfillStore store;
  private final DepthAssessmentAgent assessmentAgent;
  private final AssessmentEvidenceValidator evidenceValidator;
  private final AlgorithmAssessmentEvidenceService algorithmEvidenceService;
  private final CandidateAbilityProfileWriter abilityProfileWriter;

  public AssessmentBackfillService(
      AssessmentBackfillStore store,
      DepthAssessmentAgent assessmentAgent,
      AssessmentEvidenceValidator evidenceValidator,
      AlgorithmAssessmentEvidenceService algorithmEvidenceService,
      CandidateAbilityProfileWriter abilityProfileWriter
  ) {
    this.store = store;
    this.assessmentAgent = assessmentAgent;
    this.evidenceValidator = evidenceValidator;
    this.algorithmEvidenceService = algorithmEvidenceService;
    this.abilityProfileWriter = abilityProfileWriter;
  }

  public int backfill(String sessionId) {
    List<AssessmentBackfillTurn> missingTurns = store.findMissing(sessionId);
    for (AssessmentBackfillTurn turn : missingTurns) {
      AssessmentDecision assessment = assessmentAgent.assess(
          new AssessmentRequest(
              turn.sessionId(),
              turn.turnIndex(),
              AssessmentContext.currentAnswer(
                  turn.dimension(),
                  turn.focus(),
                  turn.question(),
                  turn.answer()
              )
          ),
          turn.llmProvider()
      );
      List<ValidatedAssessmentEvidence> evidences = evidenceValidator.validate(
          turn.sessionId(),
          turn.turnIndex(),
          turn.answer(),
          assessment.evidenceQuotes().stream()
              .map(AssessmentEvidenceCandidate::quote)
              .toList()
      );
      store.save(turn, assessment, evidences);
    }
    algorithmEvidenceService.attachAvailable(sessionId);
    abilityProfileWriter.refresh(sessionId);
    return missingTurns.size();
  }
}
