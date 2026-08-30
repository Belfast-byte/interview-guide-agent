package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.PromptLoader;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentModelGateway;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActModelContext;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolObservation;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 基于 Spring AI 的自适应 Agent 模型网关，按角色组装 Prompt 并调用 LLM。
 */
@Component
@Slf4j
public class SpringAiAdaptiveAgentModelGateway implements AgentModelGateway {

  private final LlmProviderRegistry llmProviderRegistry;
  private final ObjectMapper objectMapper;
  private final AdaptiveAgentTelemetry telemetry;
  private final AdaptiveInputTokenBudget inputTokenBudget;
  private final AdaptiveModelOptionsFactory modelOptionsFactory;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final AdaptiveAgentResponseMapper responseMapper;

  public SpringAiAdaptiveAgentModelGateway(
      LlmProviderRegistry llmProviderRegistry,
      ObjectMapper objectMapper,
      AdaptiveAgentTelemetry telemetry,
      AdaptiveInputTokenBudget inputTokenBudget,
      AdaptiveModelOptionsFactory modelOptionsFactory,
      AdaptiveAgentResponseMapper responseMapper,
      AdaptiveAgentProperties properties,
      PromptLoader promptLoader
  ) {
    this.llmProviderRegistry = llmProviderRegistry;
    this.objectMapper = objectMapper;
    this.telemetry = telemetry;
    this.inputTokenBudget = inputTokenBudget;
    this.modelOptionsFactory = modelOptionsFactory;
    this.responseMapper = responseMapper;
    this.systemPromptTemplate = promptLoader.loadTemplate(properties.getSystemPromptPath());
    this.userPromptTemplate = promptLoader.loadTemplate(properties.getUserPromptPath());
  }

  @Override
  public AgentAction nextAction(ReActModelContext context) {
    long startedNanos = System.nanoTime();
    ChatResponse response;
    try {
      response = callModel(context);
    } catch (Exception e) {
      recordFailure(context, startedNanos, ErrorCode.AI_SERVICE_ERROR.getCode());
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent interview model call failed",
          e
      );
    }

