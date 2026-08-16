package interview.guide.modules.interview.agent.runtime;

import java.util.List;

/**
 * 旧版 Agent 回答评估上下文，包含当前问题、回答与历史轮次。
 */
public record AssessmentContext(
    String currentQuestion,
    String currentAnswer,
    List<InterviewTranscriptTurn> previousTurns
) {

  public AssessmentContext {
    previousTurns = List.copyOf(previousTurns);
  }
}
