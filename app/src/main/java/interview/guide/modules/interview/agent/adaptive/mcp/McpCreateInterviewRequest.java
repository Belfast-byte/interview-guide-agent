package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import java.util.List;

public record McpCreateInterviewRequest(
      String candidateId,
      String jd,
      String resume,
      String llmProvider,
      SessionMode mode,
      CandidateLevel candidateLevel,
      List<TopicKey> practiceScope
  ) {

    InterviewSessionSettings settings() {
      return new InterviewSessionSettings(
          mode,
          candidateLevel,
          new PracticeScope(practiceScope)
      );
    }

  private static final int MAX_IDENTIFIER_LENGTH = 64;

  void validate() {
    if (candidateId() == null
        || candidateId().isBlank()
        || candidateId().length() > MAX_IDENTIFIER_LENGTH) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "候选人标识无效");
    }
    if (jd() == null
        || jd().isBlank()
        || resume() == null
        || resume().isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "JD 和简历不能为空");
    }
    if (llmProvider() != null && llmProvider().length() > MAX_IDENTIFIER_LENGTH) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "LLM Provider 标识无效");
    }
    if (mode() == null
        || candidateLevel() == null
        || practiceScope() == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "面试模式参数不能为空");
    }
    settings();
  }

}
