package interview.guide.modules.interview.agent.tool;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.runtime.LoadedSkill;
import interview.guide.modules.interview.agent.runtime.SkillDescriptor;
import interview.guide.modules.interview.skill.InterviewSkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 基于 Spring AI Agent Utils 的面试技能目录实现，从 classpath 加载 SKILL.md 技能。
 */
@Component
@RequiredArgsConstructor
public class InterviewSkillServiceCatalog implements InterviewSkillCatalog {

  private final InterviewSkillService interviewSkillService;

  @Override
  public List<SkillDescriptor> listDescriptors() {
    return interviewSkillService.getAllSkills().stream()
        .filter(skill -> skill.persona() != null && !skill.persona().isBlank())
        .map(skill -> new SkillDescriptor(skill.id(), skill.name(), skill.description()))
        .toList();
  }

  @Override
  public LoadedSkill load(String skillId) {
    InterviewSkillService.SkillDTO skill = interviewSkillService.getSkill(skillId);
    if (skill.persona() == null || skill.persona().isBlank()) {
      throw new BusinessException(
          ErrorCode.AGENT_INTERVIEW_DECISION_FAILED,
          "Skill 不包含可加载的 SKILL.md body: " + skillId
      );
    }
    return new LoadedSkill(
        skill.id(),
        skill.name(),
        skill.description(),
        skill.persona(),
        sha256(skill.persona())
    );
  }

  private String sha256(String content) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new BusinessException(
          ErrorCode.INTERNAL_ERROR,
          "无法计算 Skill 内容哈希",
          e
      );
    }
  }
}
