package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.modules.interview.agent.adaptive.planning.PlanDimensionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "agent_plans",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_agent_plan_session_order",
        columnNames = {"session_id", "dimension_order"}
    )
)
public class AdaptiveAgentPlanEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false, length = 36)
  private String sessionId;

  @Column(name = "dimension_order", nullable = false)
  private int dimensionOrder;

  @Column(nullable = false, length = 100)
  private String dimension;

  @Column(nullable = false, length = 500)
  private String focus;

  @Column(name = "suggested_turns", nullable = false)
  private int suggestedTurns;

  @Column(name = "allocated_turns", nullable = false)
  private int allocatedTurns;

  @Column(name = "completed_turns", nullable = false)
  private int completedTurns;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PlanDimensionStatus status;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected AdaptiveAgentPlanEntity() {}

  AdaptiveAgentPlanEntity(String sessionId, PlannedDimension dimension) {
    this.sessionId = sessionId;
    apply(dimension);
  }

  PlannedDimension toDomain() {
    return new PlannedDimension(
        dimensionOrder,
        dimension,
        focus,
        suggestedTurns,
        allocatedTurns,
        completedTurns,
        status
    );
  }

  void apply(PlannedDimension plannedDimension) {
    dimensionOrder = plannedDimension.order();
    dimension = plannedDimension.dimension();
    focus = plannedDimension.focus();
    suggestedTurns = plannedDimension.suggestedTurns();
    allocatedTurns = plannedDimension.allocatedTurns();
    completedTurns = plannedDimension.completedTurns();
    status = plannedDimension.status();
  }

  @PrePersist
  void prePersist() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
