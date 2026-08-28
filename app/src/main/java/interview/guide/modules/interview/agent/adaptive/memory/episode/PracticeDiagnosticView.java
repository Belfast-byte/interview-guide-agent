package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.List;

/** 练习 Coach 可见的完整历史诊断。 */
public record PracticeDiagnosticView(
    long exposureId,
    Long episodeId,
    TopicKey topic,
    String question,
    String answer,
    DepthLevel rating,
    double confidence,
    List<String> evidence,
    List<ProbeGap> gaps,
    List<String> toolFacts,
    EpisodeAssistanceLevel assistanceLevel,
    EpisodeClosureStatus closureStatus,
    double similarity
) {

  public PracticeDiagnosticView {
    evidence = List.copyOf(evidence);
    gaps = List.copyOf(gaps);
    toolFacts = List.copyOf(toolFacts);
  }
}
