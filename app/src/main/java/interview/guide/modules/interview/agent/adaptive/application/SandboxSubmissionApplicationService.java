package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.algorithm.judge.AlgorithmSubmissionService;
import interview.guide.modules.interview.agent.adaptive.algorithm.judge.SubmitAlgorithmCode;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import org.springframework.stereotype.Service;

/** 代码回答的应用命令，不伪装成由模型选择的通用 Tool。 */
@Service
public class SandboxSubmissionApplicationService {

  private final AlgorithmSubmissionService algorithmSubmissions;
  public SandboxSubmissionApplicationService(AlgorithmSubmissionService algorithmSubmissions) {
    this.algorithmSubmissions = algorithmSubmissions;
  }

  public void submit(String sessionId, CandidateAnswer answer) {
    var submission = answer.codeSubmission();
    if (submission == null) {
      return;
    }
    if (submission.patch()) {
      // 历史回答可恢复旧场景引用，但不能重新创建已退役的仓库补丁任务。
      throw new BusinessException(ErrorCode.BAD_REQUEST, "项目仓库补丁提交已停用");
    }
    algorithmSubmissions.submit(new SubmitAlgorithmCode(
        sessionId,
        answer.turnIndex(),
        submission.problemId(),
        SandboxLanguage.valueOf(submission.language()),
        answer.content(),
        SandboxRunMode.valueOf(submission.runMode())
    ));
  }
}
