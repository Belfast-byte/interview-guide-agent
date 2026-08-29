package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.algorithm.judge.AlgorithmSubmissionService;
import interview.guide.modules.interview.agent.adaptive.algorithm.judge.SubmitAlgorithmCode;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.CodePatchSubmissionService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.PatchCodeSubmission;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import org.springframework.stereotype.Service;

/** 代码回答的应用命令，不伪装成由模型选择的通用 Tool。 */
@Service
public class SandboxSubmissionApplicationService {

  private final AlgorithmSubmissionService algorithmSubmissions;
  private final CodePatchSubmissionService patchSubmissions;

  public SandboxSubmissionApplicationService(
      AlgorithmSubmissionService algorithmSubmissions,
      CodePatchSubmissionService patchSubmissions
  ) {
    this.algorithmSubmissions = algorithmSubmissions;
    this.patchSubmissions = patchSubmissions;
  }

  public void submit(String sessionId, CandidateAnswer answer) {
    var submission = answer.codeSubmission();
    if (submission == null) {
      return;
    }
    if (submission.patch()) {
      patchSubmissions.submit(new PatchCodeSubmission(
          sessionId,
          answer.turnIndex(),
          submission.scenarioId(),
          SandboxLanguage.valueOf(submission.language()),
          answer.content()
      ));
      return;
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
