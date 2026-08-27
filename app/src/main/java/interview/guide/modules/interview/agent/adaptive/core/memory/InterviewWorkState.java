package interview.guide.modules.interview.agent.adaptive.core.memory;

import java.util.List;

/** 单次面试持续演变的唯一运行状态。 */
public record InterviewWorkState(
    String sessionId,
    long revision,
    WorkPhase phase,
    List<TargetWorkState> targets,
    String activeTargetId,
    String attentionFocus,
    List<WorkEvidenceRef> activeEvidenceRefs,
    List<WorkIssue> openIssues,
    Integer awaitingAnswerTurnIndex,
    String awaitingIssueId,
    String activeActionIntentId
) {

  public InterviewWorkState {
    targets = List.copyOf(targets);
    activeEvidenceRefs = List.copyOf(activeEvidenceRefs);
    openIssues = List.copyOf(openIssues);
  }

  public TargetWorkState activeTarget() {
    return targets.stream()
        .filter(target -> target.targetId().equals(activeTargetId))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("WorkState 缺少当前目标"));
  }

  public List<WorkIssue> activeOpenIssues() {
    return openIssues.stream()
        .filter(WorkIssue::isOpen)
        .filter(issue -> issue.targetId().equals(activeTargetId))
        .toList();
  }

  public String nextPendingTargetId() {
    return targets.stream()
        .filter(target -> target.status() == TargetWorkStatus.PENDING)
        .map(TargetWorkState::targetId)
        .findFirst()
        .orElse(null);
  }

  public InterviewWorkState withRevision(long nextRevision) {
    return copy(new NextState(nextRevision, phase, targets, activeTargetId, attentionFocus,
        activeEvidenceRefs, openIssues, awaitingAnswerTurnIndex, awaitingIssueId,
        activeActionIntentId));
  }

  public InterviewWorkState withTargets(List<TargetWorkState> nextTargets) {
    return copy(new NextState(revision, phase, nextTargets, activeTargetId, attentionFocus,
        activeEvidenceRefs, openIssues, awaitingAnswerTurnIndex, awaitingIssueId,
        activeActionIntentId));
  }

  public InterviewWorkState withFocus(String focus) {
    return copy(new NextState(revision, phase, targets, activeTargetId, focus,
        activeEvidenceRefs, openIssues, awaitingAnswerTurnIndex, awaitingIssueId,
        activeActionIntentId));
  }

  public InterviewWorkState withEvidence(List<WorkEvidenceRef> evidenceRefs) {
    return copy(new NextState(revision, phase, targets, activeTargetId, attentionFocus,
        evidenceRefs, openIssues, awaitingAnswerTurnIndex, awaitingIssueId,
        activeActionIntentId));
  }

  public InterviewWorkState withIssues(List<WorkIssue> issues) {
    return copy(new NextState(revision, phase, targets, activeTargetId, attentionFocus,
        activeEvidenceRefs, issues, awaitingAnswerTurnIndex, awaitingIssueId,
        activeActionIntentId));
  }

  public InterviewWorkState withExecution(
      WorkPhase nextPhase,
      Integer turnIndex,
      String issueId,
      String intentId
  ) {
    return copy(new NextState(revision, nextPhase, targets, activeTargetId, attentionFocus,
        activeEvidenceRefs, openIssues, turnIndex, issueId, intentId));
  }

  public InterviewWorkState switchTarget(
      String nextTargetId,
      TargetWorkStatus currentStatus
  ) {
    List<TargetWorkState> nextTargets = targets.stream()
        .map(target -> switchStatus(target, nextTargetId, currentStatus))
        .toList();
    List<WorkIssue> nextIssues = currentStatus == TargetWorkStatus.EXHAUSTED
        ? abandonActiveIssues()
        : openIssues;
    TargetWorkState next = nextTargets.stream()
        .filter(target -> target.targetId().equals(nextTargetId))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("下一个目标不存在"));
    return copy(new NextState(
        revision,
        WorkPhase.READY_TO_DECIDE,
        nextTargets,
        nextTargetId,
        next.target().identity().focus(),
        List.of(),
        nextIssues,
        null,
        null,
        null
    ));
  }

  public InterviewWorkState finish(TargetWorkStatus currentStatus) {
    List<TargetWorkState> nextTargets = targets.stream()
        .map(target -> target.targetId().equals(activeTargetId)
            ? target.withStatus(currentStatus)
            : target)
        .toList();
    List<WorkIssue> nextIssues = currentStatus == TargetWorkStatus.EXHAUSTED
        ? abandonActiveIssues()
        : openIssues;
    return copy(new NextState(revision, WorkPhase.FINISHED, nextTargets, activeTargetId,
        attentionFocus, activeEvidenceRefs, nextIssues, null, null, null));
  }

  private TargetWorkState switchStatus(
      TargetWorkState target,
      String nextTargetId,
      TargetWorkStatus currentStatus
  ) {
    if (target.targetId().equals(activeTargetId)) {
      return target.withStatus(currentStatus);
    }
    if (target.targetId().equals(nextTargetId)) {
      return target.withStatus(TargetWorkStatus.ACTIVE);
    }
    return target;
  }

  private List<WorkIssue> abandonActiveIssues() {
    return openIssues.stream()
        .map(issue -> issue.targetId().equals(activeTargetId) && issue.isOpen()
            ? issue.close(WorkIssueStatus.ABANDONED, "目标预算或深度上限已到")
            : issue)
        .toList();
  }

  private InterviewWorkState copy(NextState next) {
    return new InterviewWorkState(
        sessionId,
        next.revision(),
        next.phase(),
        next.targets(),
        next.targetId(),
        next.focus(),
        next.evidenceRefs(),
        next.issues(),
        next.turnIndex(),
        next.issueId(),
        next.intentId()
    );
  }

  private record NextState(
      long revision,
      WorkPhase phase,
      List<TargetWorkState> targets,
      String targetId,
      String focus,
      List<WorkEvidenceRef> evidenceRefs,
      List<WorkIssue> issues,
      Integer turnIndex,
      String issueId,
      String intentId
  ) {}

}
