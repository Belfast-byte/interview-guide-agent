package interview.guide.modules.interview.agent.runtime;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 基于 Spring AI 的 Agent 模型网关实现，负责组装 Prompt 并调用 LLM 获取结构化决策。
 */
@Slf4j
@Component
public class SpringAiAgentModelGateway implements AgentModelGateway {

  private final LlmProviderRegistry llmProviderRegistry;
  private final StructuredOutputInvoker structuredOutputInvoker;
  private final ObjectMapper objectMapper;
  private final BeanOutputConverter<AgentStepOutput> outputConverter;
  private final BeanOutputConverter<AssessmentOutput> assessmentOutputConverter;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final PromptTemplate assessmentSystemPromptTemplate;
  private final PromptTemplate assessmentUserPromptTemplate;

  public SpringAiAgentModelGateway(
      LlmProviderRegistry llmProviderRegistry,
      StructuredOutputInvoker structuredOutputInvoker,
      ObjectMapper objectMapper,
      @Value("classpath:prompts/agent-interview-loop-system.st") Resource systemPrompt,
      @Value("classpath:prompts/agent-interview-loop-user.st") Resource userPrompt,
      @Value("classpath:prompts/agent-interview-assessment-system.st")
      Resource assessmentSystemPrompt,
      @Value("classpath:prompts/agent-interview-assessment-user.st")
      Resource assessmentUserPrompt
  ) throws IOException {
    this.llmProviderRegistry = llmProviderRegistry;
    this.structuredOutputInvoker = structuredOutputInvoker;
    this.objectMapper = objectMapper;
    this.outputConverter = new BeanOutputConverter<>(AgentStepOutput.class) {};
    this.assessmentOutputConverter = new BeanOutputConverter<>(AssessmentOutput.class) {};
    this.systemPromptTemplate = new PromptTemplate(
        systemPrompt.getContentAsString(StandardCharsets.UTF_8)
    );
    this.userPromptTemplate = new PromptTemplate(
        userPrompt.getContentAsString(StandardCharsets.UTF_8)
    );
    this.assessmentSystemPromptTemplate = new PromptTemplate(
        assessmentSystemPrompt.getContentAsString(StandardCharsets.UTF_8)
    );
    this.assessmentUserPromptTemplate = new PromptTemplate(
        assessmentUserPrompt.getContentAsString(StandardCharsets.UTF_8)
    );
  }

  @Override
  public AssessmentResult assess(AssessmentContext context) {
    try {
      String systemPrompt = assessmentSystemPromptTemplate.render(Map.of(
          "format", assessmentOutputConverter.getFormat()
      ));
      String userPrompt = assessmentUserPromptTemplate.render(Map.of(
          "contextJson", objectMapper.writeValueAsString(context)
      ));

      ChatClient chatClient = llmProviderRegistry.getPlainChatClient(null);
      AssessmentOutput output = structuredOutputInvoker.invokeOnce(
          chatClient,
          systemPrompt,
          userPrompt,
          assessmentOutputConverter,
          ErrorCode.AGENT_INTERVIEW_DECISION_FAILED,
          "Agent 回答评估失败: ",
          "Agent 面试回答评估",
          log
      );
      return toAssessment(output);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("Agent 回答评估失败", e);
      throw new BusinessException(
          ErrorCode.AGENT_INTERVIEW_DECISION_FAILED,
          "Agent 回答评估失败",
          e
      );
    }
  }

  @Override
  public AgentStep nextStep(InterviewAgentContext context) {
    try {
      String systemPrompt = systemPromptTemplate.render(Map.of(
          "format", outputConverter.getFormat()
      ));
      String userPrompt = userPromptTemplate.render(Map.of(
          "contextJson", objectMapper.writeValueAsString(context)
      ));

      ChatClient chatClient = llmProviderRegistry.getPlainChatClient(null);
      AgentStepOutput output = structuredOutputInvoker.invokeOnce(
          chatClient,
          systemPrompt,
          userPrompt,
          outputConverter,
          ErrorCode.AGENT_INTERVIEW_DECISION_FAILED,
          "Agent 单步模型决策失败: ",
          "Agent 面试决策",
          log
      );
      return toStep(output);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("Agent 单步模型决策失败", e);
      throw new BusinessException(
          ErrorCode.AGENT_INTERVIEW_DECISION_FAILED,
          "Agent 单步模型决策失败",
          e
      );
    }
  }

  private AgentStep toStep(AgentStepOutput output) {
    if (output == null || output.action() == null) {
      throw new BusinessException(
          ErrorCode.AGENT_INTERVIEW_DECISION_FAILED,
          "Agent 返回了空决策"
      );
    }
    return switch (output.action()) {
      case CALL_TOOL -> new AgentStep.CallTool(output.toolName(), output.arguments());
      case ASK -> new AgentStep.Ask(output.question());
      case FINISH -> new AgentStep.Finish(output.reason());
    };
  }

  private AssessmentResult toAssessment(AssessmentOutput output) {
    if (output == null || output.depth() == null || output.suggestedAction() == null) {
      throw new BusinessException(
          ErrorCode.AGENT_INTERVIEW_DECISION_FAILED,
          "Agent 返回了不完整的回答评估"
      );
    }
    AnswerEvidence evidence = output.finding() == null
        || output.finding().isBlank()
        || output.quote() == null
        || output.quote().isBlank()
        ? null
        : new AnswerEvidence(output.finding().trim(), output.quote());
    return new AssessmentResult(output.depth(), evidence, output.suggestedAction());
  }

  enum Action {
    CALL_TOOL,
    ASK,
    FINISH
  }

  record AgentStepOutput(
      Action action,
      String toolName,
      Map<String, Object> arguments,
      String question,
      String reason
  ) {}

  record AssessmentOutput(
      AnswerDepthLevel depth,
      String finding,
      String quote,
      AssessmentAction suggestedAction
  ) {}
}
