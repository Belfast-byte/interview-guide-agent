package interview.guide.modules.interview.agent.adaptive.assessment.depth;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.PromptLoader;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 基于 Spring AI 的评估建议生成器。
 */
@Component
@Slf4j
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
  private final String assessmentExamples;

  public SpringAiAssessmentProposalGenerator(
      LlmProviderRegistry llmProviderRegistry,
      StructuredOutputInvoker structuredOutputInvoker,
      ObjectMapper objectMapper,
      AdaptiveAgentTelemetry telemetry,
      AdaptiveInputTokenBudget inputTokenBudget,
      DeadlineExecutor deadlineExecutor,
      AdaptiveAgentProperties properties,
      PromptLoader promptLoader
  ) {
    this.llmProviderRegistry = llmProviderRegistry;
    this.structuredOutputInvoker = structuredOutputInvoker;
    this.objectMapper = objectMapper;
    this.telemetry = telemetry;
    this.inputTokenBudget = inputTokenBudget;
    this.deadlineExecutor = deadlineExecutor;
    this.properties = properties;
    this.systemPromptTemplate = promptLoader.loadTemplate(
        properties.getAssessmentSystemPromptPath()
    );
    this.userPromptTemplate = promptLoader.loadTemplate(
        properties.getAssessmentUserPromptPath()
    );
    this.assessmentExamples = promptLoader.loadText(properties.getAssessmentExamplesPath());
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
          + assessmentExamples
          + "\n\n"
          + skillReferenceSection(request.skillReferenceSection())
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
              log
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

  private String skillReferenceSection(String skillReferenceSection) {
    if (skillReferenceSection == null || skillReferenceSection.isBlank()) {
      return "";
    }
    return "# Skill reference baseline\n"
        + skillReferenceSection
        + "\n\n";
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
