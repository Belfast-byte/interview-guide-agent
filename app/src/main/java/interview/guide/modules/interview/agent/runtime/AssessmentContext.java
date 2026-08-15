package interview.guide.modules.interview.agent.runtime;

import java.util.List;

public record AssessmentContext(
    String currentQuestion,
    String currentAnswer,
    List<InterviewTranscriptTurn> previousTurns
) {

  public AssessmentContext {
    previousTurns = List.copyOf(previousTurns);
  }
}
