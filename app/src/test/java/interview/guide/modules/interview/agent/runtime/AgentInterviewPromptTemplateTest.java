package interview.guide.modules.interview.agent.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentInterviewPromptTemplateTest {

  @Test
  @DisplayName("系统 Prompt 可由 StringTemplate 渲染结构化输出格式")
  void shouldRenderSystemPromptWithStructuredOutputFormat() throws IOException {
    ClassPathResource resource = new ClassPathResource(
        "prompts/agent-interview-loop-system.st"
    );
    PromptTemplate template = new PromptTemplate(
        resource.getContentAsString(StandardCharsets.UTF_8)
    );

    String rendered = template.render(Map.of(
        "format", "{\"action\":\"CALL_TOOL\"}"
    ));

    assertThat(rendered)
        .contains("`load_skill`")
        .contains("`skillId`")
        .contains("{\"action\":\"CALL_TOOL\"}");
  }

  @Test
  @DisplayName("评估 Prompt 明确量规、引用边界与历史评级隔离")
  void shouldRenderAssessmentPromptWithFairnessBoundary() throws IOException {
    ClassPathResource resource = new ClassPathResource(
        "prompts/agent-interview-assessment-system.st"
    );
    PromptTemplate template = new PromptTemplate(
        resource.getContentAsString(StandardCharsets.UTF_8)
    );

    String rendered = template.render(Map.of(
        "format", "{\"depth\":\"L2\"}"
    ));

    assertThat(rendered)
        .contains("L0")
        .contains("L4")
        .contains("不能参考任何历史评级")
        .contains("必须逐字复制自 `currentAnswer`")
        .contains("{\"depth\":\"L2\"}");
  }
}
