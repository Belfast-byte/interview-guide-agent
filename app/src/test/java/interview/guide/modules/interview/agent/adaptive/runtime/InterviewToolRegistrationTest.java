package interview.guide.modules.interview.agent.adaptive.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.tool.AssessmentReadTool;
import interview.guide.modules.interview.agent.adaptive.tool.CodeTaskReadTool;
import interview.guide.modules.interview.agent.adaptive.tool.InterviewMaterialReadTool;
import interview.guide.modules.interview.agent.adaptive.tool.MemoryRecallTool;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionSearchTool;
import interview.guide.modules.interview.agent.adaptive.tool.ReferenceSearchTool;
import interview.guide.modules.interview.agent.adaptive.tool.RubricSearchTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class InterviewToolRegistrationTest {
  @Test
  void internalQueriesAreNotPublishedAsGlobalMcpCallbackBeans() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(AdaptiveAgentRuntimeConfiguration.class);
      context.registerBean(InterviewMaterialReadTool.class, () -> mock(InterviewMaterialReadTool.class));
      context.registerBean(CodeTaskReadTool.class, () -> mock(CodeTaskReadTool.class));
      context.registerBean(AssessmentReadTool.class, () -> mock(AssessmentReadTool.class));
      context.registerBean(MemoryRecallTool.class, () -> mock(MemoryRecallTool.class));
      context.registerBean(QuestionSearchTool.class, () -> mock(QuestionSearchTool.class));
      context.registerBean(RubricSearchTool.class, () -> mock(RubricSearchTool.class));
      context.registerBean(ReferenceSearchTool.class, () -> mock(ReferenceSearchTool.class));
      context.registerBean(InterviewDecisionModel.class, () -> mock(InterviewDecisionModel.class));
      context.registerBean(AgentDecisionValidator.class, () -> mock(AgentDecisionValidator.class));
      context.registerBean(DeadlineExecutor.class, DeadlineExecutor::new);
      context.registerBean(AdaptiveAgentProperties.class, AdaptiveAgentProperties::new);
      context.refresh();
      assertThat(context.getBean(AdaptiveAgentRuntimeConfiguration.QueryTools.class).callbacks())
          .extracting(tool -> tool.getToolDefinition().name()).containsExactlyInAnyOrder(
              "interview_material_read", "code_task_read", "assessment_read", "memory_recall",
              "question_search", "rubric_search", "reference_search");
      assertThat(context.getBeansOfType(ToolCallbackProvider.class)).isEmpty();
      assertThat(context.getBeansOfType(ToolCallback.class)).isEmpty();
    }
  }
}
