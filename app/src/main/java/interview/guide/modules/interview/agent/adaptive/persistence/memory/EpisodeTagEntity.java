package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagCategory;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagFact;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSourceType;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagValue;
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

@Entity
@Table(
    name = "candidate_memory_episode_tags",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_memory_episode_tag_source",
        columnNames = {"episode_id", "category", "tag", "source_type", "source_id"}
    )
)
public class EpisodeTagEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "episode_id", nullable = false)
  private EpisodeFactEntity episode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private EpisodeTagCategory category;

  @Column(nullable = false, length = 64)
  private String tag;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 32)
  private EpisodeTagSourceType sourceType;

  @Column(name = "source_id", nullable = false)
  private long sourceId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected EpisodeTagEntity() {}

  public EpisodeTagEntity(
      EpisodeFactEntity episode,
      EpisodeTagValue value,
      EpisodeTagSource source
  ) {
    this.episode = episode;
    this.category = value.category();
    this.tag = value.tag();
    this.sourceType = source.type();
    this.sourceId = source.sourceId();
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }

  public EpisodeTagFact toDomain() {
    return new EpisodeTagFact(
        id,
        episode.id(),
        new EpisodeTagValue(category, tag),
        new EpisodeTagSource(sourceType, sourceId)
    );
  }
}
