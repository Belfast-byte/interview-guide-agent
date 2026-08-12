package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningAgent;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.helpers.NOPLogger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class SpringAiPlanningAgent implements PlanningAgent {

  private static final int MAX_DIMENSIONS = 12;

  private final LlmProviderRegistry llmProviderRegistry;
  private final StructuredOutputInvoker structuredOutputInvoker;
  private final ObjectMapper objectMapper;
  private final AdaptiveAgentTelemetry telemetry;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final BeanOutputConverter<PlanProposal> outputConverter;

  public SpringAiPlanningAgent(
      LlmProviderRegistry llmProviderRegistry,
      StructuredOutputInvoker structuredOutputInvoker,
      ObjectMapper objectMapper,
      AdaptiveAgentTelemetry telemetry,
      AdaptiveAgentProperties properties,
      ResourceLoader resourceLoader
  ) throws IOException {
    this.llmProviderRegistry = llmProviderRegistry;
    this.structuredOutputInvoker = structuredOutputInvoker;
    this.objectMapper = objectMapper;
    this.telemetry = telemetry;
    this.systemPromptTemplate = new PromptTemplate(
        resourceLoader.getResource(properties.getPlannerSystemPromptPath())
            .getContentAsString(StandardCharsets.UTF_8)
    );
    this.userPromptTemplate = new PromptTemplate(
        resourceLoader.getResource(properties.getPlannerUserPromptPath())
            .getContentAsString(StandardCharsets.UTF_8)
    );
    this.outputConverter = new BeanOutputConverter<>(PlanProposal.class);
  }

  @Override
  public PlanProposal propose(PlanningRequest request, String llmProvider) {
    long startedNanos = System.nanoTime();
    PlanProposal proposal;
    try {
      String inputJson = serializeInput(request);
      String systemPrompt = systemPromptTemplate.render()
          + "\n\n"
          + outputConverter.getFormat();
      String userPrompt = userPromptTemplate.render(Map.of("inputJson", inputJson));
      ChatClient chatClient = llmProviderRegistry.getChatClientOrDefault(llmProvider);
      proposal = structuredOutputInvoker.invoke(
          chatClient,
          systemPrompt,
          userPrompt,
          outputConverter,
          ErrorCode.AI_SERVICE_ERROR,
          "Agent 规划失败：",
          "adaptive_agent_planning",
          NOPLogger.NOP_LOGGER
      );
    } catch (BusinessException e) {
      telemetry.modelCallFailed(
          "planner",
          request.sessionId(),
          0,
          e.getCode(),
          startedNanos
      );
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Agent 规划失败", e);
    }

    try {
      validate(proposal);
      telemetry.modelCallSucceeded("planner", "PLAN", startedNanos);
      return proposal;
    } catch (BusinessException e) {
      telemetry.modelCallFailed(
          "planner",
          request.sessionId(),
          0,
          e.getCode(),
          startedNanos
      );
      throw e;
    }
  }

  private String serializeInput(PlanningRequest request) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          "jd", request.jd(),
          "resume", request.resume()
      ));
    } catch (JacksonException e) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "规划上下文序列化失败", e);
    }
  }

  private void validate(PlanProposal proposal) {
    if (proposal == null
        || proposal.dimensions().isEmpty()
        || proposal.dimensions().size() > MAX_DIMENSIONS) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "规划结果必须包含 1 到 12 个维度"
      );
    }

    Set<String> dimensionNames = new HashSet<>();
    for (DimensionProposal dimension : proposal.dimensions()) {
      if (dimension.dimension() == null
          || dimension.dimension().isBlank()
          || dimension.focus() == null
          || dimension.focus().isBlank()) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "规划维度和考察重点不能为空");
      }
      if (dimension.suggestedTurns() < 1
          || dimension.suggestedTurns() > MAX_DIMENSIONS) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "规划建议轮次必须在 1 到 12 之间");
      }
      String normalizedName = dimension.dimension().trim().toLowerCase(Locale.ROOT);
      if (!dimensionNames.add(normalizedName)) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "规划结果包含重复维度");
      }
      if (dimension.suggestedTools().stream().anyMatch(String::isBlank)) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "建议工具标识不能为空");
      }
      if (dimension.suggestedSkill() != null && dimension.suggestedSkill().isBlank()) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "建议 Skill 标识不能为空");
      }
    }
  }
}
