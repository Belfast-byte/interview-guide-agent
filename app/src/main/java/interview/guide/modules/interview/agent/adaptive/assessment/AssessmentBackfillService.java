package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AssessmentBackfillService {

  private final AssessmentBackfillStore store;
  private final DepthAssessmentAgent assessmentAgent;
  private final AssessmentEvidenceValidator evidenceValidator;

  public AssessmentBackfillService(
      AssessmentBackfillStore store,
      DepthAssessmentAgent assessmentAgent,
      AssessmentEvidenceValidator evidenceValidator
  ) {
    this.store = store;
    this.assessmentAgent = assessmentAgent;
    this.evidenceValidator = evidenceValidator;
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
    return missingTurns.size();
  }
}
