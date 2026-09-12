package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** 在原回答事务中追加一次经历，不保存第二套评级、gap 或整理状态。 */
@Entity
@Table(name = "candidate_memory_episode_facts", uniqueConstraints = @UniqueConstraint(
    name = "uk_memory_episode_session_turn", columnNames = {"session_id", "turn_index"}))
public class EpisodeFactEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", length = 64)
  private String tenantId;

  @Column(name = "candidate_id", nullable = false, length = 64)
  private String candidateId;

  @Column(name = "session_id", nullable = false, length = 36)
  private String sessionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "session_mode", nullable = false, length = 16)
  private SessionMode sessionMode;

  @Column(name = "turn_id", nullable = false, unique = true)
  private long turnId;

  @Column(name = "turn_index", nullable = false)
  private int turnIndex;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "assessment_id", nullable = false)
  private AdaptiveAgentAssessmentEntity assessment;

  @Column(name = "assessment_id", nullable = false, insertable = false, updatable = false)
  private Long assessmentId;

  @Column(name = "skill_id", nullable = false, length = 64)
  private String skillId;

  @Column(name = "focus_id", nullable = false, length = 64)
  private String focusId;

  @Column(name = "target_id", nullable = false, length = 36)
  private String targetId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected EpisodeFactEntity() {}

  public EpisodeFactEntity(Creation creation, AdaptiveAgentAssessmentEntity assessment) {
    tenantId = creation.owner().tenantId();
    candidateId = creation.owner().candidateId();
    sessionId = creation.sessionId();
    sessionMode = creation.sessionMode();
    turnId = creation.turnId();
    turnIndex = creation.turnIndex();
    this.assessment = assessment;
    assessmentId = assessment.id();
    skillId = creation.topic().skillId();
    focusId = creation.topic().focusId();
    targetId = creation.targetId();
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }

  public record Creation(
      MemoryOwner owner, String sessionId, SessionMode sessionMode,
      long turnId, int turnIndex, TopicKey topic, String targetId
  ) {}

  public long id() {
    return id;
  }
}
