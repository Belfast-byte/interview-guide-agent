package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentBackfillStore;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentBackfillTurn;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.ValidatedAssessmentEvidence;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaAssessmentBackfillStore implements AssessmentBackfillStore {

  private final AdaptiveAgentSessionRepository sessionRepository;
  private final AdaptiveAgentPlanRepository planRepository;
  private final AdaptiveAgentTurnRepository turnRepository;
  private final AdaptiveAgentAssessmentRepository assessmentRepository;
  private final AdaptiveAgentEvidenceRepository evidenceRepository;

  public JpaAssessmentBackfillStore(
      AdaptiveAgentSessionRepository sessionRepository,
      AdaptiveAgentPlanRepository planRepository,
      AdaptiveAgentTurnRepository turnRepository,
      AdaptiveAgentAssessmentRepository assessmentRepository,
      AdaptiveAgentEvidenceRepository evidenceRepository
  ) {
    this.sessionRepository = sessionRepository;
    this.planRepository = planRepository;
    this.turnRepository = turnRepository;
    this.assessmentRepository = assessmentRepository;
    this.evidenceRepository = evidenceRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AssessmentBackfillTurn> findMissing(String sessionId) {
    AdaptiveAgentSessionEntity session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    Map<Integer, AdaptiveAgentPlanEntity> plans = planRepository
        .findBySessionIdOrderByDimensionOrder(sessionId).stream()
        .collect(Collectors.toMap(
            AdaptiveAgentPlanEntity::dimensionOrder,
            Function.identity()
        ));
    Set<Integer> assessedTurns = assessmentRepository
        .findBySessionIdOrderByDimensionOrderAscTurnIndexAsc(sessionId).stream()
        .map(AdaptiveAgentAssessmentEntity::turnIndex)
        .collect(Collectors.toSet());
    return turnRepository.findBySessionIdOrderByTurnIndex(sessionId).stream()
        .filter(turn -> turn.answer() != null)
        .filter(turn -> !assessedTurns.contains(turn.turnIndex()))
        .map(turn -> backfillTurn(session, plans, turn))
        .toList();
  }

  @Override
  @Transactional
  public void save(
      AssessmentBackfillTurn turn,
      AssessmentDecision assessment,
      List<ValidatedAssessmentEvidence> evidences
  ) {
    AdaptiveAgentAssessmentEntity entity = assessmentRepository.save(
        new AdaptiveAgentAssessmentEntity(turn.dimensionOrder(), assessment)
    );
    evidenceRepository.saveAll(evidences.stream()
        .map(evidence -> new AdaptiveAgentEvidenceEntity(
            entity,
            turn.sessionId(),
            turn.turnIndex(),
            evidence
        ))
        .toList());
    if (turn.codeFactUsage() != null) {
      evidenceRepository.save(new AdaptiveAgentEvidenceEntity(
          entity,
          turn.sessionId(),
          turn.turnIndex(),
          turn.codeSourceId(),
          turn.codeAnchor(),
          turn.codeFactUsage()
      ));
    }
  }

  private AssessmentBackfillTurn backfillTurn(
      AdaptiveAgentSessionEntity session,
      Map<Integer, AdaptiveAgentPlanEntity> plans,
      AdaptiveAgentTurnEntity turn
  ) {
    AdaptiveAgentPlanEntity plan = plans.get(turn.dimensionOrder());
    return new AssessmentBackfillTurn(
        session.id(),
        turn.turnIndex(),
        turn.dimensionOrder(),
        plan.dimension(),
        plan.focus(),
        turn.question(),
        turn.answer(),
        session.llmProvider(),
        turn.codeSourceId(),
        turn.codeAnchor(),
        turn.codeFactUsage()
    );
  }
}
