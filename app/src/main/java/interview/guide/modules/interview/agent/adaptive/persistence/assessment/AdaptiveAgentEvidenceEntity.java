package interview.guide.modules.interview.agent.adaptive.persistence.assessment;

import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeFactUsage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * AdaptiveAgentEvidenceEntity JPA 实体，对应数据库中的相关表。
 */
@Entity
@Table(
    name = "agent_evidences",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_agent_evidence_sandbox_execution",
        columnNames = "sandbox_execution_id"
    )
)
public class AdaptiveAgentEvidenceEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "assessment_id", nullable = false)
  private AdaptiveAgentAssessmentEntity assessment;

  @Enumerated(EnumType.STRING)
  @Column(name = "evidence_type", nullable = false, length = 20)
  private EvidenceType evidenceType;

  @Column(name = "source_session_id", nullable = false, length = 36)
  private String sourceSessionId;

  @Column(name = "source_turn_index", nullable = false)
  private int sourceTurnIndex;

  @Column(name = "quote_text", columnDefinition = "TEXT")
  private String quoteText;

  @Column(name = "tool_call_id")
  private Long toolCallId;

  @Column(name = "sandbox_execution_id", length = 36)
  private String sandboxExecutionId;

  @Column(name = "code_source_id", length = 128)
  private String codeSourceId;

  @Column(name = "code_anchor", length = 500)
  private String codeAnchor;

  @Enumerated(EnumType.STRING)
  @Column(name = "code_fact_usage", length = 24)
  private CodeFactUsage codeFactUsage;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected AdaptiveAgentEvidenceEntity() {}

  public AdaptiveAgentEvidenceEntity(
      AdaptiveAgentAssessmentEntity assessment,
      String sessionId,
      int turnIndex,
      ValidatedAssessmentEvidence evidence
  ) {
    this.assessment = assessment;
    this.evidenceType = evidence.type();
    this.sourceSessionId = sessionId;
    this.sourceTurnIndex = turnIndex;
    this.quoteText = evidence.quote();
    this.toolCallId = evidence.toolCallId();
    this.sandboxExecutionId = evidence.sandboxExecutionId();
  }

  public AdaptiveAgentEvidenceEntity(
      AdaptiveAgentAssessmentEntity assessment,
      String sessionId,
      int turnIndex,
      String codeSourceId,
      String codeAnchor,
      CodeFactUsage codeFactUsage
  ) {
    this.assessment = assessment;
    this.evidenceType = EvidenceType.CODE_FACT;
    this.sourceSessionId = sessionId;
    this.sourceTurnIndex = turnIndex;
    this.codeSourceId = codeSourceId;
    this.codeAnchor = codeAnchor;
    this.codeFactUsage = codeFactUsage;
  }

  /**
   * 构建代码事实证据；轮次不含代码事实时返回空。
   */
  public static Optional<AdaptiveAgentEvidenceEntity> codeFact(
      AdaptiveAgentAssessmentEntity assessment,
      String sessionId,
      int turnIndex,
      String codeSourceId,
      String codeAnchor,
      CodeFactUsage codeFactUsage
  ) {
    if (codeFactUsage == null) {
      return Optional.empty();
    }
    return Optional.of(new AdaptiveAgentEvidenceEntity(
        assessment,
        sessionId,
        turnIndex,
        codeSourceId,
        codeAnchor,
        codeFactUsage
    ));
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }

  public AdaptiveAgentAssessmentEntity assessment() {
    return assessment;
  }

  public long id() {
    return id;
  }

  public EvidenceType evidenceType() {
    return evidenceType;
  }

  public int sourceTurnIndex() {
    return sourceTurnIndex;
  }

  public String quoteText() {
    return quoteText;
  }

  public Long toolCallId() {
    return toolCallId;
  }

  public String sandboxExecutionId() {
    return sandboxExecutionId;
  }

  public String codeSourceId() {
    return codeSourceId;
  }

  public String codeAnchor() {
    return codeAnchor;
  }

  public CodeFactUsage codeFactUsage() {
    return codeFactUsage;
  }
}
