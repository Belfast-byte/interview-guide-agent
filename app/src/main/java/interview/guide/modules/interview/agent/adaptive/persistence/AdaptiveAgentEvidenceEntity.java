package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.modules.interview.agent.adaptive.assessment.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.assessment.ValidatedAssessmentEvidence;
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
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_evidences")
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

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected AdaptiveAgentEvidenceEntity() {}

  AdaptiveAgentEvidenceEntity(
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
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }
}
