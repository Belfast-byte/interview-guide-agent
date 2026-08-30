package interview.guide.modules.interview.agent.adaptive.persistence.assessment;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AdaptiveAgentAssessmentEntity JPA 实体，对应数据库中的相关表。
 */
@Entity
@Table(
    name = "agent_assessments",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_agent_assessment_turn",
        columnNames = {"session_id", "turn_index"}
    )
)
public class AdaptiveAgentAssessmentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false, length = 36)
  private String sessionId;

  @Column(name = "turn_index", nullable = false)
  private int turnIndex;

  @Column(name = "dimension_order", nullable = false)
  private int dimensionOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "depth_level", nullable = false, length = 2)
  private DepthLevel depthLevel;

  @Column(nullable = false, precision = 4, scale = 3)
  private BigDecimal confidence;

  @Column(name = "rationale_summary", nullable = false, length = 500)
  private String rationaleSummary;

  @Column(name = "budget_exhausted_final", nullable = false)
  private boolean budgetExhaustedFinal;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected AdaptiveAgentAssessmentEntity() {}

  public AdaptiveAgentAssessmentEntity(
      int dimensionOrder,
      AssessmentDecision decision
  ) {
    this(dimensionOrder, decision, false);
  }

  public AdaptiveAgentAssessmentEntity(
      int dimensionOrder,
      AssessmentDecision decision,
      boolean budgetExhaustedFinal
  ) {
    this.sessionId = decision.sessionId();
    this.turnIndex = decision.turnIndex();
    this.dimensionOrder = dimensionOrder;
    this.depthLevel = decision.depthLevel();
    this.confidence = BigDecimal.valueOf(decision.confidence());
    this.rationaleSummary = decision.rationaleSummary();
    this.budgetExhaustedFinal = budgetExhaustedFinal;
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }

  public Long id() {
    return id;
  }

  public String sessionId() {
    return sessionId;
  }

  public int turnIndex() {
    return turnIndex;
  }

  public int dimensionOrder() {
    return dimensionOrder;
  }

  public DepthLevel depthLevel() {
    return depthLevel;
  }

  public double confidence() {
    return confidence.doubleValue();
  }

  public String rationaleSummary() {
    return rationaleSummary;
  }

  public boolean budgetExhaustedFinal() {
    return budgetExhaustedFinal;
  }
}
