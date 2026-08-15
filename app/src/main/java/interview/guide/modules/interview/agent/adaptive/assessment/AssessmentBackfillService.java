package interview.guide.modules.interview.agent.adaptive.assessment;

import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmAssessmentEvidenceService;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AssessmentBackfillService {

  private final AssessmentBackfillStore store;
  private final DepthAssessmentAgent assessmentAgent;
  private final AssessmentEvidenceValidator evidenceValidator;
  private final AlgorithmAssessmentEvidenceService algorithmEvidenceService;

  public AssessmentBackfillService(
      AssessmentBackfillStore store,
      DepthAssessmentAgent assessmentAgent,
      AssessmentEvidenceValidator evidenceValidator,
      AlgorithmAssessmentEvidenceService algorithmEvidenceService
  ) {
    this.store = store;
    this.assessmentAgent = assessmentAgent;
    this.evidenceValidator = evidenceValidator;
    this.algorithmEvidenceService = algorithmEvidenceService;
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
    return missingTurns.size();
  }
}
