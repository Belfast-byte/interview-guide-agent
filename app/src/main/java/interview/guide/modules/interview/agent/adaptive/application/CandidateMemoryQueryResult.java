package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagCategory;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityCounter;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticAbility;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

/** 候选人长期记忆的只读查询结果。 */
public record CandidateMemoryQueryResult(
    String candidateId,
    List<TopicProfile> topics,
    Page<Episode> episodes
) {

  public record TopicProfile(
      TopicKey topic,
      SemanticAbility ability,
      AbilityCounter counter,
      List<TagCount> tagCounts
  ) {}

  public record TagCount(
      EpisodeTagCategory category,
      String tag,
      long count
  ) {}

  public record Episode(
      String sessionId,
      int turnIndex,
      Integer parentTurnIndex,
      TopicKey topic,
      DepthLevel depthLevel,
      EpisodeEnrichmentStatus enrichmentStatus,
      LocalDateTime createdAt
  ) {}
}
