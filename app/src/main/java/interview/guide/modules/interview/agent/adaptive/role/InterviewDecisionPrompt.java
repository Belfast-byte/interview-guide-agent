package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.common.ai.PromptLoader;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionModelContext;
import java.util.Map;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 新 Agent Loop 的 Prompt 与结构化输出契约。 */
@Component
class InterviewDecisionPrompt {

  private final ObjectMapper objectMapper;
  private final PromptTemplate systemTemplate;
  private final PromptTemplate userTemplate;
  private final BeanOutputConverter<InterviewDecisionOutput> outputConverter;

  InterviewDecisionPrompt(
      ObjectMapper objectMapper,
      PromptLoader promptLoader,
      AdaptiveAgentProperties properties
  ) {
    this.objectMapper = objectMapper;
    this.systemTemplate = promptLoader.loadTemplate(
        properties.getDecisionSystemPromptPath());
    this.userTemplate = promptLoader.loadTemplate(
        properties.getDecisionUserPromptPath());
    this.outputConverter = new BeanOutputConverter<>(InterviewDecisionOutput.class);
  }

  PreparedPrompt prepare(DecisionModelContext context) {
    return new PreparedPrompt(
        systemTemplate.render() + "\n\n" + outputConverter.getFormat(),
        userTemplate.render(Map.of("contextJson", serialize(context))),
        outputConverter
    );
  }

  private String serialize(DecisionModelContext context) {
    try {
      return objectMapper.writeValueAsString(context);
    } catch (JacksonException e) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AgentContext 序列化失败", e);
    }
  }

  record PreparedPrompt(
      String system,
      String user,
      BeanOutputConverter<InterviewDecisionOutput> converter
  ) {}
}
