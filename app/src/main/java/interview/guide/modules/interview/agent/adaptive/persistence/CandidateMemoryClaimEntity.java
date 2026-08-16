package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.modules.interview.agent.adaptive.core.CandidateClaimType;
import interview.guide.modules.interview.agent.adaptive.core.UnverifiedClaim;
import interview.guide.modules.interview.agent.adaptive.memory.CandidateClaim;
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

/**
 * CandidateMemoryClaimEntity JPA 实体，对应数据库中的相关表。
 */
@Entity
@Table(
    name = "candidate_memory_claims",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_candidate_memory_claim_source",
        columnNames = {
            "source_session_id",
            "source_turn_index",
            "claim_type",
            "skill_id",
            "focus_id"
        }
    )
)
public class CandidateMemoryClaimEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "candidate_id", nullable = false, length = 64)
  private String candidateId;

  @Column(name = "tenant_id", length = 64)
  private String tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "claim_type", nullable = false, length = 30)
  private CandidateClaimType claimType;

  @Column(name = "skill_id", nullable = false, length = 64)
  private String skillId;

  @Column(name = "focus_id", nullable = false, length = 64)
  private String focusId;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_status", nullable = false, length = 20)
  private ClaimVerificationStatus verificationStatus;

  @Column(name = "source_session_id", nullable = false, length = 36)
  private String sourceSessionId;

  @Column(name = "source_turn_index", nullable = false)
  private int sourceTurnIndex;

  @Column(name = "observed_at", nullable = false)
  private LocalDateTime observedAt;

  protected CandidateMemoryClaimEntity() {}

  CandidateMemoryClaimEntity(
      String tenantId,
      String candidateId,
      String sessionId,
      CandidateClaim claim
  ) {
    this.tenantId = tenantId;
    this.candidateId = candidateId;
    this.claimType = claim.type();
    this.skillId = claim.skillId();
    this.focusId = claim.focusId();
    this.verificationStatus = ClaimVerificationStatus.UNVERIFIED;
    this.sourceSessionId = sessionId;
    this.sourceTurnIndex = claim.sourceTurnIndex();
  }

  public UnverifiedClaim toDomain() {
    return new UnverifiedClaim(claimType, skillId, focusId);
  }

  @PrePersist
  void prePersist() {
    observedAt = LocalDateTime.now();
  }
}
