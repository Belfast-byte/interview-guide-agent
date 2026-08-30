package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import java.util.List;

/** 模型对本轮最终动作及完整 WorkingMemory 的提案。 */
public record AgentDecision(
    WorkingMemory workingMemory,
    Action action
) {

  public sealed interface Action permits Ask, CallReadTools, Finish {}

  public record Ask(
      String targetId,
      Long sourceGapId,
      QuestionDraft question
  ) implements Action {}
  public record CallReadTools(List<ReadToolCall> calls) implements Action {

    public CallReadTools {
      if (calls != null) {
        calls = List.copyOf(calls);
      }
    }
  }


  public record QuestionDraft(
      String content,
      String decisionSummary,
      List<String> adoptedSourceRefs
  ) {

    public QuestionDraft {
      if (adoptedSourceRefs != null) {
        adoptedSourceRefs = List.copyOf(adoptedSourceRefs);
      }
    }
  }

  public record Finish(String decisionSummary) implements Action {}
}
