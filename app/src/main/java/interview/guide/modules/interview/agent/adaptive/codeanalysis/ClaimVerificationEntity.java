package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "claim_verifications")
class ClaimVerificationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "claim_id", nullable = false, length = 64)
  private String claimId;

  @Column(name = "repository_id", nullable = false, length = 36)
  private String repositoryId;

  @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
  private String payloadJson;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected ClaimVerificationEntity() {}

  ClaimVerificationEntity(String claimId, String repositoryId, String payloadJson) {
    this.claimId = claimId;
    this.repositoryId = repositoryId;
    this.payloadJson = payloadJson;
  }

  String payloadJson() {
    return payloadJson;
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }
}
