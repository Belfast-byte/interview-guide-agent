package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.PromptLoader;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningAgent;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningRequest;
import interview.guide.modules.interview.agent.adaptive.planning.PlannerContext;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMemoryService.PracticePlanningMemory;
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
 * 基于 Spring AI 的规划 Agent 实现，将 JD/简历/记忆转换为结构化面试计划。
 */
@Component
@Slf4j
public class SpringAiPlanningAgent implements PlanningAgent {

  private final LlmProviderRegistry llmProviderRegistry;
  private final StructuredOutputInvoker structuredOutputInvoker;
  private final ObjectMapper objectMapper;
  private final AdaptiveAgentTelemetry telemetry;
  private final AdaptiveInputTokenBudget inputTokenBudget;
  private final DeadlineExecutor deadlineExecutor;
  private final AdaptiveAgentProperties properties;
  private final AdaptiveModelOptionsFactory modelOptionsFactory;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final BeanOutputConverter<PlanProposal> outputConverter;

  public SpringAiPlanningAgent(
      LlmProviderRegistry llmProviderRegistry,
      StructuredOutputInvoker structuredOutputInvoker,
      ObjectMapper objectMapper,
      AdaptiveAgentTelemetry telemetry,
      AdaptiveInputTokenBudget inputTokenBudget,
      DeadlineExecutor deadlineExecutor,
      AdaptiveAgentProperties properties,
      AdaptiveModelOptionsFactory modelOptionsFactory,
      PromptLoader promptLoader
  ) {
    this.llmProviderRegistry = llmProviderRegistry;
    this.structuredOutputInvoker = structuredOutputInvoker;
    this.objectMapper = objectMapper;
    this.telemetry = telemetry;
    this.inputTokenBudget = inputTokenBudget;
    this.deadlineExecutor = deadlineExecutor;
    this.properties = properties;
    this.modelOptionsFactory = modelOptionsFactory;
    this.systemPromptTemplate = promptLoader.loadTemplate(properties.getPlannerSystemPromptPath());
    this.userPromptTemplate = promptLoader.loadTemplate(properties.getPlannerUserPromptPath());
    this.outputConverter = StructuredOutputInvoker.strictConverter(PlanProposal.class);
  }

  @Override
  public PlanProposal propose(PlanningRequest request, String llmProvider) {
    long startedNanos = System.nanoTime();
    PlanProposal proposal;
    try {
      String inputJson = serializeInput(request);
      String systemPrompt = systemPromptTemplate.render()
          + "\n\n"
          + outputConverter.getFormat()
          + (request.codeRepairFirst()
              ? "\n\n候选人面试流程以 Java 代码改错题作为首题：读取 JD 和简历后直接生成完整 Java 业务代码改错任务。"
                  + "initialQuestion.questionType 必须为 CODE_REPAIR，codeTask 必须完整，codeTaskTurnIndex=null。"
                  + "此首题要求优先于自主选择首题题型；具体场景和考察内容仍由你根据材料与练习范围决定。"
              : "");
      String userPrompt = userPromptTemplate.render(Map.of("inputJson", inputJson));
      inputTokenBudget.verify("planner", systemPrompt, userPrompt);
      // 规划是无工具的结构化输出：使用 plain client，避免默认工具 advisor 引入隐藏的额外往返
      ChatClient chatClient = plannerClient(llmProvider, request.sessionId());
      proposal = deadlineExecutor.invoke(
          () -> structuredOutputInvoker.invokeOnce(
              chatClient,
              systemPrompt,
              userPrompt,
              outputConverter,
              ErrorCode.AI_SERVICE_ERROR,
              "Agent 规划失败：",
              "adaptive_agent_planning",
              log
          ),
          System.nanoTime() + properties.getPlannerDeadline().toNanos(),
          "Agent 规划执行"
      );
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

    telemetry.modelCallSucceeded("planner", "PLAN", startedNanos);
    return proposal;
  }

  private ChatClient plannerClient(String llmProvider, String sessionId) {
    ChatClient boundedClient = llmProviderRegistry.getPlainChatClient(llmProvider)
        .mutate()
        .defaultOptions(modelOptionsFactory.planner())
        .build();
    return telemetry.observeTokenUsage(boundedClient, "planner", sessionId);
  }

  private String serializeInput(PlanningRequest request) {
    try {
      Object input = request.context().mode() == SessionMode.PRACTICE
          ? new PracticePlanningModelInput(request.context(), request.practiceMemory())
          : new EvaluationPlanningModelInput(request.context());
      return objectMapper.writeValueAsString(input);
    } catch (JacksonException e) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "规划上下文序列化失败", e);
    }
  }

  private record EvaluationPlanningModelInput(PlannerContext interview) {}

  private record PracticePlanningModelInput(
      PlannerContext interview,
      PracticePlanningMemory semanticMemory
  ) {}

}
