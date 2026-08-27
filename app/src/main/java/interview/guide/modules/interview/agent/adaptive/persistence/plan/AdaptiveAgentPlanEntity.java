package interview.guide.modules.interview.agent.adaptive.persistence.plan;

import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.planning.PlanDimensionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.List;

/**
 * AdaptiveAgentPlanEntity JPA 实体，对应数据库中的相关表。
 */
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

  @Column(name = "focus_id", nullable = false, length = 64)
  private String focusId;

  @Column(name = "suggested_turns", nullable = false)
  private int suggestedTurns;

  @Column(name = "suggested_tools", nullable = false, length = 500)
  private String suggestedTools;

  @Column(name = "suggested_skill", length = 64)
  private String suggestedSkill;

  @Column(name = "allocated_turns", nullable = false)
  private int allocatedTurns;

  @Enumerated(EnumType.STRING)
  @Column(name = "expected_depth", nullable = false, length = 8)
  private DepthLevel expectedDepth;

  @Enumerated(EnumType.STRING)
  @Column(name = "depth_ceiling", nullable = false, length = 8)
  private DepthLevel depthCeiling;

  @Column(name = "follow_up_budget", nullable = false)
  private int followUpBudget;

  @Column(name = "tool_budget", nullable = false)
  private int toolBudget;

  @Convert(converter = EvidenceObjectivesJsonConverter.class)
  @Column(name = "evidence_objectives_json", nullable = false, columnDefinition = "TEXT")
  private List<CapabilityTarget.EvidenceObjective> evidenceObjectives;

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

  public AdaptiveAgentPlanEntity(String sessionId, PlannedDimension dimension) {
    this.sessionId = sessionId;
    apply(dimension);
  }

  public PlannedDimension toDomain() {
    return new PlannedDimension(
        new CapabilityTarget(
            new CapabilityTarget.Identity(
                dimensionOrder,
                dimension,
                focus,
                new TopicKey(suggestedSkill, focusId)
            ),
            new CapabilityTarget.Budget(
                suggestedTurns,
                allocatedTurns,
                followUpBudget,
                toolBudget
            ),
            new CapabilityTarget.Depth(expectedDepth, depthCeiling),
            evidenceObjectives,
            suggestedTools.isBlank() ? List.of() : List.of(suggestedTools.split(","))
        ),
        completedTurns,
        status
    );
  }

  public void apply(PlannedDimension plannedDimension) {
    dimensionOrder = plannedDimension.order();
    dimension = plannedDimension.dimension();
    focus = plannedDimension.focus();
    focusId = plannedDimension.focusId();
    suggestedTurns = plannedDimension.suggestedTurns();
    suggestedTools = String.join(",", plannedDimension.suggestedTools());
    suggestedSkill = plannedDimension.suggestedSkill();
    allocatedTurns = plannedDimension.allocatedTurns();
    expectedDepth = plannedDimension.expectedDepth();
    depthCeiling = plannedDimension.depthCeiling();
    followUpBudget = plannedDimension.followUpBudget();
    toolBudget = plannedDimension.toolBudget();
    evidenceObjectives = plannedDimension.evidenceObjectives();
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

  public int dimensionOrder() {
    return dimensionOrder;
  }

  public String dimension() {
    return dimension;
  }

  public String focus() {
    return focus;
  }

  public TopicKey topic() {
    return new TopicKey(suggestedSkill, focusId);
  }
}
