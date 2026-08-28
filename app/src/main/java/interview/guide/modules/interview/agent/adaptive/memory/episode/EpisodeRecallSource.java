package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.List;

/** 按当前会话 owner 和 TopicKey 召回历史的分模式端口。 */
public interface EpisodeRecallSource {

  List<EvaluationRecallView> evaluation(
      String sessionId,
      TopicKey topic,
      String question
  );

  List<PracticeDiagnosticView> practice(
      String sessionId,
      TopicKey topic,
      String question
  );
}
