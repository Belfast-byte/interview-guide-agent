package interview.guide.modules.interview.agent.adaptive.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.AnswerProcessingStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodeRepairTurnFieldsTest {
  @Test
  void preservesCodeOnlyAnswerAndRetryState() {
    var turn = turn();
    String code = "  void reserve() {\n    stocks.tryReserve();\n  }\n";
    var answer = new CandidateAnswer(1, "  ", null, new CandidateAnswer.CodeRepairAnswer(code));
    turn.recordAnswer(answer);
    turn.claimExecution("execution", Duration.ofMinutes(1));
    assertThat(turn.hasAnswer()).isTrue();
    assertThat(turn.answer()).isNull();
    assertThat(turn.candidateAnswer()).isEqualTo(answer);
    assertThat(turn.toDomain().submittedCode()).isEqualTo(code);
    assertThat(turn.toDomain().answerStatus()).isEqualTo(AnswerProcessingStatus.PROCESSING);
    turn.failExecution("execution", "模型失败");
    assertThat(turn.toDomain().answerStatus()).isEqualTo(AnswerProcessingStatus.RETRYABLE);
    assertThat(turn.candidateAnswer()).isEqualTo(answer);
  }

  @Test
  void taskIsFrozenAndOriginalRootIsSelf() {
    var turn = turn();
    assertThat(turn.codeRepair().codeTaskTurnIndex()).isEqualTo(1);
    assertThat(turn.codeRepair().codeTask().initialCode()).isEqualTo("void reserve() {}");
    assertThatThrownBy(() -> turn.recordAnswer(new CandidateAnswer(1, "已修好")))
        .hasMessageContaining("非空代码");
    assertThatThrownBy(() -> new CodeRepairTurnFields(
        RespondAction.ask("继续改", "验证").withCodeTask(QuestionType.CODE_REPAIR, null, 2), 2))
        .hasMessageContaining("早于当前轮次");
  }

  private AdaptiveAgentTurnEntity turn() {
    var task = new CodeRepairTask("void reserve() {}", List.of("不能超卖"), List.of("数据库共享"),
        new CodeRepairTask.ReviewGuide(List.of(new CodeRepairTask.Check(
            "C1", "非原子操作", "并发", "原子扣减"))));
    return new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation("session", 1, 0,
        RespondAction.ask("修复库存", "验证并发").withCodeTask(QuestionType.CODE_REPAIR, task, null),
        TurnProvenance.initial()));
  }
}
