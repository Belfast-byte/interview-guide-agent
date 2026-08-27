package interview.guide.modules.interview.agent.adaptive.core.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;

/** 当前会话需要继续验证或明确放弃的问题。 */
public record WorkIssue(
    String issueId,
    String targetId,
    CapabilityTarget.EvidenceMethod evidenceMethod,
    String anchor,
    String missingPoint,
    WorkIssueStatus status,
    String closeReason
) {

  public WorkIssue close(WorkIssueStatus nextStatus, String reason) {
    return new WorkIssue(
        issueId,
        targetId,
        evidenceMethod,
        anchor,
        missingPoint,
        nextStatus,
        reason
    );
  }

  public boolean isOpen() {
    return status == WorkIssueStatus.OPEN || status == WorkIssueStatus.INVESTIGATING;
  }
}
