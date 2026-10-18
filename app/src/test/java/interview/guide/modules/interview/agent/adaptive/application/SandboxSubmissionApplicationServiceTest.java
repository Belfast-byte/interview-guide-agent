package interview.guide.modules.interview.agent.adaptive.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.algorithm.judge.AlgorithmSubmissionService;
import interview.guide.modules.interview.agent.adaptive.algorithm.judge.SubmitAlgorithmCode;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateCodeSubmission;
import org.junit.jupiter.api.Test;

class SandboxSubmissionApplicationServiceTest {
  private final AlgorithmSubmissionService algorithms = mock(AlgorithmSubmissionService.class);
  private final SandboxSubmissionApplicationService service = new SandboxSubmissionApplicationService(algorithms);

  @Test
  void codeRepairRemainsModelReviewedWithoutSandboxSubmission() {
    service.submit("session", new CandidateAnswer(1, "修改说明", null,
        new CandidateAnswer.CodeRepairAnswer("void fixed() {}")));
    verifyNoInteractions(algorithms);
  }

  @Test
  void algorithmSubmissionKeepsSourceLanguageAndRunMode() {
    service.submit("session", new CandidateAnswer(2, "class Solution {}",
        new CandidateCodeSubmission("two-sum", "JAVA", "SAMPLE")));
    verify(algorithms).submit(new SubmitAlgorithmCode(
        "session", 2, "two-sum", SandboxLanguage.JAVA, "class Solution {}", SandboxRunMode.SAMPLE));
  }

  @Test
  void restoredRepositoryPatchCannotCreateNewExecution() {
    var answer = new CandidateAnswer(1, "patch",
        new CandidateCodeSubmission(null, "old-scenario", "JAVA", "FULL"));
    assertThatThrownBy(() -> service.submit("session", answer))
        .isInstanceOf(BusinessException.class).hasMessage("项目仓库补丁提交已停用");
    verifyNoInteractions(algorithms);
  }
}
