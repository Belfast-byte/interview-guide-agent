package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import java.util.List;

/** Spring AI 结构化输出 DTO；非法组合保留给 AgentDecisionValidator 回流模型。 */
record InterviewDecisionOutput(
    WorkingMemory workingMemory,
    ActionOutput action
) {

  AgentDecision toDomain() {
    return new AgentDecision(workingMemory, action == null ? null : action.toDomain());
  }

  record ActionOutput(String type, AskOutput ask, FinishOutput finish) {

    AgentDecision.Action toDomain() {
      if ("ASK".equals(type)) {
        return ask == null ? null : ask.toDomain();
      }
      if ("FINISH".equals(type)) {
        return finish == null ? null : new AgentDecision.Finish(finish.decisionSummary());
      }
      return null;
    }
  }

  record AskOutput(String targetId, Long sourceGapId, QuestionOutput question) {

    AgentDecision.Ask toDomain() {
      return new AgentDecision.Ask(
          targetId,
          sourceGapId,
          question == null ? null : question.toDomain()
      );
    }
  }

  record QuestionOutput(
      String content,
      String decisionSummary,
      List<String> adoptedSourceRefs
  ) {

    AgentDecision.QuestionDraft toDomain() {
      return new AgentDecision.QuestionDraft(content, decisionSummary, adoptedSourceRefs);
    }
  }

  record FinishOutput(String decisionSummary) {}
}
