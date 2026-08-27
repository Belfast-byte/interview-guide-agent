package interview.guide.modules.interview.agent.adaptive.core.context;

import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.TargetWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkIssue;

/** 面试官只读的当前目标、焦点、问题和预算视图。 */
public record InterviewerWorkView(
    String targetId,
    TopicKey topic,
    DepthLevel expectedDepth,
    DepthLevel depthCeiling,
    DepthLevel currentDepth,
    String attentionFocus,
    WorkIssue issue,
    int remainingTurns,
    int remainingFollowUps,
    int remainingTools
) {

  public static InterviewerWorkView from(
      InterviewWorkState state,
      String issueId
  ) {
    TargetWorkState target = state.activeTarget();
    WorkIssue issue = issueId == null
        ? null
        : state.activeOpenIssues().stream()
            .filter(candidate -> candidate.issueId().equals(issueId))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("策略选择的问题不存在"));
    return new InterviewerWorkView(
        target.targetId(),
        target.target().identity().topic(),
        target.target().depth().expected(),
        target.target().depth().ceiling(),
        target.currentDepth(),
        state.attentionFocus(),
        issue,
        target.remainingBudget().turns(),
        target.remainingBudget().followUps(),
        target.remainingBudget().tools()
    );
  }
}
