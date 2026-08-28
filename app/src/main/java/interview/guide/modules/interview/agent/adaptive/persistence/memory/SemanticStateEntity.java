package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluatedAbility;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluationAggregate;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluationSemanticState;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluationStatistics;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.LatestPractice;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeAggregate;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMastery;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeOutcome;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeResult;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeSemanticState;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeStatistics;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticState;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticStateKey;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticTrack;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.StablePattern;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.TransferAssessment;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.TransferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(
    name = "candidate_semantic_states",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_semantic_state_owner_topic_track",
        columnNames = {"tenant_id", "candidate_id", "skill_id", "focus_id", "track"}
    )
)
public class SemanticStateEntity {

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
  @Column(nullable = false, length = 24)
  private SemanticTrack track;

  @Column(nullable = false)
  private long revision;

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

  @Column(name = "none_count", nullable = false)
  private long noneCount;

  @Column(name = "follow_up_count", nullable = false)
  private long followUpCount;

  @Column(name = "hint_count", nullable = false)
  private long hintCount;

  @Column(name = "tool_assisted_count", nullable = false)
  private long toolAssistedCount;

  @Column(name = "unresolved_count", nullable = false)
  private long unresolvedCount;

  @Enumerated(EnumType.STRING)
  @Column(name = "evaluated_ability", length = 16)
  private EvaluatedAbility evaluatedAbility;

  @Enumerated(EnumType.STRING)
  @Column(name = "practice_mastery", length = 16)
  private PracticeMastery practiceMastery;

  @Enumerated(EnumType.STRING)
  @Column(name = "latest_practice_outcome", length = 16)
  private PracticeOutcome latestPracticeOutcome;

  @Enumerated(EnumType.STRING)
  @Column(name = "latest_assistance_level", length = 16)
  private EpisodeAssistanceLevel latestAssistanceLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "latest_target_depth", length = 2)
  private DepthLevel latestTargetDepth;

  @Column(name = "latest_practice_episode_id")
  private Long latestPracticeEpisodeId;

  @Column(name = "latest_practice_at")
  private LocalDateTime latestPracticeAt;

  @Convert(converter = StablePatternsJsonConverter.class)
  @Column(name = "stable_patterns", nullable = false, columnDefinition = "TEXT")
  private List<StablePattern> stablePatterns;

  @Enumerated(EnumType.STRING)
  @Column(name = "transfer_status", length = 24)
  private TransferStatus transferStatus;

  @Column(name = "confirmed_by_episode_id")
  private Long confirmedByEpisodeId;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected SemanticStateEntity() {}

  public SemanticStateEntity(SemanticStateKey key) {
    tenantId = key.owner().tenantId();
    candidateId = key.owner().candidateId();
    skillId = key.topic().skillId();
    focusId = key.topic().focusId();
    track = key.track();
    stablePatterns = List.of();
  }

  public void apply(EvaluationAggregate aggregate) {
    EvaluationStatistics statistics = aggregate.statistics();
    l0Count = statistics.count(DepthLevel.L0);
    l1Count = statistics.count(DepthLevel.L1);
    l2Count = statistics.count(DepthLevel.L2);
    l3Count = statistics.count(DepthLevel.L3);
    l4Count = statistics.count(DepthLevel.L4);
    evaluatedAbility = aggregate.ability();
    stablePatterns = aggregate.stablePatterns();
    revision++;
  }

  public void apply(PracticeAggregate aggregate) {
    PracticeStatistics statistics = aggregate.statistics();
    noneCount = statistics.completed(EpisodeAssistanceLevel.NONE);
    followUpCount = statistics.completed(EpisodeAssistanceLevel.FOLLOW_UP);
    hintCount = statistics.completed(EpisodeAssistanceLevel.HINT);
    toolAssistedCount = statistics.completed(EpisodeAssistanceLevel.TOOL_ASSISTED);
    unresolvedCount = statistics.unresolvedCount();
    practiceMastery = aggregate.mastery();
    applyLatest(statistics.latest());
    stablePatterns = aggregate.stablePatterns();
    transferStatus = aggregate.transfer().status();
    confirmedByEpisodeId = aggregate.transfer().confirmedByEpisodeId();
    revision++;
  }

  private void applyLatest(LatestPractice latest) {
    PracticeResult result = latest.result();
    latestPracticeEpisodeId = latest.episodeId();
    latestPracticeOutcome = result.outcome();
    latestAssistanceLevel = result.assistance();
    latestTargetDepth = result.targetDepth();
    latestPracticeAt = latest.createdAt();
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

  public SemanticState toDomain() {
    SemanticStateKey key = key();
    if (track == SemanticTrack.EVALUATED_CAPABILITY) {
      return new EvaluationSemanticState(
          key, revision, evaluationStatistics(), evaluatedAbility, stablePatterns, updatedAt);
    }
    LatestPractice latest = new LatestPractice(
        latestPracticeEpisodeId,
        new PracticeResult(latestPracticeOutcome, latestAssistanceLevel, latestTargetDepth),
        latestPracticeAt
    );
    return new PracticeSemanticState(
        key, revision, practiceStatistics(latest), practiceMastery, stablePatterns,
        new TransferAssessment(transferStatus, confirmedByEpisodeId), updatedAt
    );
  }

  private EvaluationStatistics evaluationStatistics() {
    return new EvaluationStatistics(List.of(l0Count, l1Count, l2Count, l3Count, l4Count));
  }

  private PracticeStatistics practiceStatistics(LatestPractice latest) {
    Map<EpisodeAssistanceLevel, Long> completed = new EnumMap<>(EpisodeAssistanceLevel.class);
    completed.put(EpisodeAssistanceLevel.NONE, noneCount);
    completed.put(EpisodeAssistanceLevel.FOLLOW_UP, followUpCount);
    completed.put(EpisodeAssistanceLevel.HINT, hintCount);
    completed.put(EpisodeAssistanceLevel.TOOL_ASSISTED, toolAssistedCount);
    return new PracticeStatistics(completed, unresolvedCount, latest);
  }

  public SemanticStateKey key() {
    return new SemanticStateKey(
        new MemoryOwner(tenantId, candidateId),
        new TopicKey(skillId, focusId),
        track
    );
  }
}
