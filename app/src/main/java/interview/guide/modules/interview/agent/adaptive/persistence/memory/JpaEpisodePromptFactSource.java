package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodePromptCandidate;
import interview.guide.modules.interview.agent.adaptive.core.context.EpisodePromptFact;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodePromptFactSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagCategory;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 从 PostgreSQL 读取已完成历史 Episode 的安全投影。
 */
@Service
@RequiredArgsConstructor
public class JpaEpisodePromptFactSource implements EpisodePromptFactSource {

  private final EpisodeFactRepository episodeRepository;
  private final EpisodeTagRepository tagRepository;

  @Override
  @Transactional(readOnly = true)
  public List<EpisodePromptCandidate> findCompletedHistory(
      String currentSessionId,
      String skillId
  ) {
    List<EpisodePromptFactProjection> rows = episodeRepository
        .findCompletedPromptFacts(currentSessionId, skillId);
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<Long, List<EpisodePromptTagProjection>> tags = tagRepository
        .findPromptTagsByEpisodeIdIn(episodeIds(rows))
        .stream()
        .collect(Collectors.groupingBy(EpisodePromptTagProjection::getEpisodeId));
    return rows.stream()
        .map(row -> toCandidate(row, tags.getOrDefault(row.getEpisodeId(), List.of())))
        .toList();
  }

  private List<Long> episodeIds(List<EpisodePromptFactProjection> rows) {
    return rows.stream().map(EpisodePromptFactProjection::getEpisodeId).toList();
  }

  private EpisodePromptCandidate toCandidate(
      EpisodePromptFactProjection row,
      List<EpisodePromptTagProjection> tags
  ) {
    return new EpisodePromptCandidate(row.getEpisodeId(), new EpisodePromptFact(
        row.getSkillId(),
        row.getFocusId(),
        row.getDepthLevel(),
        tags(tags, EpisodeTagCategory.ERROR_PATTERN),
        tags(tags, EpisodeTagCategory.ANSWER_HABIT),
        row.getCreatedAt()
    ));
  }

  private List<String> tags(
      List<EpisodePromptTagProjection> tags,
      EpisodeTagCategory category
  ) {
    return tags.stream()
        .filter(tag -> tag.getCategory() == category)
        .map(EpisodePromptTagProjection::getTag)
        .toList();
  }
}
