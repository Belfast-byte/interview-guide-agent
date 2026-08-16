package interview.guide.modules.interview.agent.runtime;

import java.util.List;

/**
 * Agent 单轮决策上下文，聚合会话快照、已加载技能、当前回答与评估结果。
 */
public record InterviewAgentContext(
    String jd,
    String resume,
    int currentTurn,
    int maxTurns,
    List<SkillDescriptor> availableSkills,
    LoadedSkill loadedSkill,
    List<InterviewTranscriptTurn> recentTurns,
    String currentAnswer,
    AssessmentResult currentAssessment
) {

  public InterviewAgentContext {
    availableSkills = List.copyOf(availableSkills);
    recentTurns = List.copyOf(recentTurns);
  }

  public InterviewAgentContext withLoadedSkill(LoadedSkill skill) {
    return new InterviewAgentContext(
        jd,
        resume,
        currentTurn,
        maxTurns,
        availableSkills,
        skill,
        recentTurns,
        currentAnswer,
        currentAssessment
    );
  }
}
