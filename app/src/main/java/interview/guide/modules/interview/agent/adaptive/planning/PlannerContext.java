package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import java.util.List;

/** 规划器只消费本次会话输入，不读取候选人的历史记忆。 */
public record PlannerContext(
    String jd,
    String resume,
    SessionMode mode,
    CandidateLevel candidateLevel,
    List<TopicKey> practiceScope,
    List<PlanningSkill> skillCatalog
) {

  public PlannerContext {
    practiceScope = List.copyOf(practiceScope);
    skillCatalog = List.copyOf(skillCatalog);
  }
}
