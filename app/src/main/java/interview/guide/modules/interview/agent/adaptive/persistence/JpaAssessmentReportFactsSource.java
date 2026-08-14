package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentReportDimensionFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentReportEvidenceFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentReportFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentReportFactsSource;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentReportTurnFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.EvidenceType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaAssessmentReportFactsSource
    implements AssessmentReportFactsSource {

  private final AdaptiveAgentSessionRepository sessionRepository;
  private final AdaptiveAgentPlanRepository planRepository;
  private final AdaptiveAgentTurnRepository turnRepository;
  private final AdaptiveAgentAssessmentRepository assessmentRepository;
  private final AdaptiveAgentEvidenceRepository evidenceRepository;
  private final AdaptiveAgentToolCallRepository toolCallRepository;
  private final PracticeRecordRepository practiceRecordRepository;

  public JpaAssessmentReportFactsSource(
      AdaptiveAgentSessionRepository sessionRepository,
      AdaptiveAgentPlanRepository planRepository,
      AdaptiveAgentTurnRepository turnRepository,
      AdaptiveAgentAssessmentRepository assessmentRepository,
      AdaptiveAgentEvidenceRepository evidenceRepository,
      AdaptiveAgentToolCallRepository toolCallRepository,
      PracticeRecordRepository practiceRecordRepository
  ) {
    this.sessionRepository = sessionRepository;
    this.planRepository = planRepository;
    this.turnRepository = turnRepository;
    this.assessmentRepository = assessmentRepository;
    this.evidenceRepository = evidenceRepository;
    this.toolCallRepository = toolCallRepository;
    this.practiceRecordRepository = practiceRecordRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public AssessmentReportFacts loadCandidate(String sessionId) {
    AdaptiveAgentSessionEntity session = sessionRepository
        .findByIdAndTenantIdIsNull(sessionId)
        .orElseThrow(this::notFound);
    return load(session);
  }

  @Override
  @Transactional(readOnly = true)
  public AssessmentReportFacts loadEnterprise(
      String tenantId,
      String sessionId
  ) {
    AdaptiveAgentSessionEntity session = sessionRepository
        .findByIdAndTenantId(sessionId, tenantId)
        .orElseThrow(this::notFound);
    return load(session);
  }

  private AssessmentReportFacts load(AdaptiveAgentSessionEntity session) {
    List<AdaptiveAgentAssessmentEntity> assessments = assessmentRepository
        .findBySessionIdOrderByDimensionOrderAscTurnIndexAsc(session.id());
    Map<Integer, AdaptiveAgentTurnEntity> turns = turnRepository
        .findBySessionIdOrderByTurnIndex(session.id()).stream()
        .collect(Collectors.toMap(
            AdaptiveAgentTurnEntity::turnIndex,
            Function.identity()
        ));
    List<AdaptiveAgentEvidenceEntity> evidences = evidenceRepository
        .findReportEvidence(session.id());
    Map<Long, AdaptiveAgentToolCallEntity> toolCalls = toolCallRepository
        .findAllById(evidences.stream()
            .filter(evidence -> evidence.evidenceType() == EvidenceType.TOOL_RESULT)
            .map(AdaptiveAgentEvidenceEntity::toolCallId)
            .toList())
        .stream()
        .collect(Collectors.toMap(
            AdaptiveAgentToolCallEntity::id,
            Function.identity()
        ));
    Map<Long, List<AdaptiveAgentEvidenceEntity>> evidenceByAssessment = evidences
        .stream()
        .collect(Collectors.groupingBy(evidence -> evidence.assessment().id()));

    List<AssessmentReportDimensionFacts> dimensions = planRepository
        .findBySessionIdOrderByDimensionOrder(session.id()).stream()
        .map(plan -> new AssessmentReportDimensionFacts(
            plan.dimensionOrder(),
            plan.dimension(),
            plan.focus(),
            assessments.stream()
                .filter(assessment ->
                    assessment.dimensionOrder() == plan.dimensionOrder())
                .map(assessment -> turnFacts(
                    assessment,
                    turns,
                    evidenceByAssessment,
                    toolCalls
                ))
                .toList()
        ))
        .toList();
    return new AssessmentReportFacts(
        session.id(),
        session.candidateId(),
        session.status(),
        dimensions,
        practiceRecordRepository
            .findBySourceSessionIdOrderByDimensionOrder(session.id()).stream()
            .map(PracticeRecordEntity::toDomain)
            .toList()
    );
  }

  private AssessmentReportTurnFacts turnFacts(
      AdaptiveAgentAssessmentEntity assessment,
      Map<Integer, AdaptiveAgentTurnEntity> turns,
      Map<Long, List<AdaptiveAgentEvidenceEntity>> evidenceByAssessment,
      Map<Long, AdaptiveAgentToolCallEntity> toolCalls
  ) {
    return new AssessmentReportTurnFacts(
        assessment.turnIndex(),
        assessment.depthLevel(),
        assessment.confidence(),
        assessment.rationaleSummary(),
        evidenceByAssessment.get(assessment.id()).stream()
            .map(evidence -> evidenceFacts(evidence, turns, toolCalls))
            .toList()
    );
  }

  private AssessmentReportEvidenceFacts evidenceFacts(
      AdaptiveAgentEvidenceEntity evidence,
      Map<Integer, AdaptiveAgentTurnEntity> turns,
      Map<Long, AdaptiveAgentToolCallEntity> toolCalls
  ) {
    AdaptiveAgentTurnEntity turn = turns.get(evidence.sourceTurnIndex());
    if (evidence.evidenceType() == EvidenceType.QUOTE) {
      return new AssessmentReportEvidenceFacts(
          evidence.evidenceType(),
          evidence.sourceTurnIndex(),
          turn.question(),
          turn.answer(),
          evidence.quoteText(),
          null,
          null,
          null,
          null
      );
    }
    AdaptiveAgentToolCallEntity toolCall = toolCalls.get(evidence.toolCallId());
    return new AssessmentReportEvidenceFacts(
        evidence.evidenceType(),
        evidence.sourceTurnIndex(),
        turn.question(),
        turn.answer(),
        evidence.quoteText(),
        toolCall.id(),
        toolCall.toolName(),
        toolCall.resultId(),
        toolCall.outputSummary()
    );
  }

  private BusinessException notFound() {
    return new BusinessException(
        ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
        "Agent 面试会话不存在"
    );
  }
}
