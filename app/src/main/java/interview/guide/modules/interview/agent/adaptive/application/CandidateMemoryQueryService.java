package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateAbilityProfileEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryEpisodeProjection;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryEpisodeQueryRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryProfileQueryRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryTagCountProjection;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryTagQueryRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  private final CandidateMemoryProfileQueryRepository profileRepository;
  private final CandidateMemoryTagQueryRepository tagRepository;
  private final CandidateMemoryEpisodeQueryRepository episodeRepository;

  @Transactional(readOnly = true)
  public CandidateMemoryQueryResult read(MemoryOwner owner, int page) {
    Map<TopicKey, List<CandidateMemoryQueryResult.TagCount>> tags = groupTags(
        tagRepository.countByOwner(owner)
    );
    List<CandidateMemoryQueryResult.TopicProfile> topics = profileRepository
        .findCurrentByOwner(owner)
        .stream()
        .map(profile -> toTopic(profile, tags))
        .toList();
    Page<CandidateMemoryQueryResult.Episode> episodes = episodeRepository
        .findByOwner(owner, PageRequest.of(page, PAGE_SIZE))
        .map(this::toEpisode);
    return new CandidateMemoryQueryResult(owner.candidateId(), topics, episodes);
  }

  private Map<TopicKey, List<CandidateMemoryQueryResult.TagCount>> groupTags(
      List<CandidateMemoryTagCountProjection> source
  ) {
    return source.stream().collect(Collectors.groupingBy(
        tag -> new TopicKey(tag.getSkillId(), tag.getFocusId()),
        LinkedHashMap::new,
        Collectors.mapping(this::toTagCount, Collectors.toList())
    ));
  }

  private CandidateMemoryQueryResult.TagCount toTagCount(
      CandidateMemoryTagCountProjection source
  ) {
    return new CandidateMemoryQueryResult.TagCount(
        source.getCategory(),
        source.getTag(),
        source.getTagCount()
    );
  }

  private CandidateMemoryQueryResult.TopicProfile toTopic(
      CandidateAbilityProfileEntity source,
      Map<TopicKey, List<CandidateMemoryQueryResult.TagCount>> tags
  ) {
    var profile = source.toDomain();
    return new CandidateMemoryQueryResult.TopicProfile(
        profile.topic(),
        profile.ability(),
        profile.counter(),
        tags.getOrDefault(profile.topic(), List.of())
    );
  }

  private CandidateMemoryQueryResult.Episode toEpisode(
      CandidateMemoryEpisodeProjection source
  ) {
    return new CandidateMemoryQueryResult.Episode(
        source.getSessionId(),
        source.getTurnIndex(),
        source.getParentTurnIndex(),
        new TopicKey(source.getSkillId(), source.getFocusId()),
        source.getDepthLevel(),
        source.getEnrichmentStatus(),
        source.getCreatedAt()
    );
  }
}
