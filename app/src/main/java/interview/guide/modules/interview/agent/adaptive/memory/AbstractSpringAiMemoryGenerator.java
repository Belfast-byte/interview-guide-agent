package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.role.AdaptiveModelOptionsFactory;
import interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 记忆类结构化生成器的共享实现：渲染提示词、校验输入预算、
 * 带 deadline 调用结构化输出，失败时记录遥测后原样抛出。
 *
 * @param <REQ> 请求类型
 * @param <RES> 结构化输出类型
 */
@Slf4j
public abstract class AbstractSpringAiMemoryGenerator<REQ, RES> {

  private final LlmProviderRegistry llmProviderRegistry;
  private final StructuredOutputInvoker structuredOutputInvoker;
  private final ObjectMapper objectMapper;
  private final AdaptiveAgentTelemetry telemetry;
  private final AdaptiveInputTokenBudget inputTokenBudget;
  private final DeadlineExecutor deadlineExecutor;
  private final AdaptiveModelOptionsFactory modelOptionsFactory;

  protected AbstractSpringAiMemoryGenerator(
      LlmProviderRegistry llmProviderRegistry,
      StructuredOutputInvoker structuredOutputInvoker,
      ObjectMapper objectMapper,
      AdaptiveAgentTelemetry telemetry,
      AdaptiveInputTokenBudget inputTokenBudget,
      DeadlineExecutor deadlineExecutor,
      AdaptiveModelOptionsFactory modelOptionsFactory
  ) {
    this.llmProviderRegistry = llmProviderRegistry;
    this.structuredOutputInvoker = structuredOutputInvoker;
    this.objectMapper = objectMapper;
    this.telemetry = telemetry;
    this.inputTokenBudget = inputTokenBudget;
    this.deadlineExecutor = deadlineExecutor;
    this.modelOptionsFactory = modelOptionsFactory;
  }

  protected RES generate(REQ request, String llmProvider, GenerationSpec<RES> spec) {
    long startedNanos = System.nanoTime();
    try {
      String systemPrompt = spec.systemPromptTemplate().render()
          + "\n\n"
          + spec.outputConverter().getFormat();
      String userPrompt = spec.userPromptTemplate().render(Map.of(
          "contextJson",
          serialize(request, spec)
      ));
      inputTokenBudget.verify(spec.model(), systemPrompt, userPrompt);
      ChatClient chatClient = telemetry.observeTokenUsage(
          llmProviderRegistry.getPlainChatClient(llmProvider).mutate()
              .defaultOptions(modelOptionsFactory.structured())
              .build(),
          spec.model(),
          spec.sessionId()
      );
      RES proposal = deadlineExecutor.invoke(
          () -> structuredOutputInvoker.invoke(
              chatClient,
              systemPrompt,
              userPrompt,
              spec.outputConverter(),
              ErrorCode.AI_SERVICE_ERROR,
              spec.failureMessage(),
              spec.logContext(),
              log
          ),
          System.nanoTime() + spec.deadline().toNanos(),
          spec.operationName()
      );
      telemetry.modelCallSucceeded(spec.model(), spec.telemetryEvent(), startedNanos);
      return proposal;
    } catch (BusinessException e) {
      telemetry.modelCallFailed(
          spec.model(),
          spec.sessionId(),
          spec.turnIndex(),
          e.getCode(),
          startedNanos
      );
      throw e;
    }
  }

  private String serialize(REQ request, GenerationSpec<RES> spec) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException e) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, spec.serializeFailureMessage(), e);
    }
  }

  /**
   * 单次生成所需的差异化配置，由子类按各自提示词、指标和文案组装。
   */
  public record GenerationSpec<RES>(
      PromptTemplate systemPromptTemplate,
      PromptTemplate userPromptTemplate,
      BeanOutputConverter<RES> outputConverter,
      String model,
      String telemetryEvent,
      String sessionId,
      int turnIndex,
      Duration deadline,
      String operationName,
      String logContext,
      String failureMessage,
      String serializeFailureMessage
  ) {}
}
