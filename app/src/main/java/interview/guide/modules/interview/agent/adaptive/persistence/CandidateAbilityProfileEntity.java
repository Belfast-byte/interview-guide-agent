package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.modules.interview.agent.adaptive.assessment.DepthLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * CandidateAbilityProfileEntity JPA 实体，对应数据库中的相关表。
 */
@Entity
@Table(
    name = "candidate_ability_profiles",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_candidate_ability_profile_session_dimension",
        columnNames = {"source_session_id", "dimension_order"}
    )
)
public class CandidateAbilityProfileEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", length = 64)
  private String tenantId;

  @Column(name = "candidate_id", nullable = false, length = 64)
  private String candidateId;

  @Column(nullable = false, length = 100)
  private String dimension;

  @Column(name = "dimension_order", nullable = false)
  private int dimensionOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "depth_level", nullable = false, length = 2)
  private DepthLevel depthLevel;

  @Column(name = "source_session_id", nullable = false, length = 36)
  private String sourceSessionId;

  @Column(name = "source_assessment_id", nullable = false)
  private Long sourceAssessmentId;

  @Column(name = "superseded_by")
  private Long supersededBy;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected CandidateAbilityProfileEntity() {}

  CandidateAbilityProfileEntity(
      AdaptiveAgentSessionEntity session,
      AdaptiveAgentPlanEntity dimension,
      AdaptiveAgentAssessmentEntity assessment
  ) {
    tenantId = session.tenantId();
    candidateId = session.candidateId();
    this.dimension = dimension.dimension();
    dimensionOrder = dimension.dimensionOrder();
    depthLevel = assessment.depthLevel();
    sourceSessionId = session.id();
    sourceAssessmentId = assessment.id();
    createdAt = session.completedAt();
  }

  public Long id() {
    return id;
  }

  public String sourceSessionId() {
    return sourceSessionId;
  }

  public String dimension() {
    return dimension;
  }

  public DepthLevel depthLevel() {
    return depthLevel;
  }

  public Long sourceAssessmentId() {
    return sourceAssessmentId;
  }

  public boolean current() {
    return supersededBy == null;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  void supersede(Long replacementId) {
    supersededBy = replacementId;
  }

  void replaceAssessment(AdaptiveAgentAssessmentEntity assessment) {
    depthLevel = assessment.depthLevel();
    sourceAssessmentId = assessment.id();
  }
}
