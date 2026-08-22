package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.util.Sha256;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.interview.skill.InterviewSkillService.SkillDTO;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * 加载面试技能工具，允许 Agent 在面试中按需加载 SKILL.md。
 */
@Component
public class LoadSkillTool implements AdaptiveAgentTool {

  public static final String NAME = "load_skill";

  private final InterviewSkillService skillService;
  private final ToolCallback callback;

  public LoadSkillTool(InterviewSkillService skillService) {
    this.skillService = skillService;
    this.callback = ToolCallbacks.gatewayOnly(
        NAME,
        "Load the frozen interviewer persona for one skill ID",
        LoadSkillInput.class
    );
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public ToolCallback callback() {
    return callback;
  }

  @Override
  public ToolResult execute(Map<String, Object> arguments) {
    String skillId = ToolArguments.requiredString(arguments, "skillId", 64);
    SkillDTO skill = skillService.getSkill(skillId);
    if (skill.persona() == null || skill.persona().isBlank()) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Skill does not contain an interviewer persona: " + skillId
      );
    }
    String hash = Sha256.hex(skill.persona());
    SkillPayload payload = new SkillPayload(
        skill.id(),
        skill.name(),
        skill.description(),
        skill.persona(),
        hash
    );
    return new CompletedToolResult(
        "skill:" + skill.id() + ":" + hash,
        payload,
        "skillId=" + skill.id() + ", sha256=" + hash
    );
  }

  record LoadSkillInput(String skillId) {}

  record SkillPayload(
      String skillId,
      String name,
      String description,
      String persona,
      String sha256
  ) {}
}
