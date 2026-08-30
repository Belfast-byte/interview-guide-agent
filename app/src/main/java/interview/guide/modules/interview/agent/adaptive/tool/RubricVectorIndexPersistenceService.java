package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.tool.RubricVectorIndexRepository.IndexedSnapshot;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** vector 写成功后提交 rubric 索引元数据。 */
@Service
@RequiredArgsConstructor
class RubricVectorIndexPersistenceService {

  private final RubricVectorIndexRepository repository;

  @Transactional
  public void markIndexed(List<IndexedSnapshot> snapshots) {
    repository.markIndexed(snapshots);
  }

  @Transactional
  public void deleteIndexEntries(List<Long> questionIds) {
    repository.deleteIndexEntries(questionIds);
  }
}
