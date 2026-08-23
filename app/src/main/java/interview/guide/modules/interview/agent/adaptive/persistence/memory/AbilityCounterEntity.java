package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityCounter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

/**
 * owner + TopicKey 的 L0-L4 累计计数。
 */
@Entity
@Table(
    name = "candidate_ability_counters",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_ability_counter_owner_topic",
        columnNames = {"tenant_id", "candidate_id", "skill_id", "focus_id"}
    )
)
public class AbilityCounterEntity {

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

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected AbilityCounterEntity() {}

  public AbilityCounterEntity(MemoryOwner owner, TopicKey topic) {
    tenantId = owner.tenantId();
    candidateId = owner.candidateId();
    skillId = topic.skillId();
    focusId = topic.focusId();
  }

  public long id() {
    return id;
  }

  public void increment(DepthLevel level) {
    apply(toDomain().increment(level));
  }

  public void decrement(DepthLevel level) {
    apply(toDomain().decrement(level));
  }

  public AbilityCounter toDomain() {
    return new AbilityCounter(l0Count, l1Count, l2Count, l3Count, l4Count);
  }

  private void apply(AbilityCounter counter) {
    l0Count = counter.l0Count();
    l1Count = counter.l1Count();
    l2Count = counter.l2Count();
    l3Count = counter.l3Count();
    l4Count = counter.l4Count();
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
