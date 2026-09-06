package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.memory.AbstractSpringAiMemoryGenerator;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.role.AdaptiveModelOptionsFactory;
import interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 基于 Spring AI 的 Episode 结构化补全生成器。
 */
@Component
public class SpringAiEpisodeEnrichmentGenerator
    extends AbstractSpringAiMemoryGenerator<EpisodeEnrichmentRequest, EpisodeEnrichmentProposal>
    implements EpisodeEnrichmentGenerator {

  private final AdaptiveAgentProperties properties;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final BeanOutputConverter<EpisodeEnrichmentProposal> outputConverter;

  public SpringAiEpisodeEnrichmentGenerator(
      LlmProviderRegistry llmProviderRegistry,
      StructuredOutputInvoker structuredOutputInvoker,
      ObjectMapper objectMapper,
      AdaptiveAgentTelemetry telemetry,
      AdaptiveInputTokenBudget inputTokenBudget,
      DeadlineExecutor deadlineExecutor,
      AdaptiveModelOptionsFactory modelOptionsFactory,
      AdaptiveAgentProperties properties,
      ResourceLoader resourceLoader
  ) throws IOException {
    super(
        llmProviderRegistry,
        structuredOutputInvoker,
        objectMapper,
        telemetry,
        inputTokenBudget,
        deadlineExecutor,
        modelOptionsFactory
    );
    this.properties = properties;
    systemPromptTemplate = prompt(
        resourceLoader,
        properties.getEpisodeEnrichmentSystemPromptPath()
    );
    userPromptTemplate = prompt(
        resourceLoader,
        properties.getEpisodeEnrichmentUserPromptPath()
    );
    outputConverter = new BeanOutputConverter<>(EpisodeEnrichmentProposal.class);
  }

  @Override
  public EpisodeEnrichmentProposal generate(
      EpisodeEnrichmentRequest request,
      String llmProvider
  ) {
    return generate(request, llmProvider, new GenerationSpec<>(
        systemPromptTemplate,
        userPromptTemplate,
        outputConverter,
        "memory_summarizer",
        "EPISODE",
        request.sessionId(),
        request.turnIndex(),
        properties.getEpisodeEnrichmentDeadline(),
        "Episode 补全生成",
        "adaptive_episode_enrichment",
        "Episode 补全生成失败",
        "Episode 补全上下文序列化失败"
    ));
  }

  private PromptTemplate prompt(ResourceLoader loader, String path) throws IOException {
    return new PromptTemplate(
        loader.getResource(path).getContentAsString(StandardCharsets.UTF_8)
    );
  }
}
