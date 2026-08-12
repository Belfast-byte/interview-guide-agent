package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.interview.skill.InterviewSkillService.SkillDTO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

@Component
public class LoadSkillTool implements AdaptiveAgentTool {

  public static final String NAME = "load_skill";

  private final InterviewSkillService skillService;
  private final ToolCallback callback;

  public LoadSkillTool(InterviewSkillService skillService) {
    this.skillService = skillService;
    this.callback = FunctionToolCallback
        .builder(NAME, (LoadSkillInput input) -> unsupportedDirectCall())
        .description("Load the frozen interviewer persona for one skill ID")
        .inputType(LoadSkillInput.class)
        .build();
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
    String hash = sha256(skill.persona());
    SkillPayload payload = new SkillPayload(
        skill.id(),
        skill.name(),
        skill.description(),
        skill.persona(),
        hash
    );
    return new ToolResult(
        "skill:" + skill.id() + ":" + hash,
        payload,
        "skillId=" + skill.id() + ", sha256=" + hash
    );
  }

  private String unsupportedDirectCall() {
    throw new IllegalStateException("Tool execution must go through ToolGateway");
  }

  private String sha256(String content) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
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
