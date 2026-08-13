package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.modules.interview.agent.adaptive.core.DimensionBrief;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "dimension_briefs",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_dimension_brief_session_order",
        columnNames = {"session_id", "dimension_order"}
    )
)
public class AdaptiveDimensionBriefEntity {

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

  @Column(name = "key_findings", nullable = false, columnDefinition = "TEXT")
  private String keyFindings;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "dimension_brief_turn_refs",
      joinColumns = @JoinColumn(name = "brief_id")
  )
  @OrderColumn(name = "reference_order")
  @Column(name = "turn_index", nullable = false)
  private List<Integer> turnIndexes = new ArrayList<>();

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected AdaptiveDimensionBriefEntity() {}

  AdaptiveDimensionBriefEntity(DimensionBrief brief) {
    sessionId = brief.sessionId();
    dimensionOrder = brief.dimensionOrder();
    dimension = brief.dimension();
    focus = brief.focus();
    keyFindings = brief.keyFindings();
    turnIndexes = new ArrayList<>(brief.turnIndexes());
  }

  DimensionBrief toDomain() {
    return new DimensionBrief(
        sessionId,
        dimensionOrder,
        dimension,
        focus,
        keyFindings,
        turnIndexes
    );
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }
}
