package interview.guide.modules.interview.agent.adaptive.rubric;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_rubric_generation_jobs")
public class RubricGenerationJob {
  @Id String id;
  @Column(nullable = false, length = 36) String sessionId;
  @Column(nullable = false) int turnIndex;
  @Column(nullable = false, columnDefinition = "TEXT") String dimension;
  @Column(nullable = false, columnDefinition = "TEXT") String focus;
  @Column(nullable = false, columnDefinition = "TEXT") String question;
  @Column(nullable = false, length = 32) String status;
  @Column(nullable = false) int attempts;
  @Column(length = 36) String leaseToken;
  @Column(nullable = false) LocalDateTime availableAt;
  @Column(nullable = false) LocalDateTime createdAt;
  @Column(nullable = false) LocalDateTime updatedAt;
  Long questionId;
  @Column(length = 64) String rubricVersion;
  @Column(columnDefinition = "TEXT") String draftJson;
  @Column(columnDefinition = "TEXT") String reviewJson;
  @Column(columnDefinition = "TEXT") String manualReviewsJson;
  @Column(length = 120) String generatorProvider;
  @Column(length = 120) String judgeProvider;
  @Column(length = 500) String lastError;
  protected RubricGenerationJob() {}
  public RubricGenerationJob(String sessionId, int turnIndex, String dimension, String focus, String question) {
    this.id = java.util.UUID.nameUUIDFromBytes((sessionId + ":" + turnIndex)
        .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    this.sessionId = sessionId; this.turnIndex = turnIndex;
    this.dimension = dimension; this.focus = focus; this.question = question; this.status = "PENDING";
    this.createdAt = this.updatedAt = this.availableAt = LocalDateTime.now();
  }
}
