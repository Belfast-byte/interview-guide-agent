package interview.guide.modules.interview.agent.tool;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.runtime.AgentStep;
import interview.guide.modules.interview.agent.runtime.LoadedSkill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewToolGatewayTest {

  @Mock
  private InterviewSkillCatalog skillCatalog;

  @Test
  @DisplayName("load_skill 只按模型给出的 skillId 加载完整内容")
  void shouldLoadSkillSelectedByModel() {
    LoadedSkill skill = new LoadedSkill(
        "system-design",
        "系统设计",
        "系统设计面试",
        "完整内容",
        "hash"
    );
    when(skillCatalog.load("system-design")).thenReturn(skill);
    InterviewToolGateway gateway = new InterviewToolGateway(skillCatalog);

    ToolResult result = gateway.execute(new AgentStep.CallTool(
        "load_skill",
        Map.of("skillId", "system-design")
    ));

    assertThat(result.loadedSkill()).isEqualTo(skill);
  }

  @Test
  @DisplayName("拒绝 load_skill 之外的任何工具")
  void shouldRejectUnknownTool() {
    InterviewToolGateway gateway = new InterviewToolGateway(skillCatalog);

    assertThatThrownBy(() -> gateway.execute(new AgentStep.CallTool(
        "search_knowledge_base",
        Map.of()
    ))).isInstanceOf(BusinessException.class);
  }
}
