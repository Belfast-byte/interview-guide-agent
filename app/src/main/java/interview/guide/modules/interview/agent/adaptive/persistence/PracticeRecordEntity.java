package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.modules.interview.agent.adaptive.assessment.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.assessment.PracticeRecommendation;
import interview.guide.modules.interview.agent.adaptive.assessment.PracticeStatus;
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
import java.time.LocalDateTime;

@Entity
@Table(
    name = "practice_records",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_practice_record_session_dimension",
        columnNames = {"source_session_id", "dimension_order"}
    )
)
public class PracticeRecordEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", length = 64)
  private String tenantId;

  @Column(name = "candidate_id", nullable = false, length = 64)
  private String candidateId;

  @Column(name = "source_session_id", nullable = false, length = 36)
  private String sourceSessionId;

  @Column(name = "dimension_order", nullable = false)
  private int dimensionOrder;

  @Column(nullable = false, length = 100)
  private String dimension;

  @Enumerated(EnumType.STRING)
  @Column(name = "demonstrated_depth", nullable = false, length = 2)
  private DepthLevel demonstratedDepth;

  @Column(name = "question_source_id", nullable = false, length = 128)
  private String questionSourceId;

  @Column(name = "question_difficulty", nullable = false, length = 16)
  private String questionDifficulty;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String question;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PracticeStatus status;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected PracticeRecordEntity() {}

  PracticeRecordEntity(
      AdaptiveAgentSessionEntity session,
      PracticeRecommendation recommendation
  ) {
    tenantId = session.tenantId();
    candidateId = session.candidateId();
    sourceSessionId = session.id();
    dimensionOrder = recommendation.dimensionOrder();
    dimension = recommendation.dimension();
    demonstratedDepth = recommendation.demonstratedLevel();
    questionSourceId = recommendation.questionSourceId();
    questionDifficulty = recommendation.questionDifficulty();
    question = recommendation.question();
    status = recommendation.status();
  }

  PracticeRecommendation toDomain() {
    return new PracticeRecommendation(
        dimensionOrder,
        dimension,
        demonstratedDepth,
        questionSourceId,
        questionDifficulty,
        question,
        status
    );
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }
}
