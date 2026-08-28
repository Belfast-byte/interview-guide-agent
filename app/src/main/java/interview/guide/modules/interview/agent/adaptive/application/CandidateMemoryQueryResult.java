package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentStatus;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluationSemanticState;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeSemanticState;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

/** 候选人长期记忆的只读查询结果。 */
public record CandidateMemoryQueryResult(
    String candidateId,
    List<TopicProfile> topics,
    Page<Episode> episodes,
    List<Episode> episodeAncestors
) {

  public record TopicProfile(
      TopicKey topic,
      EvaluationSemanticState evaluation,
      PracticeSemanticState practice
  ) {}

  public record Episode(
      String sessionId,
      int turnIndex,
      Integer parentTurnIndex,
      TurnTriggerType triggerType,
      TopicKey topic,
      DepthLevel depthLevel,
      EpisodeEnrichmentStatus enrichmentStatus,
      LocalDateTime createdAt
  ) {}
}
