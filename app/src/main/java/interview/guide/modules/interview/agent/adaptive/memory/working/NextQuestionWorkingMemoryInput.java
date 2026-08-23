package interview.guide.modules.interview.agent.adaptive.memory.working;

import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import java.util.List;

/** 正常回答评估后组装下一题工作记忆所需的事实。 */
public record NextQuestionWorkingMemoryInput(
    String sessionId,
    int currentTurnIndex,
    TopicKey currentTopic,
    List<ProbeGap> currentAssessmentGaps,
    List<ProbeGapCandidate> persistedGaps,
    List<AdaptiveInterviewTurn> history
) {

  public NextQuestionWorkingMemoryInput {
    currentAssessmentGaps = List.copyOf(currentAssessmentGaps);
    persistedGaps = List.copyOf(persistedGaps);
    history = List.copyOf(history);
  }
}
