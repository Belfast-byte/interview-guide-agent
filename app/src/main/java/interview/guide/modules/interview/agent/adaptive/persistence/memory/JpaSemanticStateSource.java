package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticContribution;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticPatternSource;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticState;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticStateProjector;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticStateSource;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaSemanticStateSource implements SemanticStateSource {

  private final SemanticContributionRepository contributionRepository;
  private final EpisodeTagRepository tagRepository;
  private final SemanticStateProjector projector;

  @Override
  public List<SemanticState> findByOwner(MemoryOwner owner) {
    List<SemanticContribution> contributions = contributionRepository.findByOwner(owner)
        .stream()
        .map(SemanticContributionEntity::toDomain)
        .toList();
    if (contributions.isEmpty()) {
      return List.of();
    }
    List<Long> episodeIds = contributions.stream()
        .map(contribution -> contribution.source().episodeId())
        .toList();
    List<SemanticPatternSource> patterns = tagRepository.findByEpisodeIdIn(episodeIds)
        .stream()
        .map(EpisodeTagEntity::toDomain)
        .map(tag -> new SemanticPatternSource(tag.episodeId(), tag.value()))
        .toList();
    return projector.project(contributions, patterns);
  }
}
