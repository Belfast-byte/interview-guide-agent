package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionModelContext;
import interview.guide.modules.interview.agent.adaptive.runtime.InterviewDecisionModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/** 使用候选人选定模型生成 AgentDecision；语义拒绝由外层 Loop 统一处理。 */
@Slf4j
@Component
public class SpringAiInterviewDecisionModel implements InterviewDecisionModel {

  private final LlmProviderRegistry providerRegistry;
  private final StructuredOutputInvoker outputInvoker;
  private final InterviewDecisionPrompt prompt;

  public SpringAiInterviewDecisionModel(
      LlmProviderRegistry providerRegistry,
      StructuredOutputInvoker outputInvoker,
      InterviewDecisionPrompt prompt
  ) {
    this.providerRegistry = providerRegistry;
    this.outputInvoker = outputInvoker;
    this.prompt = prompt;
  }

  @Override
  public AgentDecision decide(DecisionModelContext context) {
    var identity = context.agentContext().session().identity();
    InterviewDecisionPrompt.PreparedPrompt prepared = prompt.prepare(context);
    ChatClient client = providerRegistry.getPlainChatClient(identity.llmProvider());
    InterviewDecisionOutput output = outputInvoker.invokeOnce(
        client,
        prepared.system(),
        prepared.user(),
        prepared.converter(),
        ErrorCode.AI_SERVICE_ERROR,
        "Interview Agent 决策解析失败: ",
        "interview_agent_decision",
        log
    );
    return output.toDomain();
  }
}
