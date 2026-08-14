package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecution;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(AdaptiveInterviewPersistenceService.class)
class AdaptiveInterviewPersistenceServiceTest {

  @Autowired
  private AdaptiveInterviewPersistenceService service;

  @Autowired
  private AdaptiveAgentToolCallRepository toolCallRepository;

  @Test
  @DisplayName("工具调用摘要、角色、轮次和稳定结果 ID 与问题事实一起落库")
  void shouldPersistToolCallAuditWithQuestionFact() {
    ToolExecution execution = new ToolExecution(
        "a".repeat(64),
        "question_bank_search",
        "读取审核题",
        "INTERVIEWER",
        1,
        "keys=[query]",
        "matchedQuestionIds=[42]",
        "question:42",
        "{\"stableId\":\"question:42\"}",
        8
    );

    service.create(
        "session-tool-audit",
        "candidate-1",
        "JD",
        "Resume",
        null,
        plan("session-tool-audit", 1),
        RespondAction.ask("第一题？", "使用审核题"),
        List.of(execution)
    );

    AdaptiveAgentToolCallEntity audit = toolCallRepository
        .findBySessionIdOrderByTurnIndexAscIdAsc("session-tool-audit")
        .getFirst();
    assertThat(audit.invocationId()).isEqualTo("a".repeat(64));
    assertThat(audit.role()).isEqualTo("INTERVIEWER");
    assertThat(audit.turnIndex()).isEqualTo(1);
    assertThat(audit.toolName()).isEqualTo("question_bank_search");
    assertThat(audit.reason()).isEqualTo("读取审核题");
    assertThat(audit.inputSummary()).isEqualTo("keys=[query]");
    assertThat(audit.outputSummary()).isEqualTo("matchedQuestionIds=[42]");
    assertThat(audit.resultId()).isEqualTo("question:42");
  }

  @Test
  @DisplayName("规划建议的工具与 Skill 经重读后保持不变")
  void shouldPersistPlannedToolsAndSkill() {
    InterviewPlan plan = InterviewPlan.decide(
        "session-plan-tools",
        new PlanProposal(List.of(new DimensionProposal(
            "专业基础",
            "缓存",
            "REDIS",
            2,
            List.of("question_bank_search", "rubric_lookup"),
            "java-backend"
        )))
    );

    service.create(
        "session-plan-tools",
        "candidate-1",
        "JD",
        "Resume",
        null,
        plan,
        RespondAction.ask("第一题？", "验证基础"),
        List.of()
    );

    assertThat(service.get("session-plan-tools").plan().dimensions().getFirst())
        .satisfies(dimension -> {
          assertThat(dimension.suggestedTools())
              .containsExactly("question_bank_search", "rubric_lookup");
          assertThat(dimension.suggestedSkill()).isEqualTo("java-backend");
        });
  }

  @Test
  @DisplayName("维度小结与回答和下一题在同一事务中保存并可重读")
  void shouldPersistDimensionBriefWithDecision() {
    service.create(
        "session-brief",
        "candidate-1",
        "JD",
        "Resume",
        null,
        plan("session-brief", 2),
        RespondAction.ask("第一题？", "验证基础"),
        List.of()
    );
    DimensionBrief brief = new DimensionBrief(
        "session-brief",
        0,
        "维度-0",
        "重点-0",
        "讨论了缓存一致性的方案与取舍",
        List.of(1)
    );

    PlannedInterview updated = service.recordDecision(
        "session-brief",
        new CandidateAnswer(1, "完整回答"),
        RespondAction.ask("第二题？", "继续验证"),
        List.of(),
        brief
    );

    assertThat(updated.dimensionBriefs()).containsExactly(brief);
    assertThat(service.get("session-brief").dimensionBriefs()).containsExactly(brief);
    assertThat(service.get("session-brief").history().turns().getFirst().answer())
        .isEqualTo("完整回答");
  }

