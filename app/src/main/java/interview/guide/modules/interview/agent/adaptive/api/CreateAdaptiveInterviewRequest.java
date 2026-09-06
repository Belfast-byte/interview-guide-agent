package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 创建自适应面试请求。
 */
public record CreateAdaptiveInterviewRequest(
    @NotBlank(message = "JD 不能为空") String jd,
    @NotBlank(message = "简历不能为空") String resume,
    @Size(max = 64, message = "LLM Provider 标识不能超过 64 个字符") String providerId,
    @NotNull(message = "会话模式不能为空") SessionMode mode,
    @NotNull(message = "候选人级别不能为空") CandidateLevel candidateLevel,
    @NotNull(message = "练习范围不能为空") List<@Valid PracticeTopicRequest> practiceScope
) {

  InterviewSessionSettings settings() {
    return new InterviewSessionSettings(
        mode,
        candidateLevel,
        new PracticeScope(practiceScope.stream().map(PracticeTopicRequest::toTopicKey).toList())
    );
  }

  /** 练习模式中由用户明确选择的一个主题。 */
  public record PracticeTopicRequest(
      @NotBlank(message = "Skill 标识不能为空") String skillId,
      @NotBlank(message = "考察重点标识不能为空") String focusId
  ) {

    TopicKey toTopicKey() {
      return new TopicKey(skillId, focusId);
    }
  }
}
