package interview.guide.modules.interview.agent.adaptive.core.context;

import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 面试官上下文，包含当前维度、历史、记忆和可用工具，用于生成下一轮问题。
 */
public record InterviewerContext(
    String jd,
    String resume,
    int currentTurn,
    int maxTurns,
    int targetDimensionOrder,
    String targetDimension,
    String targetFocus,
    List<String> suggestedTools,
    String suggestedSkill,
    List<AdaptiveInterviewTurn> currentDimensionTurns,
    CandidateAnswer currentDimensionAnswer,
    InterviewerWorkView working,
    List<QuestionRecallHint> recalledQuestions,
    ToolResultEvent currentToolResult,
    CandidateAnswer currentCodeSubmission,
    ProjectInterviewContext project,
    PracticeCoachingContext practiceMemory
) {

  public InterviewerContext {
    suggestedTools = List.copyOf(suggestedTools);
    currentDimensionTurns = List.copyOf(currentDimensionTurns);
    recalledQuestions = List.copyOf(recalledQuestions);
    Objects.requireNonNull(working, "working 不能为空");
  }

  public Map<String, Object> modelView() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("jd", jd);
    values.put("resume", resume);
    values.put("currentTurn", currentTurn);
    values.put("maxTurns", maxTurns);
    values.put("targetDimensionOrder", targetDimensionOrder);
    values.put("targetDimension", targetDimension);
    values.put("targetFocus", targetFocus);
    values.put("suggestedTools", suggestedTools);
    put(values, "suggestedSkill", suggestedSkill);
    values.put("currentDimensionTurns", currentDimensionTurns);
    put(values, "currentDimensionAnswer", currentDimensionAnswer);
    values.put("working", working);
    values.put("recalledQuestions", recalledQuestions);
    put(values, "currentToolResult", currentToolResult);
    put(values, "currentCodeSubmission", currentCodeSubmission);
    put(values, "project", project);
    put(values, "practiceMemory", practiceMemory);
    return Map.copyOf(values);
  }

  private void put(Map<String, Object> values, String key, Object value) {
    if (value != null) {
      values.put(key, value);
    }
  }

}
