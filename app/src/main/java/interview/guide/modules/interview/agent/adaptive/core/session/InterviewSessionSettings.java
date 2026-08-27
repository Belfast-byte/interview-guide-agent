package interview.guide.modules.interview.agent.adaptive.core.session;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;

/** 会话创建时固定的模式、候选人级别和练习范围。 */
public record InterviewSessionSettings(
    SessionMode mode,
    CandidateLevel candidateLevel,
    PracticeScope practiceScope
) {

  public InterviewSessionSettings {
    validateScope(mode, practiceScope);
  }

  private static void validateScope(SessionMode mode, PracticeScope scope) {
    if (mode == SessionMode.EVALUATION && !scope.isEmpty()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "正式评估不能指定练习范围");
    }
    if (mode == SessionMode.PRACTICE && scope.isEmpty()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "练习模式必须选择至少一个主题");
    }
  }
}
