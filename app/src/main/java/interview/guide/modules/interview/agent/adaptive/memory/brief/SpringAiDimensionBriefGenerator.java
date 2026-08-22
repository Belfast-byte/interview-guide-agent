package interview.guide.modules.interview.agent.adaptive.memory.brief;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.memory.AbstractSpringAiMemoryGenerator;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 基于 Spring AI 的维度简报生成器。
 */
@Component
public class SpringAiDimensionBriefGenerator
    extends AbstractSpringAiMemoryGenerator<DimensionBriefRequest, DimensionBriefProposal>
    implements DimensionBriefGenerator {

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
    super(
        llmProviderRegistry,
        structuredOutputInvoker,
        objectMapper,
        telemetry,
        inputTokenBudget,
        deadlineExecutor
    );
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
    return generate(request, llmProvider, new GenerationSpec<>(
        systemPromptTemplate,
        userPromptTemplate,
        outputConverter,
        "memory_summarizer",
        "BRIEF",
        request.sessionId(),
        request.turns().getLast().turnIndex(),
        properties.getBriefDeadline(),
        "维度小结生成",
        "adaptive_dimension_brief",
        "维度小结生成失败",
        "维度小结上下文序列化失败"
    ));
  }
}
