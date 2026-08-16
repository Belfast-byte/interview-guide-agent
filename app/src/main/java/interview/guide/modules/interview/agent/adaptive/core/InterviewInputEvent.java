package interview.guide.modules.interview.agent.adaptive.core;

/**
 * 面试输入事件，统一封装候选人回答、工具结果等驱动循环的事件。
 */
public sealed interface InterviewInputEvent permits CandidateAnswer, ToolResultEvent {

  int turnIndex();
}
