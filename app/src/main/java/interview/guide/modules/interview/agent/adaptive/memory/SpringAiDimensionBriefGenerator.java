package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.helpers.NOPLogger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class SpringAiDimensionBriefGenerator implements DimensionBriefGenerator {

  private final LlmProviderRegistry llmProviderRegistry;
  private final StructuredOutputInvoker structuredOutputInvoker;
  private final ObjectMapper objectMapper;
  private final AdaptiveAgentTelemetry telemetry;
  private final AdaptiveInputTokenBudget inputTokenBudget;
  private final DeadlineExecutor deadlineExecutor;
  private final AdaptiveAgentProperties properties;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final BeanOutputConverter<DimensionBriefProposal> outputConverter;

  public SpringAiDimensionBriefGenerator(
      LlmProviderRegistry llmProviderRegistry,
      StructuredOutputInvoker structuredOutputInvoker,
      ObjectMapper objectMapper,
      AdaptiveAgentTelemetry telemetry,
      AdaptiveInputTokenBudget inputTokenBudget,
      DeadlineExecutor deadlineExecutor,
      AdaptiveAgentProperties properties,
      ResourceLoader resourceLoader
  ) throws IOException {
    this.llmProviderRegistry = llmProviderRegistry;
    this.structuredOutputInvoker = structuredOutputInvoker;
    this.objectMapper = objectMapper;
    this.telemetry = telemetry;
    this.inputTokenBudget = inputTokenBudget;
    this.deadlineExecutor = deadlineExecutor;
    this.properties = properties;
    this.systemPromptTemplate = new PromptTemplate(
        resourceLoader.getResource(properties.getBriefSystemPromptPath())
            .getContentAsString(StandardCharsets.UTF_8)
    );
    this.userPromptTemplate = new PromptTemplate(
        resourceLoader.getResource(properties.getBriefUserPromptPath())
            .getContentAsString(StandardCharsets.UTF_8)
    );
    this.outputConverter = new BeanOutputConverter<>(DimensionBriefProposal.class);
  }

  @Override
  public DimensionBriefProposal generate(
      DimensionBriefRequest request,
      String llmProvider
  ) {
    long startedNanos = System.nanoTime();
    try {
      String systemPrompt = systemPromptTemplate.render()
          + "\n\n"
          + outputConverter.getFormat();
      String userPrompt = userPromptTemplate.render(Map.of(
          "contextJson",
          serialize(request)
      ));
      inputTokenBudget.verify("memory_summarizer", systemPrompt, userPrompt);
      ChatClient chatClient = telemetry.observeTokenUsage(
          llmProviderRegistry.getChatClientOrDefault(llmProvider),
          "memory_summarizer",
          request.sessionId()
      );
      DimensionBriefProposal proposal = deadlineExecutor.invoke(
          () -> structuredOutputInvoker.invoke(
              chatClient,
              systemPrompt,
              userPrompt,
              outputConverter,
              ErrorCode.AI_SERVICE_ERROR,
              "维度小结生成失败",
              "adaptive_dimension_brief",
              NOPLogger.NOP_LOGGER
          ),
          System.nanoTime() + properties.getBriefDeadline().toNanos(),
          "维度小结生成"
      );
      telemetry.modelCallSucceeded("memory_summarizer", "BRIEF", startedNanos);
      return proposal;
    } catch (BusinessException e) {
      telemetry.modelCallFailed(
          "memory_summarizer",
          request.sessionId(),
          request.turns().getLast().turnIndex(),
          e.getCode(),
          startedNanos
      );
      throw new BusinessException(e.getCode(), "维度小结生成失败");
    }
  }

  private String serialize(DimensionBriefRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException e) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "维度小结上下文序列化失败", e);
    }
  }
}
