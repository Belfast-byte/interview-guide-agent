package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.NextAction;
import interview.guide.modules.interview.agent.adaptive.core.memory.NextActionPolicy;
import interview.guide.modules.interview.agent.adaptive.core.memory.NextActionType;
import interview.guide.modules.interview.agent.adaptive.core.memory.TargetWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkBudgetType;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkEvidenceRef;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkIssue;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkIssueStatus;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStateOperation;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatchSource;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStateReducer;
import interview.guide.modules.interview.agent.adaptive.core.session.NextTurnProvenanceDraft;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 将 Assessor 提案映射为 Typed Patch，并执行本地策略动作。 */
final class AssessmentWorkStatePlanner {

  private AssessmentWorkStatePlanner() {}

  static PreparedWorkDecision prepare(
      InterviewWorkState state,
      AssessmentDecision assessment,
      List<ValidatedAssessmentEvidence> evidences
  ) {
    List<WorkStatePatch> patches = new ArrayList<>();
    WorkStatePatch assessmentPatch = patch(
        state,
        WorkStatePatchSource.ASSESSMENT,
        "turn:" + assessment.turnIndex(),
        assessmentOperations(state, assessment, evidences)
    );
    patches.add(assessmentPatch);
    InterviewWorkState assessed = WorkStateReducer.apply(state, assessmentPatch);
    PolicyProjection policy = applyPolicy(assessed, assessment.turnIndex());
    patches.addAll(policy.patches());
    return new PreparedWorkDecision(
        state.sessionId(),
        assessment.turnIndex(),
        policy.state(),
        policy.action(),
        List.copyOf(patches),
        provenance(policy.action(), assessment)
    );
  }

  private static PolicyProjection applyPolicy(InterviewWorkState assessed, int turnIndex) {
    List<WorkStatePatch> patches = new ArrayList<>();
    InterviewWorkState projected = assessed;
    NextAction action = NextActionPolicy.decide(projected);
    int policyStep = 1;
    while (action.type() == NextActionType.SWITCH_TARGET) {
      WorkStatePatch policyPatch = patch(
          projected,
          WorkStatePatchSource.POLICY,
          policySource(turnIndex, policyStep++),
          List.of(new WorkStateOperation.SwitchTarget(
              action.nextTargetId(), action.terminalStatus()))
      );
      patches.add(policyPatch);
      projected = WorkStateReducer.apply(projected, policyPatch);
      action = NextActionPolicy.decide(projected);
    }
    WorkStatePatch finalPolicy = patch(
        projected,
        WorkStatePatchSource.POLICY,
        policySource(turnIndex, policyStep),
        policyOperations(projected.activeTarget(), action)
    );
    patches.add(finalPolicy);
    return new PolicyProjection(
        WorkStateReducer.apply(projected, finalPolicy), action, List.copyOf(patches));
  }

  private static List<WorkStateOperation> policyOperations(
      TargetWorkState target,
      NextAction action
  ) {
    return action.type() == NextActionType.FINISH
        ? List.of(new WorkStateOperation.FinishSession(action.terminalStatus()))
        : actionBudget(target, action);
  }

  private static List<WorkStateOperation> assessmentOperations(
      InterviewWorkState state,
      AssessmentDecision assessment,
      List<ValidatedAssessmentEvidence> evidences
  ) {
    List<WorkStateOperation> operations = new ArrayList<>();
    operations.add(new WorkStateOperation.CompleteAnswer(assessment.turnIndex()));
    if (state.awaitingIssueId() != null) {
      operations.add(new WorkStateOperation.CloseIssue(
          state.awaitingIssueId(), WorkIssueStatus.RESOLVED, "候选人已回答该追问"));
    }
    operations.add(new WorkStateOperation.UpdateTargetDepth(
        state.activeTargetId(),
        boundedDepth(state.activeTarget(), assessment.depthLevel())
    ));
    for (int index = 0; index < evidences.size(); index++) {
      operations.add(new WorkStateOperation.AddEvidenceRef(new WorkEvidenceRef(
          state.activeTargetId(),
          "ASSESSMENT",
          "turn-" + assessment.turnIndex() + "-evidence-" + index,
          evidences.get(index).quote()
      )));
    }
    for (int index = 0; index < assessment.probeGaps().size(); index++) {
      operations.add(new WorkStateOperation.OpenIssue(issue(state, assessment, index)));
    }
    return operations;
  }

