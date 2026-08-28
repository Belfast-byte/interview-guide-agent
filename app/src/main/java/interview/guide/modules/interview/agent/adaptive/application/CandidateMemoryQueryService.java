package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluationSemanticState;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeSemanticState;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticState;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticStateSource;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryEpisodeProjection;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryEpisodeQueryRepository;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 组合 current Profile、标签统计和 Episode 页。 */
@Service
@RequiredArgsConstructor
public class CandidateMemoryQueryService {

  public static final int PAGE_SIZE = 20;

  private final SemanticStateSource stateSource;
  private final CandidateMemoryEpisodeQueryRepository episodeRepository;

  @Transactional(readOnly = true)
  public CandidateMemoryQueryResult read(MemoryOwner owner, int page) {
    List<CandidateMemoryQueryResult.TopicProfile> topics = topics(owner);
    Page<CandidateMemoryQueryResult.Episode> episodes = episodeRepository
        .findByOwner(owner, PageRequest.of(page, PAGE_SIZE))
        .map(this::toEpisode);
    List<CandidateMemoryQueryResult.Episode> ancestors = findAncestors(
        owner,
        episodes.getContent()
    );
    return new CandidateMemoryQueryResult(owner.candidateId(), topics, episodes, ancestors);
  }

  private List<CandidateMemoryQueryResult.TopicProfile> topics(MemoryOwner owner) {
    List<SemanticState> states = stateSource.findByOwner(owner);
    Map<TopicKey, EvaluationSemanticState> evaluations = states.stream()
        .filter(EvaluationSemanticState.class::isInstance)
        .map(EvaluationSemanticState.class::cast)
        .collect(Collectors.toMap(state -> state.key().topic(), state -> state));
    Map<TopicKey, PracticeSemanticState> practices = states.stream()
        .filter(PracticeSemanticState.class::isInstance)
        .map(PracticeSemanticState.class::cast)
        .collect(Collectors.toMap(state -> state.key().topic(), state -> state));
    Set<TopicKey> topics = new LinkedHashSet<>(evaluations.keySet());
    topics.addAll(practices.keySet());
    return topics.stream()
        .sorted(Comparator.comparing(TopicKey::skillId).thenComparing(TopicKey::focusId))
        .map(topic -> new CandidateMemoryQueryResult.TopicProfile(
            topic, evaluations.get(topic), practices.get(topic)))
        .toList();
  }

  private List<CandidateMemoryQueryResult.Episode> findAncestors(
      MemoryOwner owner,
      List<CandidateMemoryQueryResult.Episode> pageEpisodes
  ) {
    if (pageEpisodes.isEmpty()) {
      return List.of();
    }
    Set<String> sessionIds = pageEpisodes.stream()
        .map(CandidateMemoryQueryResult.Episode::sessionId)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    List<CandidateMemoryQueryResult.Episode> sessionEpisodes = episodeRepository
        .findByOwnerAndSessionIdIn(owner, sessionIds)
        .stream()
        .map(this::toEpisode)
        .toList();
    Map<EpisodeKey, CandidateMemoryQueryResult.Episode> episodesByKey = sessionEpisodes.stream()
        .collect(Collectors.toMap(this::keyOf, episode -> episode));
    Set<EpisodeKey> pageKeys = pageEpisodes.stream()
        .map(this::keyOf)
        .collect(Collectors.toSet());
    Set<EpisodeKey> ancestorKeys = collectAncestorKeys(pageEpisodes, episodesByKey);
    return sessionEpisodes.stream()
        .filter(episode -> ancestorKeys.contains(keyOf(episode)))
        .filter(episode -> !pageKeys.contains(keyOf(episode)))
        .toList();
  }

  private Set<EpisodeKey> collectAncestorKeys(
      List<CandidateMemoryQueryResult.Episode> pageEpisodes,
      Map<EpisodeKey, CandidateMemoryQueryResult.Episode> episodesByKey
  ) {
    Set<EpisodeKey> result = new LinkedHashSet<>();
    for (CandidateMemoryQueryResult.Episode episode : pageEpisodes) {
      Integer parentTurnIndex = episode.parentTurnIndex();
      while (parentTurnIndex != null) {
        EpisodeKey parentKey = new EpisodeKey(episode.sessionId(), parentTurnIndex);
        CandidateMemoryQueryResult.Episode parent = requireParent(episodesByKey, parentKey);
        result.add(parentKey);
        parentTurnIndex = parent.parentTurnIndex();
      }
    }
    return result;
  }

  private CandidateMemoryQueryResult.Episode requireParent(
      Map<EpisodeKey, CandidateMemoryQueryResult.Episode> episodesByKey,
      EpisodeKey parentKey
  ) {
    CandidateMemoryQueryResult.Episode parent = episodesByKey.get(parentKey);
    if (parent == null) {
      throw new IllegalStateException(
          "Episode 追问链缺少父事实: sessionId=%s, turnIndex=%s"
              .formatted(parentKey.sessionId(), parentKey.turnIndex())
      );
    }
    return parent;
  }

  private EpisodeKey keyOf(CandidateMemoryQueryResult.Episode episode) {
    return new EpisodeKey(episode.sessionId(), episode.turnIndex());
  }

  private CandidateMemoryQueryResult.Episode toEpisode(
      CandidateMemoryEpisodeProjection source
  ) {
    return new CandidateMemoryQueryResult.Episode(
        source.getSessionId(),
        source.getTurnIndex(),
        source.getParentTurnIndex(),
        source.getTriggerType(),
        new TopicKey(source.getSkillId(), source.getFocusId()),
        source.getDepthLevel(),
        source.getEnrichmentStatus(),
        source.getCreatedAt()
    );
  }

  private record EpisodeKey(String sessionId, int turnIndex) {}
}
