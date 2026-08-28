package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticState;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticStateSource;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaSemanticStateSource implements SemanticStateSource {

  private final SemanticStateRepository repository;

  @Override
  public List<SemanticState> findByOwner(MemoryOwner owner) {
    return repository.findByOwner(owner).stream()
        .map(SemanticStateEntity::toDomain)
        .toList();
  }
}