    try {
      AgentAction action;
      try {
        action = responseMapper.map(response, context);
      } catch (AdaptiveAgentResponseMapper.ModelOutputRejectionException e) {
        log.info(
            "adaptive_agent_output_rejected role=interviewer sessionId={} inputTurn={} reason={}",
            context.request().sessionId(),
            context.request().inputTurnIndex(),
            e.getMessage()
        );
        ReActModelContext retryContext = withOutputRejection(context, e.getMessage());
        action = responseMapper.map(callModel(retryContext), retryContext);
      }
      telemetry.modelCallSucceeded(
          "interviewer",
          action instanceof RespondAction respond ? respond.type().name() : "TOOL_CALL",
          startedNanos
      );
      return action;
    } catch (BusinessException e) {
      recordFailure(context, startedNanos, e.getCode());
      throw e;
    } catch (Exception e) {
      recordFailure(context, startedNanos, ErrorCode.AI_SERVICE_ERROR.getCode());
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent interview decision failed",
          e
      );
    }
  }

  private ReActModelContext withOutputRejection(
      ReActModelContext context,
      String reason
  ) {
    ToolObservation rejection = new ToolObservation(
        "model_output_validation",
        Map.of(),
        false,
        null,
        reason + "; fix the output and respond again"
    );
    return new ReActModelContext(
        context.request(),
        Stream.concat(context.observations().stream(), Stream.of(rejection)).toList()
    );
  }

  @Override
  public AgentAction nextActionStreaming(ReActModelContext context, Consumer<String> deltaSink) {
    long startedNanos = System.nanoTime();
    try {
      AgentAction action;
      try {
        action = streamAndMap(context, deltaSink);
      } catch (AdaptiveAgentResponseMapper.ModelOutputRejectionException e) {
        log.info(
            "adaptive_agent_output_rejected role=interviewer sessionId={} inputTurn={} reason={}",
            context.request().sessionId(),
            context.request().inputTurnIndex(),
            e.getMessage()
        );
        ReActModelContext retryContext = withOutputRejection(context, e.getMessage());
        action = streamAndMap(retryContext, deltaSink);
      }
      telemetry.modelCallSucceeded(
          "interviewer",
          action instanceof RespondAction respond ? respond.type().name() : "TOOL_CALL",
          startedNanos
      );
      return action;
    } catch (BusinessException e) {
      recordFailure(context, startedNanos, e.getCode());
      throw e;
    } catch (Exception e) {
      recordFailure(context, startedNanos, ErrorCode.AI_SERVICE_ERROR.getCode());
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent interview decision failed",
          e
      );
    }
  }

  /** 流式转发文本，同时聚合完整响应以保留工具调用。 */
  private AgentAction streamAndMap(ReActModelContext context, Consumer<String> deltaSink) {
    ModelPrompt prompt = preparePrompt(context);
    AtomicReference<ChatResponse> aggregatedResponse = new AtomicReference<>();
    var responseFlux = prompt.chatClient().prompt()
        .system(prompt.systemPrompt())
        .user(prompt.userPrompt())
        .options(modelOptionsFactory.interviewer(List.of()))
        .advisors(advisor -> advisor.param(
            ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(),
            false
        ))
        .stream()
        .chatResponse();
    new MessageAggregator()
        .aggregate(responseFlux, aggregatedResponse::set)
        .doOnNext(chunk -> forwardTextDelta(chunk, deltaSink))
        .blockLast();
    return responseMapper.map(aggregatedResponse.get(), context);
  }

  private void forwardTextDelta(ChatResponse chunk, Consumer<String> deltaSink) {
    if (chunk.getResults() == null || chunk.getResults().isEmpty()) {
      return;
    }
    AssistantMessage output = chunk.getResult().getOutput();
    String delta = output.getText();
    if (delta != null && !delta.isEmpty()) {
      deltaSink.accept(delta);
    }
  }

  private record ModelPrompt(String systemPrompt, String userPrompt, ChatClient chatClient) {}

  private ModelPrompt preparePrompt(ReActModelContext context) {
    String systemPrompt = systemPromptTemplate.render()
        + "\n\n"
        + responseMapper.format();
    String userPrompt = userPromptTemplate.render(Map.of(
        "contextJson",
        serializeContext(context)
    ));
    inputTokenBudget.verify("interviewer", systemPrompt, userPrompt);
    ChatClient chatClient = telemetry.observeTokenUsage(
        llmProviderRegistry.getPlainChatClient(context.request().llmProvider()),
        "interviewer",
        context.request().sessionId()
    );
    return new ModelPrompt(systemPrompt, userPrompt, chatClient);
  }

  private ChatResponse callModel(ReActModelContext context) {
    ModelPrompt prompt = preparePrompt(context);
    return prompt.chatClient().prompt()
        .system(prompt.systemPrompt())
        .user(prompt.userPrompt())
        .options(modelOptionsFactory.interviewer(List.of()))
        // 旧 ReAct 路径不再声明或执行 Tool；只读 Tool 统一由 InterviewAgentLoop 驱动。
        .advisors(advisor -> advisor.param(
            ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(),
            false
        ))
        .call()
        .chatResponse();
  }

  private void recordFailure(
      ReActModelContext context,
      long startedNanos,
      int errorCode
  ) {
    telemetry.modelCallFailed(
        "interviewer",
        context.request().sessionId(),
        inputTurn(context),
        errorCode,
        startedNanos
    );
  }


  private int inputTurn(ReActModelContext context) {
    return context.request().inputTurnIndex();
  }

  private String serializeContext(ReActModelContext context) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          "interviewContext", context.request().interviewerContext().modelView(),
          "observations", context.observations()
      ));
    } catch (JacksonException e) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent context serialization failed",
          e
      );
    }
  }

}
