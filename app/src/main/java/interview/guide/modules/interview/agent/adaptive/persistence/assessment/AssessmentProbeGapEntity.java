package interview.guide.modules.interview.agent.adaptive.persistence.assessment;

import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Assessment 产生的可恢复追问缺口。
 */
@Entity
@Table(
    name = "agent_assessment_probe_gaps",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_assessment_probe_gap_order",
        columnNames = {"assessment_id", "gap_order"}
    )
)
public class AssessmentProbeGapEntity {

  private static final String GAP_CODE_PREFIX = "GAP_";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "assessment_id", nullable = false)
  private AdaptiveAgentAssessmentEntity assessment;

  @Column(name = "gap_order", nullable = false)
  private int gapOrder;

  @Column(name = "gap_code", nullable = false, length = 32)
  private String gapCode;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String anchor;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected AssessmentProbeGapEntity() {}

  public AssessmentProbeGapEntity(
      AdaptiveAgentAssessmentEntity assessment,
      int gapOrder,
      ProbeGap gap
  ) {
    if (gapOrder < 1) {
      throw new IllegalArgumentException("gapOrder 必须为正数");
    }
    this.assessment = assessment;
    this.gapOrder = gapOrder;
    this.gapCode = GAP_CODE_PREFIX + gapOrder;
    this.anchor = gap.anchor();
    this.description = gap.missingPoint();
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }

  public long id() {
    return id;
  }

  public long assessmentId() {
    return assessment.id();
  }

  public int gapOrder() {
    return gapOrder;
  }

  public String gapCode() {
    return gapCode;
  }

  public ProbeGap toDomain() {
    return new ProbeGap(anchor, description);
  }
}
