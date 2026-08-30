package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticContribution;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticContributionFactory;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticContributionInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SemanticMemoryPersistenceService {

  private final SemanticContributionRepository contributions;
  private final SemanticContributionFactory contributionFactory;

  @Transactional
  public SemanticContribution record(SemanticContributionInput input) {
    SemanticContribution contribution = contributionFactory.create(input);
    var existing = contributions
        .findByEpisodeIdAndTrack(
            contribution.source().episodeId(), contribution.track());
    if (existing.isPresent()) {
      return existing.orElseThrow().toDomain();
    }
    return contributions
        .saveAndFlush(new SemanticContributionEntity(contribution))
        .toDomain();
  }
}
