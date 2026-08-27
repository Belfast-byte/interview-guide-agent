package interview.guide.modules.interview.agent.adaptive.application;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;
import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.NextActionType;
import interview.guide.modules.interview.agent.adaptive.core.memory.TargetWorkStatus;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkIssueStatus;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStateReducer;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatchSource;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssessmentWorkStatePlannerTest {

  @Test
  @DisplayName("达到目标深度后由代码切换目标并生成下一题 Patch")
  void shouldSwitchCompletedTargetBeforeAsking() {
    InterviewWorkState state = state(2);

    var prepared = AssessmentWorkStatePlanner.prepare(
        state, assessment(1, DepthLevel.L2, List.of()), List.of());
    InterviewWorkState applied = apply(state, prepared.finalPatches(2));

    assertThat(prepared.action().type()).isEqualTo(NextActionType.ASK);
    assertThat(applied.targets()).extracting(target -> target.status())
        .containsExactly(TargetWorkStatus.COMPLETED, TargetWorkStatus.ACTIVE);
    assertThat(applied.awaitingAnswerTurnIndex()).isEqualTo(2);
    assertThat(prepared.finalPatches(2)).extracting(WorkStatePatch::sourceType)
        .containsExactly(
            WorkStatePatchSource.ASSESSMENT,
            WorkStatePatchSource.POLICY,
            WorkStatePatchSource.POLICY,
            WorkStatePatchSource.ACTION_RESULT
        );
  }

  @Test
  @DisplayName("追问回答后关闭旧 issue，预算耗尽时放弃本轮新 issue")
  void shouldCloseAnsweredIssueAndKeepCurrentGaps() {
    InterviewWorkState initial = state(1);
    var first = AssessmentWorkStatePlanner.prepare(
        initial,
        assessment(1, DepthLevel.L1, List.of(new ProbeGap("AOF", "缺少刷盘策略"))),
        List.of()
    );
    InterviewWorkState awaiting = apply(initial, first.finalPatches(2));

    var second = AssessmentWorkStatePlanner.prepare(
        awaiting,
        assessment(2, DepthLevel.L1, List.of(new ProbeGap("RDB", "缺少写时复制"))),
        List.of()
    );
    InterviewWorkState projected = second.projectedState();

    assertThat(projected.openIssues()).extracting(issue -> issue.status())
        .containsExactly(WorkIssueStatus.RESOLVED, WorkIssueStatus.ABANDONED);
    assertThat(second.action().type()).isEqualTo(NextActionType.FINISH);
  }

  private InterviewWorkState state(int dimensions) {
    List<DimensionProposal> proposals = java.util.stream.IntStream.range(0, dimensions)
        .mapToObj(index -> new DimensionProposal(
            "维度-" + index,
            "重点-" + index,
            "FOCUS_" + index,
            2,
            List.of(),
            "java-backend"
        ))
        .toList();
    return testPlan("session-1", new PlanProposal(proposals)).initialWorkState();
  }

  private AssessmentDecision assessment(
      int turnIndex,
      DepthLevel depth,
      List<ProbeGap> gaps
  ) {
    return new AssessmentDecision(
        "session-1", turnIndex, depth, 0.8, "评估", List.of(), gaps);
  }

  private InterviewWorkState apply(
      InterviewWorkState state,
      List<WorkStatePatch> patches
  ) {
    InterviewWorkState updated = state;
    for (var patch : patches) {
      updated = WorkStateReducer.apply(updated, patch);
    }
    return updated;
  }
}
