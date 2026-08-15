package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.DepthLevel;
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

  @Column(name = "recommend_switch_question", nullable = false)
  private boolean recommendSwitchQuestion;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected AdaptiveAgentAssessmentEntity() {}

  AdaptiveAgentAssessmentEntity(
      int dimensionOrder,
      AssessmentDecision decision
  ) {
    this.sessionId = decision.sessionId();
    this.turnIndex = decision.turnIndex();
    this.dimensionOrder = dimensionOrder;
    this.depthLevel = decision.depthLevel();
    this.confidence = BigDecimal.valueOf(decision.confidence());
    this.rationaleSummary = decision.rationaleSummary();
    this.recommendSwitchQuestion = decision.recommendSwitchQuestion();
  }

  void replace(AssessmentDecision decision) {
    this.depthLevel = decision.depthLevel();
    this.confidence = BigDecimal.valueOf(decision.confidence());
    this.rationaleSummary = decision.rationaleSummary();
    this.recommendSwitchQuestion = decision.recommendSwitchQuestion();
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }

  Long id() {
    return id;
  }

  String sessionId() {
    return sessionId;
  }

  int turnIndex() {
    return turnIndex;
  }

  int dimensionOrder() {
    return dimensionOrder;
  }

  DepthLevel depthLevel() {
    return depthLevel;
  }

  double confidence() {
    return confidence.doubleValue();
  }

  String rationaleSummary() {
    return rationaleSummary;
  }
}
