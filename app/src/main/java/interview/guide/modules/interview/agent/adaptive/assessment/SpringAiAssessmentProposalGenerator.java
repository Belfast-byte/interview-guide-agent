package interview.guide.modules.interview.agent.adaptive.assessment;

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

/**
 * 基于 Spring AI 的评估建议生成器。
 */
@Component
public class SpringAiAssessmentProposalGenerator
    implements AssessmentProposalGenerator {

  private final LlmProviderRegistry llmProviderRegistry;
  private final StructuredOutputInvoker structuredOutputInvoker;
  private final ObjectMapper objectMapper;
  private final AdaptiveAgentTelemetry telemetry;
  private final AdaptiveInputTokenBudget inputTokenBudget;
  private final DeadlineExecutor deadlineExecutor;
  private final AdaptiveAgentProperties properties;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final BeanOutputConverter<AssessmentProposal> outputConverter;

  public SpringAiAssessmentProposalGenerator(
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
        resourceLoader.getResource(properties.getAssessmentSystemPromptPath())
            .getContentAsString(StandardCharsets.UTF_8)
    );
    this.userPromptTemplate = new PromptTemplate(
        resourceLoader.getResource(properties.getAssessmentUserPromptPath())
            .getContentAsString(StandardCharsets.UTF_8)
    );
    this.outputConverter = new BeanOutputConverter<>(AssessmentProposal.class);
  }

  @Override
  public AssessmentProposal generate(
      AssessmentRequest request,
      String llmProvider
  ) {
    long startedNanos = System.nanoTime();
    try {
      String systemPrompt = systemPromptTemplate.render()
          + "\n\n"
          + outputConverter.getFormat();
      String userPrompt = userPromptTemplate.render(Map.of(
          "contextJson",
          serialize(request.context())
      ));
      inputTokenBudget.verify("depth_assessor", systemPrompt, userPrompt);
      ChatClient chatClient = telemetry.observeTokenUsage(
          llmProviderRegistry.getChatClientOrDefault(llmProvider),
          "depth_assessor",
          request.sessionId()
      );
      AssessmentProposal proposal = deadlineExecutor.invoke(
          () -> structuredOutputInvoker.invoke(
              chatClient,
              systemPrompt,
              userPrompt,
              outputConverter,
              ErrorCode.AI_SERVICE_ERROR,
              "回答深度评估失败",
              "adaptive_depth_assessment",
              NOPLogger.NOP_LOGGER
          ),
          System.nanoTime() + properties.getAssessmentDeadline().toNanos(),
          "回答深度评估"
      );
      telemetry.modelCallSucceeded("depth_assessor", "ASSESS", startedNanos);
      return proposal;
    } catch (BusinessException e) {
      telemetry.modelCallFailed(
          "depth_assessor",
          request.sessionId(),
          request.turnIndex(),
          e.getCode(),
          startedNanos
      );
      throw e;
    }
  }

  private String serialize(AssessmentContext context) {
    try {
      return objectMapper.writeValueAsString(context);
    } catch (JacksonException e) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "回答深度评估上下文序列化失败",
          e
      );
    }
  }
}
