package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluationContribution;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeContribution;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeOutcome;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeResult;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticContribution;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticSource;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticTrack;
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
    name = "candidate_semantic_contributions",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_semantic_contribution_episode_track",
        columnNames = {"episode_id", "track"}
    )
)
public class SemanticContributionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "episode_id", nullable = false)
  private long episodeId;

  @Column(name = "tenant_id", length = 64)
  private String tenantId;

  @Column(name = "candidate_id", nullable = false, length = 64)
  private String candidateId;

  @Column(name = "skill_id", nullable = false, length = 64)
  private String skillId;

  @Column(name = "focus_id", nullable = false, length = 64)
  private String focusId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private SemanticTrack track;

  @Enumerated(EnumType.STRING)
  @Column(name = "evaluation_level", length = 2)
  private DepthLevel evaluationLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "practice_outcome", length = 16)
  private PracticeOutcome practiceOutcome;

  @Enumerated(EnumType.STRING)
  @Column(name = "assistance_level", length = 16)
  private EpisodeAssistanceLevel assistanceLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_depth", length = 2)
  private DepthLevel targetDepth;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected SemanticContributionEntity() {}

  public SemanticContributionEntity(SemanticContribution contribution) {
    SemanticSource source = contribution.source();
    episodeId = source.episodeId();
    tenantId = source.owner().tenantId();
    candidateId = source.owner().candidateId();
    skillId = source.topic().skillId();
    focusId = source.topic().focusId();
    track = contribution.track();
    createdAt = source.createdAt();
    applyTrackFields(contribution);
  }

  private void applyTrackFields(SemanticContribution contribution) {
    if (contribution instanceof EvaluationContribution evaluation) {
      evaluationLevel = evaluation.level();
      return;
    }
    PracticeResult result = ((PracticeContribution) contribution).result();
    practiceOutcome = result.outcome();
    assistanceLevel = result.assistance();
    targetDepth = result.targetDepth();
  }

  @PrePersist
  void prePersist() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }

  public SemanticContribution toDomain() {
    SemanticSource source = new SemanticSource(
        episodeId,
        new MemoryOwner(tenantId, candidateId),
        new TopicKey(skillId, focusId),
        createdAt
    );
    if (track == SemanticTrack.EVALUATED_CAPABILITY) {
      return new EvaluationContribution(source, evaluationLevel);
    }
    return new PracticeContribution(
        source,
        new PracticeResult(practiceOutcome, assistanceLevel, targetDepth)
    );
  }

  public long episodeId() {
    return episodeId;
  }

  public SemanticTrack track() {
    return track;
  }
}
