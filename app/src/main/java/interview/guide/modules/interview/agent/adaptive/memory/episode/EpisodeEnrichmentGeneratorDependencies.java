package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.role.AdaptiveModelOptionsFactory;
import interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Episode 结构化生成器的基础设施依赖组。
 */
@Component
public record EpisodeEnrichmentGeneratorDependencies(
    LlmProviderRegistry llmProviderRegistry,
    StructuredOutputInvoker structuredOutputInvoker,
    ObjectMapper objectMapper,
    AdaptiveAgentTelemetry telemetry,
    AdaptiveInputTokenBudget inputTokenBudget,
    DeadlineExecutor deadlineExecutor,
    AdaptiveModelOptionsFactory modelOptionsFactory
) {}
