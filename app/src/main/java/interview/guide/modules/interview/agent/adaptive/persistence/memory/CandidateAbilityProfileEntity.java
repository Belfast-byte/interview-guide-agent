package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityCounter;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileRevisionReason;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileSnapshot;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileSnapshotCreation;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticAbility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * owner + TopicKey 的不可变能力画像快照实体。
 */
@Entity
@Table(name = "candidate_ability_profiles")
public class CandidateAbilityProfileEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", length = 64)
  private String tenantId;

  @Column(name = "candidate_id", nullable = false, length = 64)
  private String candidateId;

  @Column(name = "skill_id", nullable = false, length = 64)
  private String skillId;

  @Column(name = "focus_id", nullable = false, length = 64)
  private String focusId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private SemanticAbility ability;

  @Column(name = "l0_count", nullable = false)
  private long l0Count;

  @Column(name = "l1_count", nullable = false)
  private long l1Count;

  @Column(name = "l2_count", nullable = false)
  private long l2Count;

  @Column(name = "l3_count", nullable = false)
  private long l3Count;

  @Column(name = "l4_count", nullable = false)
  private long l4Count;

  @Column(name = "source_session_id", nullable = false, length = 36)
  private String sourceSessionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "revision_reason", nullable = false, length = 24)
  private AbilityProfileRevisionReason revisionReason;

  @Column(name = "superseded_at")
  private LocalDateTime supersededAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected CandidateAbilityProfileEntity() {}

  public CandidateAbilityProfileEntity(AbilityProfileSnapshotCreation creation) {
    AbilityCounter counter = creation.counter();
    tenantId = creation.owner().tenantId();
    candidateId = creation.owner().candidateId();
    skillId = creation.topic().skillId();
    focusId = creation.topic().focusId();
    ability = counter.ability().orElseThrow();
    l0Count = counter.l0Count();
    l1Count = counter.l1Count();
    l2Count = counter.l2Count();
    l3Count = counter.l3Count();
    l4Count = counter.l4Count();
    sourceSessionId = creation.sourceSessionId();
    revisionReason = creation.revisionReason();
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }

  public void supersede(LocalDateTime supersededAt) {
    if (this.supersededAt != null) {
      throw new IllegalStateException("Profile 已被 supersede");
    }
    this.supersededAt = supersededAt;
  }

  public AbilityProfileSnapshot toDomain() {
    return new AbilityProfileSnapshot(
        id,
        new MemoryOwner(tenantId, candidateId),
        new TopicKey(skillId, focusId),
        ability,
        new AbilityCounter(l0Count, l1Count, l2Count, l3Count, l4Count),
        sourceSessionId,
        revisionReason,
        supersededAt,
        createdAt
    );
  }

  public long id() {
    return id;
  }

  public TopicKey topic() {
    return new TopicKey(skillId, focusId);
  }
}
