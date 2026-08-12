package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.interview.skill.InterviewSkillService.SkillDTO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoadSkillToolTest {

  @Test
  @DisplayName("Skill 内容以 SHA-256 冻结并形成稳定结果 ID")
  void shouldFreezeSkillContentWithSha256() {
    InterviewSkillService skillService = mock(InterviewSkillService.class);
    when(skillService.getSkill("java-backend")).thenReturn(new SkillDTO(
        "java-backend",
        "Java 后端",
        "面试 Java 后端能力",
        List.of(),
        true,
        null,
        "只问可验证的工程问题。",
        null
    ));
    LoadSkillTool tool = new LoadSkillTool(skillService);

    ToolResult first = tool.execute(Map.of("skillId", "java-backend"));
    ToolResult second = tool.execute(Map.of("skillId", "java-backend"));

    assertThat(first.resultId()).isEqualTo(second.resultId());
    assertThat(first.resultId()).startsWith("skill:java-backend:");
    assertThat(first.summary()).contains("sha256=");
    assertThat(tool.callback().getToolDefinition().name()).isEqualTo("load_skill");
    assertThat(tool.callback().getToolDefinition().inputSchema()).contains("skillId");
    assertThatThrownBy(() -> tool.callback().call("{\"skillId\":\"java-backend\"}"))
        .hasMessageContaining("Tool execution must go through ToolGateway");
  }
}
