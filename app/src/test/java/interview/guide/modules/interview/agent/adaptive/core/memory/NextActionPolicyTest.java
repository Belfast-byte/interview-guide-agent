package interview.guide.modules.interview.agent.adaptive.core.memory;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;
import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NextActionPolicyTest {

  @Test
  @DisplayName("等待回答和待执行 Intent 优先于所有内容判断")
  void shouldRespectExecutionPhaseFirst() {
    InterviewWorkState awaiting = initialState();
    assertThat(NextActionPolicy.decide(awaiting).type()).isEqualTo(NextActionType.WAIT);

    InterviewWorkState ready = apply(awaiting, new WorkStateOperation.CompleteAnswer(1));
    InterviewWorkState pending = apply(ready, new WorkStateOperation.SetPendingAction("intent-1"));
    assertThat(NextActionPolicy.decide(pending).type())
        .isEqualTo(NextActionType.RESUME_INTENT);
  }

  @Test
  @DisplayName("达到期望深度且无未闭环问题时切换目标")
  void shouldSwitchCompletedTarget() {
    InterviewWorkState ready = readyState(List.of());
    InterviewWorkState reached = apply(
        ready,
        new WorkStateOperation.UpdateTargetDepth(ready.activeTargetId(), DepthLevel.L2)
    );

    NextAction action = NextActionPolicy.decide(reached);

    assertThat(action.type()).isEqualTo(NextActionType.SWITCH_TARGET);
    assertThat(action.terminalStatus()).isEqualTo(TargetWorkStatus.COMPLETED);
    assertThat(action.nextTargetId()).isEqualTo("target-1");
  }

  @Test
  @DisplayName("追问预算耗尽时按 EXHAUSTED 切换")
  void shouldExhaustWhenFollowUpBudgetRunsOut() {
    InterviewWorkState ready = readyState(List.of());
    InterviewWorkState exhausted = apply(ready, List.of(
        new WorkStateOperation.ConsumeBudget(ready.activeTargetId(), WorkBudgetType.FOLLOW_UP),
        new WorkStateOperation.ConsumeBudget(ready.activeTargetId(), WorkBudgetType.FOLLOW_UP)
    ));

    NextAction action = NextActionPolicy.decide(exhausted);

    assertThat(action.type()).isEqualTo(NextActionType.SWITCH_TARGET);
    assertThat(action.terminalStatus()).isEqualTo(TargetWorkStatus.EXHAUSTED);
  }

  @Test
  @DisplayName("回答问题优先于工具事实问题")
  void shouldPrioritizeCandidateAnswerIssue() {
    WorkIssue answerIssue = issue(
        "answer", CapabilityTarget.EvidenceMethod.CANDIDATE_ANSWER
    );
    WorkIssue toolIssue = issue("tool", CapabilityTarget.EvidenceMethod.TOOL_FACT);
    InterviewWorkState ready = readyState(List.of(answerIssue, toolIssue));

    NextAction action = NextActionPolicy.decide(ready);

    assertThat(action.type()).isEqualTo(NextActionType.ASK);
    assertThat(action.issueId()).isEqualTo("answer");
  }

  @Test
  @DisplayName("只有工具问题时选择 CALL_TOOL")
  void shouldCallToolForToolFactIssue() {
    InterviewWorkState ready = readyState(List.of(
        issue("tool", CapabilityTarget.EvidenceMethod.TOOL_FACT)
    ));

    assertThat(NextActionPolicy.decide(ready).type()).isEqualTo(NextActionType.CALL_TOOL);
  }

  @Test
  @DisplayName("无问题但仍有轮次预算时继续主问题，所有目标终态后结束")
  void shouldAskMainQuestionAndFinishAfterLastTarget() {
    InterviewWorkState ready = readyState(List.of());
    assertThat(NextActionPolicy.decide(ready).type()).isEqualTo(NextActionType.ASK);

    InterviewWorkState single = readySingleTarget();
    InterviewWorkState reached = apply(
        single,
        new WorkStateOperation.UpdateTargetDepth(single.activeTargetId(), DepthLevel.L2)
    );
    assertThat(NextActionPolicy.decide(reached).type()).isEqualTo(NextActionType.FINISH);
  }

  private InterviewWorkState readyState(List<WorkIssue> issues) {
    InterviewWorkState ready = apply(
        initialState(),
        new WorkStateOperation.CompleteAnswer(1)
    );
    return applyIssues(ready, issues);
  }

  private InterviewWorkState readySingleTarget() {
    InterviewWorkState state = testPlan(
        "session-single",
        new PlanProposal(List.of(dimension("缓存机制", "REDIS")))
    ).initialWorkState();
    return apply(state, new WorkStateOperation.CompleteAnswer(1));
  }

  private InterviewWorkState initialState() {
    return testPlan(
        "session-1",
        new PlanProposal(List.of(
            dimension("缓存机制", "REDIS"),
            dimension("并发控制", "CONCURRENCY")
        ))
    ).initialWorkState();
  }

  private InterviewWorkState applyIssues(
      InterviewWorkState state,
      List<WorkIssue> issues
  ) {
    if (issues.isEmpty()) {
      return state;
    }
    return apply(state, issues.stream()
        .map(WorkStateOperation.OpenIssue::new)
        .map(WorkStateOperation.class::cast)
        .toList());
  }

  private InterviewWorkState apply(
      InterviewWorkState state,
      WorkStateOperation operation
  ) {
    return apply(state, List.of(operation));
  }

  private InterviewWorkState apply(
      InterviewWorkState state,
      List<WorkStateOperation> operations
  ) {
    WorkStatePatch patch = new WorkStatePatch(
        "patch-" + state.revision(),
        state.sessionId(),
        state.revision(),
        state.revision() + 1,
        WorkStatePatchSource.ASSESSMENT,
        "source-" + state.revision(),
        operations
    );
    return WorkStateReducer.apply(state, patch);
  }

  private WorkIssue issue(
      String issueId,
      CapabilityTarget.EvidenceMethod evidenceMethod
  ) {
    return new WorkIssue(
        issueId,
        "target-0",
        evidenceMethod,
        "anchor",
        "missing",
        WorkIssueStatus.OPEN,
        null
    );
  }

  private DimensionProposal dimension(String name, String focusId) {
    return new DimensionProposal(
        name,
        name,
        focusId,
        2,
        List.of("rubric_lookup"),
        "java-backend"
    );
  }
}
