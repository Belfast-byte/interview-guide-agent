package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SemanticMemoryRepositories {

  private final SemanticContributionRepository contributions;
  private final SemanticStateRepository states;
  private final EpisodeTagRepository tags;

  public SemanticContributionRepository contributions() {
    return contributions;
  }

  public SemanticStateRepository states() {
    return states;
  }

  public EpisodeTagRepository tags() {
    return tags;
  }
}
