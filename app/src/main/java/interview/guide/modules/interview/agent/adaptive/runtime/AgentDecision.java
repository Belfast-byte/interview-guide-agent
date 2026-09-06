package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

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
        calls = calls == null ? null : Collections.unmodifiableList(new ArrayList<>(calls));
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
        adoptedSourceRefs = adoptedSourceRefs == null ? null : Collections.unmodifiableList(new ArrayList<>(adoptedSourceRefs));
      }
    }
  }

  public record Finish(String decisionSummary) implements Action {}
}
