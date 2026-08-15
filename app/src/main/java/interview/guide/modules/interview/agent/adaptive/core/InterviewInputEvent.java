package interview.guide.modules.interview.agent.adaptive.core;

public sealed interface InterviewInputEvent permits CandidateAnswer, ToolResultEvent {

  int turnIndex();
}
