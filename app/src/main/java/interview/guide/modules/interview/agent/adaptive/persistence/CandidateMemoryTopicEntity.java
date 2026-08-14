package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.modules.interview.agent.adaptive.core.CoveredTopic;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "candidate_memory_topics",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_candidate_memory_topic_source",
        columnNames = {"source_session_id", "dimension_order"}
    )
)
public class CandidateMemoryTopicEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "candidate_id", nullable = false, length = 64)
  private String candidateId;

  @Column(name = "skill_id", nullable = false, length = 64)
  private String skillId;

  @Column(name = "focus_id", nullable = false, length = 64)
  private String focusId;

  @Column(name = "source_session_id", nullable = false, length = 36)
  private String sourceSessionId;

  @Column(name = "dimension_order", nullable = false)
  private int dimensionOrder;

  @Column(name = "observed_at", nullable = false)
  private LocalDateTime observedAt;

  protected CandidateMemoryTopicEntity() {}

  CandidateMemoryTopicEntity(
      String candidateId,
      String sessionId,
      PlannedDimension dimension
  ) {
    this.candidateId = candidateId;
    this.skillId = dimension.suggestedSkill();
    this.focusId = dimension.focusId();
    this.sourceSessionId = sessionId;
    this.dimensionOrder = dimension.order();
  }

  public CoveredTopic toDomain() {
    return new CoveredTopic(skillId, focusId);
  }

  @PrePersist
  void prePersist() {
    observedAt = LocalDateTime.now();
  }
}
