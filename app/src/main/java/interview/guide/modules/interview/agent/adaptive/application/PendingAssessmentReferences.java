package interview.guide.modules.interview.agent.adaptive.application;

/** 最终事务前供 Agent 引用的临时负 ID；提交时显式解析为数据库事实 ID。 */
public final class PendingAssessmentReferences {

  private static final long EVIDENCE_ID_OFFSET = 1_000_000L;
  public static final long ASSESSMENT_ID = -1L;

  private PendingAssessmentReferences() {}

  public static long gapId(int zeroBasedIndex) {
    return -(zeroBasedIndex + 1L);
  }

  public static long evidenceId(int zeroBasedIndex) {
    return -(EVIDENCE_ID_OFFSET + zeroBasedIndex);
  }

  public static boolean pending(long id) {
    return id < 0;
  }
}
