package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentState;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFact;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactCreation;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

/**
 * 已回答 turn 的最小 Episode 事实实体。
 */
@Entity
@Table(
    name = "candidate_memory_episode_facts",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_memory_episode_session_turn",
        columnNames = {"session_id", "turn_index"}
    )
)
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

  @Enumerated(EnumType.STRING)
  @Column(name = "enrichment_status", nullable = false, length = 24)
  private EpisodeEnrichmentStatus enrichmentStatus;

  @Column(name = "answer_summary", columnDefinition = "TEXT")
  private String answerSummary;

  @Column(name = "enrichment_error", columnDefinition = "TEXT")
  private String enrichmentError;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected EpisodeFactEntity() {}

  public EpisodeFactEntity(
      EpisodeFactCreation creation,
      AdaptiveAgentAssessmentEntity assessment
  ) {
    assertAssessmentMatches(creation, assessment);
    this.tenantId = creation.owner().tenantId();
    this.candidateId = creation.owner().candidateId();
    this.sessionId = creation.sessionId();
    this.turnIndex = creation.turnIndex();
    this.assessment = assessment;
    this.assessmentId = assessment.id();
    this.skillId = creation.topic().skillId();
    this.focusId = creation.topic().focusId();
    this.enrichmentStatus = EpisodeEnrichmentStatus.PENDING;
  }

  private void assertAssessmentMatches(
      EpisodeFactCreation creation,
      AdaptiveAgentAssessmentEntity assessment
  ) {
    if (!assessment.sessionId().equals(creation.sessionId())
        || assessment.turnIndex() != creation.turnIndex()) {
      throw new IllegalArgumentException("Episode 与 Assessment 不属于同一轮次");
    }
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

  public EpisodeFact toDomain() {
    return new EpisodeFact(
        id,
        new MemoryOwner(tenantId, candidateId),
        sessionId,
        turnIndex,
        assessmentId,
        new TopicKey(skillId, focusId),
        enrichmentStatus,
        answerSummary,
        enrichmentError,
        createdAt,
        updatedAt
    );
  }

  public long id() {
    return id;
  }

  public boolean claimEnrichment() {
    if (enrichmentStatus != EpisodeEnrichmentStatus.PENDING) {
      return false;
    }
    apply(EpisodeEnrichmentState.pending().claim());
    return true;
  }

  public void completeEnrichment(String summary) {
    if (summary == null || summary.isBlank()) {
      throw new IllegalArgumentException("Episode answerSummary 不能为空");
    }
    apply(state().complete());
    answerSummary = summary;
  }

  public void failEnrichment(String error) {
    apply(state().fail(error));
    answerSummary = null;
  }

  public void retryEnrichment() {
    apply(state().retry());
    answerSummary = null;
  }

  public void recoverStaleEnrichment() {
    apply(state().recoverStaleProcessing());
    answerSummary = null;
  }

  public void resetEnrichmentAfterAssessmentCorrection() {
    apply(state().resetAfterAssessmentCorrection());
    answerSummary = null;
  }

  private EpisodeEnrichmentState state() {
    return new EpisodeEnrichmentState(enrichmentStatus, enrichmentError);
  }

  private void apply(EpisodeEnrichmentState state) {
    enrichmentStatus = state.status();
    enrichmentError = state.error();
  }
}