  @Test
  @DisplayName("回答原文、决策摘要和下一题在同一事实历史中完整保存")
  void shouldPersistFullTurnAndNextQuestion() {
    String answer = "候选人的完整回答。".repeat(2000);
    service.create(
        "session-1",
        "candidate-1",
        "JD",
        "Resume",
        "provider-1",
        plan("session-1", 3),
        RespondAction.ask("第一题？", "验证基础"),
        List.of()
    );

    PlannedInterview interview = service.recordDecision(
        "session-1",
        new CandidateAnswer(1, answer),
        RespondAction.ask("第二题？", "需要验证边界条件"),
        List.of(),
        null
    );
    AdaptiveInterviewHistory history = interview.history();

    assertThat(history.session().currentTurn()).isEqualTo(2);
    assertThat(history.llmProvider()).isEqualTo("provider-1");
    assertThat(history.turns()).hasSize(2);
    assertThat(history.turns().getFirst().questionReason()).isEqualTo("验证基础");
    assertThat(history.turns().getLast().questionReason())
        .isEqualTo("需要验证边界条件");
    assertThat(history.turns().getFirst().answer()).isEqualTo(answer);
    assertThat(history.turns().getFirst().responseType()).isEqualTo(AgentResponseType.ASK);
    assertThat(history.turns().getFirst().decisionReason()).isEqualTo("需要验证边界条件");
    assertThat(history.turns().get(1).question()).isEqualTo("第二题？");
  }

  @Test
  @DisplayName("轮次预算覆盖模型建议后只保存结束裁决")
  void shouldPersistBudgetDecision() {
    service.create(
        "session-2",
        "candidate-1",
        "JD",
        "Resume",
        null,
        plan("session-2", 1),
        RespondAction.ask("第一题？", "验证基础"),
        List.of()
    );

    service.recordDecision(
        "session-2",
        new CandidateAnswer(1, "回答"),
        RespondAction.ask("第二题？", "继续验证"),
        List.of(),
        null
    );
    AdaptiveInterviewHistory history = service.recordDecision(
        "session-2",
        new CandidateAnswer(2, "第二轮回答"),
        RespondAction.ask("不应出现的下一题？", "模型希望继续"),
        List.of(),
        null
    ).history();

    assertThat(history.session().status()).isEqualTo(AdaptiveSessionStatus.COMPLETED);
    assertThat(history.turns()).hasSize(2);
    assertThat(history.turns().getLast().responseType()).isEqualTo(AgentResponseType.FINISH);
    assertThat(history.turns().getLast().decisionReason()).isEqualTo("轮次预算已用尽");
  }

  @Test
  @DisplayName("全部维度覆盖前拒绝模型提前结束")
  void shouldRejectEarlyFinish() {
    service.create(
        "session-early-finish",
        "candidate-1",
        "JD",
        "Resume",
        null,
        plan("session-early-finish", 2),
        RespondAction.ask("第一题？", "验证基础"),
        List.of()
    );

    assertThatThrownBy(() -> service.recordDecision(
        "session-early-finish",
        new CandidateAnswer(1, "回答"),
        RespondAction.finish("结束", "模型建议提前结束"),
        List.of(),
        null
    )).isInstanceOf(BusinessException.class)
        .hasMessageContaining("全部规划维度");

    PlannedInterview interview = service.get("session-early-finish");
    assertThat(interview.history().session().currentTurn()).isEqualTo(1);
    assertThat(interview.history().turns().getFirst().answer()).isNull();
  }

  @Test
  @DisplayName("过期回答失败时不写入轮次事实")
  void shouldNotPersistStaleAnswer() {
    service.create(
        "session-3",
        "candidate-1",
        "JD",
        "Resume",
        null,
        plan("session-3", 3),
        RespondAction.ask("第一题？", "验证基础"),
        List.of()
    );

    assertThatThrownBy(() -> service.recordDecision(
        "session-3",
        new CandidateAnswer(2, "错误轮次的回答"),
        RespondAction.ask("下一题？", "继续"),
        List.of(),
        null
    )).isInstanceOf(BusinessException.class)
        .hasMessageContaining("轮次");

    AdaptiveInterviewHistory history = service.get("session-3").history();
    assertThat(history.session().currentTurn()).isEqualTo(1);
    assertThat(history.turns().getFirst().answer()).isNull();
  }

  private InterviewPlan plan(String sessionId, int dimensionCount) {
    return InterviewPlan.decide(
        sessionId,
        new PlanProposal(java.util.stream.IntStream.range(0, dimensionCount)
            .mapToObj(index -> new DimensionProposal(
                "维度-" + index,
                "重点-" + index,
                "FOCUS_" + index,
                2,
                List.of(),
                "java-backend"
            ))
            .toList())
    );
  }
}
