package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankVectorIndexRepository.IndexedSnapshot;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 题库向量索引持久化服务。
 */
@Service
@RequiredArgsConstructor
class QuestionBankVectorIndexPersistenceService {

  private final QuestionBankVectorIndexRepository repository;

  @Transactional
  public void markIndexed(List<IndexedSnapshot> snapshots) {
    repository.markIndexed(snapshots);
  }

  @Transactional
  public void deleteIndexEntries(List<Long> questionIds) {
    repository.deleteIndexEntries(questionIds);
  }
}
