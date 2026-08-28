package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.List;

/** 候选人题目向量召回端口。 */
public interface QuestionSimilaritySearch {

  List<QuestionSimilarityHit> search(
      MemoryOwner owner,
      TopicKey topic,
      String question
  );
}
