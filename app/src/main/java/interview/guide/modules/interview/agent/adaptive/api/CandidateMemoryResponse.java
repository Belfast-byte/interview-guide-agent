package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.application.CandidateMemoryQueryResult;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagCategory;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticAbility;
import java.time.LocalDateTime;
import java.util.List;

/** 候选人长期记忆响应，仅公开报告与追问链所需字段。 */
public record CandidateMemoryResponse(
    String candidateId,
    List<TopicProfileResponse> topics,
    EpisodePageResponse episodes
) {

  static CandidateMemoryResponse from(CandidateMemoryQueryResult source) {
    return new CandidateMemoryResponse(
        source.candidateId(),
        source.topics().stream().map(TopicProfileResponse::from).toList(),
        EpisodePageResponse.from(source)
    );
  }

  public record TopicProfileResponse(
      String skillId,
      String focusId,
      SemanticAbility ability,
      long l0Count,
      long l1Count,
      long l2Count,
      long l3Count,
      long l4Count,
      List<TagCountResponse> tagCounts
  ) {

    private static TopicProfileResponse from(CandidateMemoryQueryResult.TopicProfile source) {
      return new TopicProfileResponse(
          source.topic().skillId(),
          source.topic().focusId(),
          source.ability(),
          source.counter().l0Count(),
          source.counter().l1Count(),
          source.counter().l2Count(),
          source.counter().l3Count(),
          source.counter().l4Count(),
          source.tagCounts().stream().map(TagCountResponse::from).toList()
      );
    }
  }

  public record TagCountResponse(
      EpisodeTagCategory category,
      String tag,
      long count
  ) {

    private static TagCountResponse from(CandidateMemoryQueryResult.TagCount source) {
      return new TagCountResponse(source.category(), source.tag(), source.count());
    }
  }

  public record EpisodePageResponse(
      List<EpisodeResponse> content,
      List<EpisodeResponse> ancestors,
      int page,
      int size,
      long totalElements,
      int totalPages,
      boolean last
  ) {

    private static EpisodePageResponse from(CandidateMemoryQueryResult source) {
      var episodes = source.episodes();
      return new EpisodePageResponse(
          episodes.getContent().stream().map(EpisodeResponse::from).toList(),
          source.episodeAncestors().stream().map(EpisodeResponse::from).toList(),
          episodes.getNumber(),
          episodes.getSize(),
          episodes.getTotalElements(),
          episodes.getTotalPages(),
          episodes.isLast()
      );
    }
  }

  public record EpisodeResponse(
      String sessionId,
      int turnIndex,
      Integer parentTurnIndex,
      TurnTriggerType triggerType,
      String skillId,
      String focusId,
      DepthLevel depthLevel,
      EpisodeEnrichmentStatus enrichmentStatus,
      LocalDateTime createdAt
  ) {

    private static EpisodeResponse from(CandidateMemoryQueryResult.Episode source) {
      return new EpisodeResponse(
          source.sessionId(),
          source.turnIndex(),
          source.parentTurnIndex(),
          source.triggerType(),
          source.topic().skillId(),
          source.topic().focusId(),
          source.depthLevel(),
          source.enrichmentStatus(),
          source.createdAt()
      );
    }
  }
}
