package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemorySnapshot;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import java.util.List;

/** 下一题 Interviewer 决策的应用层输入。 */
record InterviewerDecisionInput(
    String sessionId,
    String llmProvider,
    String jd,
    String resume,
    int maxTurns,
    PlannedDimension dimension,
    List<AdaptiveInterviewTurn> turns,
    CandidateAnswer candidateAnswer,
    WorkingMemorySnapshot workingMemory
) {

  InterviewerDecisionInput {
    turns = List.copyOf(turns);
  }
}
