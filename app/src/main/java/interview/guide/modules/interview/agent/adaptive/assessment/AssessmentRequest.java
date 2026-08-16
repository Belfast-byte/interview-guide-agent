package interview.guide.modules.interview.agent.adaptive.assessment;

/**
 * 评估请求，包含上下文、当前 Skill 知识基线与证据来源。
 */
public record AssessmentRequest(
    String sessionId,
    int turnIndex,
    AssessmentContext context,
    String skillReferenceSection
) {

  public AssessmentRequest(
      String sessionId,
      int turnIndex,
      AssessmentContext context
  ) {
    this(sessionId, turnIndex, context, "");
  }
}
