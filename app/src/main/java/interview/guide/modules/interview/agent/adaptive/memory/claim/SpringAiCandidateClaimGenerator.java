package interview.guide.modules.interview.agent.adaptive.memory.claim;

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
 * 基于 Spring AI 的候选人声明生成器。
 */
@Component
public class SpringAiCandidateClaimGenerator
    extends AbstractSpringAiMemoryGenerator<CandidateClaimExtractionRequest, CandidateClaimsProposal>
    implements CandidateClaimGenerator {

  private final AdaptiveAgentProperties properties;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final BeanOutputConverter<CandidateClaimsProposal> outputConverter;

  public SpringAiCandidateClaimGenerator(
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
        resourceLoader.getResource(properties.getClaimSystemPromptPath())
            .getContentAsString(StandardCharsets.UTF_8)
    );
    this.userPromptTemplate = new PromptTemplate(
        resourceLoader.getResource(properties.getClaimUserPromptPath())
            .getContentAsString(StandardCharsets.UTF_8)
    );
    this.outputConverter = new BeanOutputConverter<>(CandidateClaimsProposal.class);
  }

  @Override
  public CandidateClaimsProposal generate(
      CandidateClaimExtractionRequest request,
      String llmProvider
  ) {
    return generate(request, llmProvider, new GenerationSpec<>(
        systemPromptTemplate,
        userPromptTemplate,
        outputConverter,
        "memory_claim_extractor",
        "CLAIMS",
        request.sessionId(),
        request.turns().getLast().turnIndex(),
        properties.getClaimDeadline(),
        "候选人声明抽取",
        "adaptive_candidate_claims",
        "候选人声明抽取失败",
        "声明抽取上下文序列化失败"
    ));
  }
}
