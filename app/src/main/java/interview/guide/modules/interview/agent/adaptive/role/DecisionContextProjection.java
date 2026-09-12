package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionModelContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 出题模型的历史索引：当前关联任务和最近提交直接装配，旧源码按工具读取。 */
final class DecisionContextProjection {
  private DecisionContextProjection() {}

  static Object project(DecisionModelContext input) {
    var context = input.agentContext();
    var turns = context.facts().recentTurns();
    var current = turns.stream().max(java.util.Comparator.comparingInt(
        AdaptiveInterviewTurn::turnIndex)).orElse(null);
    Integer rootIndex = current == null ? null : current.codeTaskTurnIndex();
    var latestCode = turns.stream().filter(turn -> rootIndex != null
        && Objects.equals(rootIndex, turn.codeTaskTurnIndex()) && turn.submittedCode() != null)
        .max(java.util.Comparator.comparingInt(AdaptiveInterviewTurn::turnIndex)).orElse(null);
    var facts = new LinkedHashMap<String, Object>();
    facts.put("coverage", context.facts().coverage());
    facts.put("fixedSkills", context.facts().fixedSkills());
    facts.put("allowedReadTools", context.facts().allowedReadTools());
    facts.put("recentTurns", turns.stream().map(turn -> turn(turn, new Window(rootIndex, latestCode, current))).toList());
    return Map.of("agentContext", Map.of("session", context.session(), "facts", facts,
        "workingMemory", context.workingMemory()), "observations", input.observations());
  }

  private static Map<String, Object> turn(AdaptiveInterviewTurn turn, Window window) {
    var data = new LinkedHashMap<String, Object>();
    data.put("turnIndex", turn.turnIndex());
    data.put("dimensionOrder", turn.dimensionOrder());
    data.put("question", turn.question());
    data.put("questionReason", turn.questionReason());
    data.put("responseType", turn.responseType());
    data.put("responseContent", turn.responseContent());
    data.put("decisionReason", turn.decisionReason());
    data.put("answer", turn.answer());
    data.put("questionType", turn.questionType());
    data.put("codeTaskTurnIndex", turn.codeTaskTurnIndex());
    data.put("originalCodeTask", turn.codeTask() != null
        && Objects.equals(turn.codeTaskTurnIndex(), turn.turnIndex()));
    data.put("answerStatus", turn.answerStatus());
    data.put("answerError", turn.answerError());
    data.put("provenance", turn.provenance());
    data.put("adoptedRubrics", turn.adoptedRubrics());
    if (window.rootIndex() != null && turn.turnIndex() == window.rootIndex()) data.put("codeTask", turn.codeTask());
    if (turn == window.latestCode() || turn == window.current()) {
      data.put("submittedCode", turn.submittedCode());
      data.put("assessmentFeedback", turn.assessmentFeedback());
    }
    return data;
  }
  private record Window(Integer rootIndex, AdaptiveInterviewTurn latestCode, AdaptiveInterviewTurn current) {}
}
