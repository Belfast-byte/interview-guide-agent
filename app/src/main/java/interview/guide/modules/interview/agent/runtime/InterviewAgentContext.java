package interview.guide.modules.interview.agent.runtime;

import java.util.List;

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
