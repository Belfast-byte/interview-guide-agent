package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemoryValidator;
import interview.guide.modules.interview.agent.adaptive.tool.AssessmentReadTool;
import interview.guide.modules.interview.agent.adaptive.tool.CodeTaskReadTool;
import interview.guide.modules.interview.agent.adaptive.tool.InterviewMaterialReadTool;
import interview.guide.modules.interview.agent.adaptive.tool.InterviewToolCallback;
import interview.guide.modules.interview.agent.adaptive.tool.MemoryRecallTool;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionSearchTool;
import interview.guide.modules.interview.agent.adaptive.tool.ReferenceSearchTool;
import interview.guide.modules.interview.agent.adaptive.tool.RubricSearchTool;
import java.util.Arrays;
import java.util.List;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 具体面试业务工具集合，不扫描或暴露全局 Spring 工具。 */
@Configuration(proxyBeanMethods = false)
public class AdaptiveAgentRuntimeConfiguration {
  @Bean
  WorkingMemoryValidator workingMemoryValidator() { return new WorkingMemoryValidator(); }

  @Bean
  QueryTools interviewQueryTools(InterviewMaterialReadTool material, CodeTaskReadTool code,
      AssessmentReadTool assessment, MemoryRecallTool memory, QuestionSearchTool questions,
      RubricSearchTool rubrics, ReferenceSearchTool references) {
    var callbacks = Arrays.stream(ToolCallbacks.from(material, code, assessment, memory, questions, rubrics, references))
        .map(callback -> new InterviewToolCallback(callback, true)).map(ToolCallback.class::cast).toList();
    return new QueryTools(callbacks);
  }

  /** 具体面试工具集合；不能暴露为通用 provider Bean，MCP 会自动发布此类 Bean。 */
  public record QueryTools(List<ToolCallback> callbacks) {
    public QueryTools { callbacks = List.copyOf(callbacks); }
  }

  @Bean
  InterviewAgentLoop interviewAgentLoop(InterviewDecisionModel model, AgentDecisionValidator validator,
      QueryTools queryTools,
      DeadlineExecutor deadlineExecutor, AdaptiveAgentProperties properties) {
    return new InterviewAgentLoop(model, validator, () -> queryTools.callbacks().toArray(ToolCallback[]::new), deadlineExecutor, properties);
  }
}
