package interview.guide.modules.interview.agent.adaptive.runtime;

import org.springframework.ai.chat.model.ChatResponse;

/** 只调用模型一次，不在此边界内执行工具或自动循环。 */
@FunctionalInterface
public interface InterviewDecisionModel {
  ChatResponse decide(DecisionModelContext context);
}
