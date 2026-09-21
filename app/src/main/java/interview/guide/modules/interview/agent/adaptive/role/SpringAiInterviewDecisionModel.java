package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionModelContext;
import interview.guide.modules.interview.agent.adaptive.runtime.InterviewDecisionModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/** 使用候选人选定模型返回单次原生响应；工具执行与语义拒绝由 Loop 控制。 */
@Slf4j
@Component
public class SpringAiInterviewDecisionModel implements InterviewDecisionModel {

  private final LlmProviderRegistry providerRegistry;
  private final InterviewDecisionPrompt prompt;
  private final AdaptiveModelOptionsFactory modelOptionsFactory;
  private final AdaptiveInputTokenBudget inputTokenBudget;
  private final AdaptiveAgentTelemetry telemetry;

  public SpringAiInterviewDecisionModel(
      LlmProviderRegistry providerRegistry,
      InterviewDecisionPrompt prompt,
      AdaptiveModelOptionsFactory modelOptionsFactory,
      AdaptiveInputTokenBudget inputTokenBudget,
      AdaptiveAgentTelemetry telemetry
  ) {
    this.providerRegistry = providerRegistry;
    this.prompt = prompt;
    this.modelOptionsFactory = modelOptionsFactory;
    this.inputTokenBudget = inputTokenBudget;
    this.telemetry = telemetry;
  }

  @Override
  public ChatResponse decide(DecisionModelContext context) {
    var identity = context.agentContext().session().identity();
    InterviewDecisionPrompt.PreparedPrompt prepared = prompt.prepare(context);
    inputTokenBudget.verify("interview_agent", prepared.system(), prepared.budgetInput());
    ChatClient client = providerRegistry.getPlainChatClient(identity.llmProvider())
        .mutate()
        .defaultOptions(modelOptionsFactory.interviewer(context.tools()))
        .build();
    return telemetry.observeTokenUsage(client, "interview_agent", identity.sessionId()).prompt()
        .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
        .system(prepared.system())
        .user(prepared.user())
        .messages(prepared.history())
        .call().chatResponse();
  }
}
