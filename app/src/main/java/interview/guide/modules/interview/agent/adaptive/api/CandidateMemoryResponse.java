package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.application.CandidateMemoryQueryResult;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagCategory;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluatedAbility;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluationSemanticState;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMastery;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeOutcome;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeSemanticState;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.StablePattern;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.TransferStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
      EvaluationTrackResponse evaluation,
      PracticeTrackResponse practice
  ) {

    private static TopicProfileResponse from(CandidateMemoryQueryResult.TopicProfile source) {
      return new TopicProfileResponse(
          source.topic().skillId(),
          source.topic().focusId(),
          EvaluationTrackResponse.from(source.evaluation()),
          PracticeTrackResponse.from(source.practice())
      );
    }
  }

  public record StablePatternResponse(
      EpisodeTagCategory category,
      String tag,
      long episodeCount
  ) {

    private static StablePatternResponse from(StablePattern source) {
      return new StablePatternResponse(
          source.value().category(), source.value().tag(), source.episodeCount());
    }
  }

  public record TrackMetadataResponse(
      long revision,
      List<StablePatternResponse> stablePatterns
  ) {}

  public record EvaluationTrackResponse(
      TrackMetadataResponse metadata,
      EvaluatedAbility ability,
      EvaluationStatisticsResponse statistics
  ) {

    private static EvaluationTrackResponse from(EvaluationSemanticState source) {
      if (source == null) {
        return null;
      }
      return new EvaluationTrackResponse(
          CandidateMemoryResponse.metadata(source.revision(), source.stablePatterns()),
          source.ability(),
          new EvaluationStatisticsResponse(source.statistics().levelCounts())
      );
    }
  }

  public record EvaluationStatisticsResponse(List<Long> levelCounts) {}

  public record PracticeTrackResponse(
      TrackMetadataResponse metadata,
      PracticeMastery mastery,
      PracticeDetailsResponse details
  ) {

    private static PracticeTrackResponse from(PracticeSemanticState source) {
      if (source == null) {
        return null;
      }
      var statistics = source.statistics();
      var latest = statistics.latest();
      return new PracticeTrackResponse(
          CandidateMemoryResponse.metadata(source.revision(), source.stablePatterns()),
          source.mastery(),
          new PracticeDetailsResponse(
              new PracticeStatisticsResponse(
                  statistics.completedByAssistance(), statistics.unresolvedCount()),
              new LatestPracticeResponse(
                  latest.episodeId(),
                  new PracticeResultResponse(
                      latest.result().outcome(),
                      latest.result().assistance(),
                      latest.result().targetDepth()
                  )),
              new TransferResponse(
                  source.transfer().status(), source.transfer().confirmedByEpisodeId())
          )
      );
    }
  }

  public record PracticeDetailsResponse(
      PracticeStatisticsResponse statistics,
      LatestPracticeResponse latest,
      TransferResponse transfer
  ) {}

  public record PracticeStatisticsResponse(
      Map<EpisodeAssistanceLevel, Long> completedByAssistance,
      long unresolvedCount
  ) {}

  public record LatestPracticeResponse(
      long episodeId,
      PracticeResultResponse result
  ) {}

  public record PracticeResultResponse(
      PracticeOutcome outcome,
      EpisodeAssistanceLevel assistance,
      DepthLevel targetDepth
  ) {}

  public record TransferResponse(
      TransferStatus status,
      Long confirmedByEpisodeId
  ) {}

  private static TrackMetadataResponse metadata(
      long revision,
      List<StablePattern> patterns
  ) {
    return new TrackMetadataResponse(
        revision,
        patterns.stream().map(StablePatternResponse::from).toList()
    );
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
