package interview.guide.modules.interview.agent.adaptive.persistence.assessment;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.assessment.report.AssessmentReportDimensionFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.report.AssessmentReportEvidenceFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.report.AssessmentReportFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.report.AssessmentReportFactsSource;
import interview.guide.modules.interview.agent.adaptive.assessment.report.AssessmentReportTurnFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.assessment.report.ProjectCodeSourceReference;
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmEvidence;
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmEvidenceSource;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.practice.PracticeRecordEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.practice.PracticeRecordRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
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
  private final PracticeRecordRepository practiceRecordRepository;
  private final AlgorithmEvidenceSource algorithmEvidenceSource;

  public JpaAssessmentReportFactsSource(
      AdaptiveAgentSessionRepository sessionRepository,
      AdaptiveAgentPlanRepository planRepository,
      AdaptiveAgentTurnRepository turnRepository,
      AdaptiveAgentAssessmentRepository assessmentRepository,
      AdaptiveAgentEvidenceRepository evidenceRepository,
      PracticeRecordRepository practiceRecordRepository,
      AlgorithmEvidenceSource algorithmEvidenceSource
  ) {
    this.sessionRepository = sessionRepository;
    this.planRepository = planRepository;
    this.turnRepository = turnRepository;
    this.assessmentRepository = assessmentRepository;
    this.evidenceRepository = evidenceRepository;
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
      Map<String, AlgorithmEvidence> algorithmEvidences
  ) {
    return new AssessmentReportTurnFacts(
        assessment.turnIndex(),
        assessment.depthLevel(),
        assessment.confidence(),
        assessment.rationaleSummary(),
        evidenceByAssessment.get(assessment.id()).stream()
            .filter(evidence -> evidence.evidenceType() != EvidenceType.CODE_FACT)
            .filter(evidence -> evidence.evidenceType() != EvidenceType.TOOL_RESULT
                || evidence.sandboxExecutionId() != null)
            .map(evidence -> evidenceFacts(evidence, turns, algorithmEvidences))
            .toList()
    );
  }

  private AssessmentReportEvidenceFacts evidenceFacts(
      AdaptiveAgentEvidenceEntity evidence,
      Map<Integer, AdaptiveAgentTurnEntity> turns,
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
          algorithmEvidence.executionId(),
          "sandbox_submit",
          algorithmEvidence.executionId(),
          algorithmEvidence.summary()
      );
    }
    throw new IllegalStateException("不支持的评估证据类型: " + evidence.evidenceType());
  }

  private BusinessException notFound() {
    return new BusinessException(
        ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
        "Agent 面试会话不存在"
    );
  }
}
