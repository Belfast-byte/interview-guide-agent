package interview.guide.modules.interview.agent.tool;

import interview.guide.modules.interview.agent.runtime.LoadedSkill;
import interview.guide.modules.interview.agent.runtime.SkillDescriptor;

import java.util.List;

public interface InterviewSkillCatalog {

  List<SkillDescriptor> listDescriptors();

  LoadedSkill load(String skillId);
}