  private static WorkIssue issue(
      InterviewWorkState state,
      AssessmentDecision assessment,
      int index
  ) {
    var gap = assessment.probeGaps().get(index);
    return new WorkIssue(
        issueId(assessment.turnIndex(), index + 1),
        state.activeTargetId(),
        CapabilityTarget.EvidenceMethod.CANDIDATE_ANSWER,
        gap.anchor(),
        gap.missingPoint(),
        WorkIssueStatus.OPEN,
        null
    );
  }

  private static DepthLevel boundedDepth(TargetWorkState target, DepthLevel proposed) {
    DepthLevel ceiling = target.target().depth().ceiling();
    return proposed.ordinal() > ceiling.ordinal() ? ceiling : proposed;
  }

  private static List<WorkStateOperation> actionBudget(
      TargetWorkState target,
      NextAction action
  ) {
    List<WorkStateOperation> operations = new ArrayList<>();
    operations.add(new WorkStateOperation.ConsumeBudget(
        target.targetId(), WorkBudgetType.TURN));
    if (action.type() == NextActionType.CALL_TOOL) {
      operations.add(new WorkStateOperation.ConsumeBudget(
          target.targetId(), WorkBudgetType.TOOL));
    } else if (action.issueId() != null) {
      operations.add(new WorkStateOperation.ConsumeBudget(
          target.targetId(), WorkBudgetType.FOLLOW_UP));
    }
    return operations;
  }

  private static NextTurnProvenanceDraft provenance(
      NextAction action,
      AssessmentDecision assessment
  ) {
    if (action.issueId() == null) {
      return NextTurnProvenanceDraft.planned();
    }
    String prefix = "assessment:" + assessment.turnIndex() + ":gap:";
    if (!action.issueId().startsWith(prefix)) {
      return NextTurnProvenanceDraft.planned();
    }
    int gapOrder = Integer.parseInt(action.issueId().substring(prefix.length()));
    return NextTurnProvenanceDraft.currentAssessmentGap(
        assessment.turnIndex(), gapOrder);
  }

  private static WorkStatePatch patch(
      InterviewWorkState state,
      WorkStatePatchSource sourceType,
      String sourceId,
      List<WorkStateOperation> operations
  ) {
    return new WorkStatePatch(
        UUID.randomUUID().toString(),
        state.sessionId(),
        state.revision(),
        state.revision() + 1,
        sourceType,
        sourceId,
        operations
    );
  }

  private static String policySource(int turnIndex, int step) {
    return "turn:" + turnIndex + ":step:" + step;
  }

  private record PolicyProjection(
      InterviewWorkState state,
      NextAction action,
      List<WorkStatePatch> patches
  ) {}

  private static String issueId(int turnIndex, int gapOrder) {
    return "assessment:" + turnIndex + ":gap:" + gapOrder;
  }

  record PreparedWorkDecision(
      String sessionId,
      int answerTurnIndex,
      InterviewWorkState projectedState,
      NextAction action,
      List<WorkStatePatch> patches,
      NextTurnProvenanceDraft provenance
  ) {

    List<WorkStatePatch> finalPatches(Integer nextTurnIndex) {
      if (nextTurnIndex == null) {
        return patches;
      }
      List<WorkStatePatch> completed = new ArrayList<>(patches);
      completed.add(patch(
          projectedState,
          WorkStatePatchSource.ACTION_RESULT,
          "turn:" + answerTurnIndex,
          List.of(new WorkStateOperation.ApplyActionResult(nextTurnIndex, action.issueId()))
      ));
      return List.copyOf(completed);
    }
  }
}
