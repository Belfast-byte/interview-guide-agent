package interview.guide.modules.interview.agent.tool;

import interview.guide.modules.interview.agent.runtime.LoadedSkill;
import interview.guide.modules.interview.agent.runtime.SkillDescriptor;

import java.util.List;

/**
 * 面试技能目录接口，提供按 ID 查找技能描述的能力。
 */
public interface InterviewSkillCatalog {

  List<SkillDescriptor> listDescriptors();

  LoadedSkill load(String skillId);
}
