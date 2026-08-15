package interview.guide.modules.interview.agent.tool;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.runtime.AgentStep;
import interview.guide.modules.interview.agent.runtime.LoadedSkill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InterviewToolGateway {

  public static final String LOAD_SKILL_TOOL = "load_skill";

  private final InterviewSkillCatalog skillCatalog;

  public ToolResult execute(AgentStep.CallTool call) {
    if (!LOAD_SKILL_TOOL.equals(call.toolName())) {
      throw new BusinessException(
          ErrorCode.AGENT_INTERVIEW_DECISION_FAILED,
          "Agent 请求了未开放的工具: " + call.toolName()
      );
    }

    Object rawSkillId = call.arguments().get("skillId");
    if (!(rawSkillId instanceof String skillId) || skillId.isBlank()) {
      throw new BusinessException(
          ErrorCode.AGENT_INTERVIEW_DECISION_FAILED,
          "load_skill 缺少有效的 skillId"
      );
    }

    LoadedSkill loadedSkill = skillCatalog.load(skillId);
    return new ToolResult(LOAD_SKILL_TOOL, loadedSkill);
  }
}
