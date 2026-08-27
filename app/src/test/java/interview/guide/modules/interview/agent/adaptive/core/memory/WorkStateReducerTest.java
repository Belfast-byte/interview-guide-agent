package interview.guide.modules.interview.agent.adaptive.core.memory;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkStateReducerTest {

  @Test
  @DisplayName("评估 Patch 原子更新回答、深度、证据、问题和焦点")
  void shouldApplyAssessmentPatchAtomically() {
    InterviewWorkState state = initialState();
    WorkIssue issue = issue("issue-1", state.activeTargetId());
    WorkEvidenceRef evidence = new WorkEvidenceRef(
        state.activeTargetId(), "ASSESSMENT", "assessment-1", "使用了 RDB"
    );
    WorkStatePatch patch = patch(state, "assessment-1", List.of(
        new WorkStateOperation.CompleteAnswer(1),
        new WorkStateOperation.UpdateTargetDepth(state.activeTargetId(), DepthLevel.L1),
        new WorkStateOperation.AddEvidenceRef(evidence),
        new WorkStateOperation.OpenIssue(issue),
        new WorkStateOperation.SetFocus("RDB 与 AOF 的取舍")
    ));

    InterviewWorkState updated = WorkStateReducer.apply(state, patch);

    assertThat(updated.revision()).isEqualTo(2);
    assertThat(updated.phase()).isEqualTo(WorkPhase.READY_TO_DECIDE);
    assertThat(updated.activeTarget().currentDepth()).isEqualTo(DepthLevel.L1);
    assertThat(updated.activeEvidenceRefs()).containsExactly(evidence);
    assertThat(updated.activeOpenIssues()).containsExactly(issue);
    assertThat(updated.attentionFocus()).isEqualTo("RDB 与 AOF 的取舍");
  }

  @Test
  @DisplayName("任一 operation 失败时原 WorkState 保持不变")
  void shouldLeaveOriginalStateUntouchedWhenPatchFails() {
    InterviewWorkState state = readyState();
    WorkStatePatch patch = patch(state, "invalid-budget", List.of(
        new WorkStateOperation.SetFocus("已修改焦点"),
        new WorkStateOperation.ConsumeBudget(state.activeTargetId(), WorkBudgetType.TOOL)
    ));

    assertThatThrownBy(() -> WorkStateReducer.apply(state, patch))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("预算已用尽");
    assertThat(state.attentionFocus()).isEqualTo("缓存机制");
    assertThat(state.revision()).isEqualTo(2);
  }

  @Test
  @DisplayName("切换耗尽目标时放弃其未闭环问题并激活下一目标")
  void shouldAbandonIssuesAndSwitchTarget() {
    InterviewWorkState state = readyStateWithIssue();
    String nextTargetId = state.nextPendingTargetId();
    WorkStatePatch patch = patch(state, "switch", List.of(
        new WorkStateOperation.SwitchTarget(nextTargetId, TargetWorkStatus.EXHAUSTED)
    ));

    InterviewWorkState updated = WorkStateReducer.apply(state, patch);

    assertThat(updated.activeTargetId()).isEqualTo(nextTargetId);
    assertThat(updated.targets()).extracting(TargetWorkState::status)
        .containsExactly(TargetWorkStatus.EXHAUSTED, TargetWorkStatus.ACTIVE);
    assertThat(updated.openIssues().getFirst().status()).isEqualTo(WorkIssueStatus.ABANDONED);
    assertThat(updated.activeEvidenceRefs()).isEmpty();
  }

  @Test
  @DisplayName("Pending Action、动作结果与结束使用明确阶段迁移")
  void shouldApplyExecutionPhaseTransitions() {
    InterviewWorkState ready = readyState();
    InterviewWorkState pending = WorkStateReducer.apply(ready, patch(
        ready, "pending", List.of(new WorkStateOperation.SetPendingAction("intent-1"))
    ));
    InterviewWorkState awaiting = WorkStateReducer.apply(pending, patch(
        pending, "action", List.of(new WorkStateOperation.ApplyActionResult(2, null))
    ));

    assertThat(pending.phase()).isEqualTo(WorkPhase.ACTION_PENDING);
    assertThat(pending.activeActionIntentId()).isEqualTo("intent-1");
    assertThat(awaiting.phase()).isEqualTo(WorkPhase.AWAITING_ANSWER);
    assertThat(awaiting.awaitingAnswerTurnIndex()).isEqualTo(2);

    InterviewWorkState readyAgain = WorkStateReducer.apply(awaiting, patch(
        awaiting, "answer-2", List.of(new WorkStateOperation.CompleteAnswer(2))
    ));
    InterviewWorkState finished = WorkStateReducer.apply(readyAgain, patch(
        readyAgain, "finish", List.of(new WorkStateOperation.FinishSession(
            TargetWorkStatus.EXHAUSTED
        ))
    ));
    assertThat(finished.phase()).isEqualTo(WorkPhase.FINISHED);
  }

  @Test
  @DisplayName("revision 冲突直接失败")
  void shouldRejectRevisionConflict() {
    InterviewWorkState state = initialState();
    WorkStatePatch stale = new WorkStatePatch(
        "patch-stale",
        state.sessionId(),
        0,
        1,
        WorkStatePatchSource.ASSESSMENT,
        "assessment-stale",
        List.of(new WorkStateOperation.CompleteAnswer(1))
    );

    assertThatThrownBy(() -> WorkStateReducer.apply(state, stale))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("revision 冲突");
  }

  private InterviewWorkState initialState() {
    return testPlan(
        "session-1",
        new PlanProposal(List.of(dimension("缓存机制"), dimension("并发控制")))
    ).initialWorkState();
  }

  private InterviewWorkState readyState() {
    InterviewWorkState state = initialState();
    return WorkStateReducer.apply(state, patch(
        state, "answer-1", List.of(new WorkStateOperation.CompleteAnswer(1))
    ));
  }

  private InterviewWorkState readyStateWithIssue() {
    InterviewWorkState state = readyState();
    return WorkStateReducer.apply(state, patch(
        state, "issue", List.of(new WorkStateOperation.OpenIssue(
            issue("issue-1", state.activeTargetId())
        ))
    ));
  }

  private WorkIssue issue(String issueId, String targetId) {
    return new WorkIssue(
        issueId,
        targetId,
        CapabilityTarget.EvidenceMethod.CANDIDATE_ANSWER,
        "RDB",
        "缺少恢复边界",
        WorkIssueStatus.OPEN,
        null
    );
  }

  private WorkStatePatch patch(
      InterviewWorkState state,
      String sourceId,
      List<WorkStateOperation> operations
  ) {
    return new WorkStatePatch(
        "patch-" + sourceId,
        state.sessionId(),
        state.revision(),
        state.revision() + 1,
        WorkStatePatchSource.ASSESSMENT,
        sourceId,
        operations
    );
  }

  private DimensionProposal dimension(String focus) {
    String focusId = focus.equals("缓存机制") ? "REDIS" : "CONCURRENCY";
    return new DimensionProposal(
        focus,
        focus,
        focusId,
        2,
        new ArrayList<>(),
        "java-backend"
    );
  }
}
