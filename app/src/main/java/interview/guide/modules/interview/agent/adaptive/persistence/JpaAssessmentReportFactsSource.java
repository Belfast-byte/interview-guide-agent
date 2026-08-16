package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentReportDimensionFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentReportEvidenceFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentReportFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentReportFactsSource;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentReportTurnFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.assessment.ProjectCodeSourceReference;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmEvidence;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmEvidenceSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 基于 JPA 的评估报告事实来源实现。
 */
@Component
public class JpaAssessmentReportFactsSource
    implements AssessmentReportFactsSource {

  private final AdaptiveAgentSessionRepository sessionRepository;
  private final AdaptiveAgentPlanRepository planRepository;
  private final AdaptiveAgentTurnRepository turnRepository;
  private final AdaptiveAgentAssessmentRepository assessmentRepository;
  private final AdaptiveAgentEvidenceRepository evidenceRepository;
  private final AdaptiveAgentToolCallRepository toolCallRepository;
  private final PracticeRecordRepository practiceRecordRepository;
  private final AlgorithmEvidenceSource algorithmEvidenceSource;

  public JpaAssessmentReportFactsSource(
      AdaptiveAgentSessionRepository sessionRepository,
      AdaptiveAgentPlanRepository planRepository,
      AdaptiveAgentTurnRepository turnRepository,
      AdaptiveAgentAssessmentRepository assessmentRepository,
      AdaptiveAgentEvidenceRepository evidenceRepository,
      AdaptiveAgentToolCallRepository toolCallRepository,
      PracticeRecordRepository practiceRecordRepository,
      AlgorithmEvidenceSource algorithmEvidenceSource
  ) {
    this.sessionRepository = sessionRepository;
    this.planRepository = planRepository;
    this.turnRepository = turnRepository;
    this.assessmentRepository = assessmentRepository;
    this.evidenceRepository = evidenceRepository;
    this.toolCallRepository = toolCallRepository;
    this.practiceRecordRepository = practiceRecordRepository;
    this.algorithmEvidenceSource = algorithmEvidenceSource;
  }

  @Override
  public AssessmentReportFacts loadCandidate(String sessionId) {
    AdaptiveAgentSessionEntity session = sessionRepository
        .findByIdAndTenantIdIsNull(sessionId)
        .orElseThrow(this::notFound);
    return load(session);
  }

  @Override
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
            .filter(Objects::nonNull)
            .toList())
        .stream()
        .collect(Collectors.toMap(
            AdaptiveAgentToolCallEntity::id,
            Function.identity()
        ));
    Map<String, AlgorithmEvidence> algorithmEvidences = algorithmEvidenceSource.findEvidence(
        evidences.stream()
            .map(AdaptiveAgentEvidenceEntity::sandboxExecutionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet())
    );
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
                    toolCalls,
                    algorithmEvidences
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
            .toList(),
        evidences.stream()
            .filter(evidence -> evidence.evidenceType() == EvidenceType.CODE_FACT)
            .map(evidence -> new ProjectCodeSourceReference(
                evidence.sourceTurnIndex(),
                turns.get(evidence.sourceTurnIndex()).question(),
                evidence.codeSourceId(),
                evidence.codeAnchor(),
                evidence.codeFactUsage()
            ))
            .toList()
    );
  }

  private AssessmentReportTurnFacts turnFacts(
      AdaptiveAgentAssessmentEntity assessment,
      Map<Integer, AdaptiveAgentTurnEntity> turns,
      Map<Long, List<AdaptiveAgentEvidenceEntity>> evidenceByAssessment,
      Map<Long, AdaptiveAgentToolCallEntity> toolCalls,
      Map<String, AlgorithmEvidence> algorithmEvidences
  ) {
    return new AssessmentReportTurnFacts(
        assessment.turnIndex(),
        assessment.depthLevel(),
        assessment.confidence(),
        assessment.rationaleSummary(),
        evidenceByAssessment.get(assessment.id()).stream()
            .filter(evidence -> evidence.evidenceType() != EvidenceType.CODE_FACT)
            .map(evidence -> evidenceFacts(
                evidence,
                turns,
                toolCalls,
                algorithmEvidences
            ))
            .toList()
    );
  }

  private AssessmentReportEvidenceFacts evidenceFacts(
      AdaptiveAgentEvidenceEntity evidence,
      Map<Integer, AdaptiveAgentTurnEntity> turns,
      Map<Long, AdaptiveAgentToolCallEntity> toolCalls,
      Map<String, AlgorithmEvidence> algorithmEvidences
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
          null,
          null
      );
    }
    if (evidence.sandboxExecutionId() != null) {
      AlgorithmEvidence algorithmEvidence = algorithmEvidences.get(
          evidence.sandboxExecutionId()
      );
      return new AssessmentReportEvidenceFacts(
          evidence.evidenceType(),
          evidence.sourceTurnIndex(),
          turn.question(),
          turn.answer(),
          null,
          null,
          algorithmEvidence.executionId(),
          "sandbox_submit",
          algorithmEvidence.executionId(),
          algorithmEvidence.summary()
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
        null,
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
