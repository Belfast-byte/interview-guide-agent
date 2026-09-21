package interview.guide.modules.interview.agent.adaptive.role;

import static org.assertj.core.api.Assertions.*;
import interview.guide.modules.interview.agent.adaptive.core.context.*;
import interview.guide.modules.interview.agent.adaptive.core.session.*;
import interview.guide.modules.interview.agent.adaptive.runtime.*;
import interview.guide.modules.interview.agent.adaptive.planning.InitialQuestionProposal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;
import tools.jackson.databind.ObjectMapper;

class DecisionContextProjectionTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void carriesCurrentTaskLatestCodeAndPendingReviewWhileOldCodeRequiresReadTool() {
    var old = turn(1, task("OLD_INITIAL"), 1, "OLD_SUBMISSION");
    var root = turn(2, task("CURRENT_INITIAL"), 2, "FIRST_SUBMISSION");
    var current = turn(3, null, 2, "LATEST_SUBMISSION").withAssessmentFeedback(
        new AdaptiveInterviewTurn.AssessmentFeedback(DepthLevel.L2, "PENDING_REVIEW", null, List.of()));
    var context = context(List.of(old, root, current));
    String json = mapper.writeValueAsString(DecisionContextProjection.project(new DecisionModelContext(context, List.of())));
    assertThat(json).contains("CURRENT_INITIAL", "LATEST_SUBMISSION", "PENDING_REVIEW", "originalCodeTask")
        .doesNotContain("OLD_INITIAL", "OLD_SUBMISSION", "FIRST_SUBMISSION");
    var draft = new AgentDecision.QuestionDraft("再修改", "验证", List.of(),
        CodeRepairTask.QuestionType.CODE_REPAIR, null, 1);
    assertThatCode(() -> CodeQuestionValidator.validate(new AgentDecision.Ask("target-0", null, draft), context)).doesNotThrowAnyException();
    assertThat(context.facts().recentTurns().getFirst().codeTask().initialCode()).isEqualTo("OLD_INITIAL");
  }

  @Test
  void plannerAndDecisionSchemasBothExposeMandatoryCodeContractWithoutTextFallback() {
    String planner = new BeanOutputConverter<>(InitialQuestionProposal.class).getJsonSchema();
    String decision = new BeanOutputConverter<>(AgentDecision.QuestionDraft.class).getJsonSchema();
    for (String schema : List.of(planner, decision)) {
      assertThat(schema).contains("questionType", "CODE_REPAIR", "TEXT", "codeTask", "reviewGuide", "codeTaskTurnIndex");
    }
    var output = new AgentDecision.QuestionDraft("question", "reason", List.of(), null, null, null);
    assertThat(output.questionType()).isNull();
    assertThatThrownBy(() -> CodeQuestionValidator.validateShape(output))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("questionType");
  }

  @Test
  void savedSourcesDescribeOnlyActualAdoptionInThisQuestion() {
    var old = WorkingMemory.empty().withAdoptedSources(List.of("question:1", "episode:9"));
    var next = old.withAdoptedSources(List.of("question:2", "question:3", "tool-0-0"));
    assertThat(next.deliberation().adoptedObservationRefs()).containsExactly("question:2", "question:3");
    assertThat(old.deliberation().adoptedObservationRefs()).containsExactly("question:1", "episode:9");
  }

  private AgentContext context(List<AdaptiveInterviewTurn> turns) {
    return new AgentContext(new AgentContext.SessionWindow(new AgentContext.SessionIdentity(
        "session", "provider", new MemoryOwner(null, "candidate")), SessionMode.PRACTICE, 10),
        new AgentContext.Facts(new CoverageView(turns.size(), 7, List.of(), List.of(), List.of()),
            turns, List.of(), List.of("code_task_read")), WorkingMemory.empty());
  }

  private AdaptiveInterviewTurn turn(int index, CodeRepairTask task, int root, String code) {
    return new AdaptiveInterviewTurn(index, 0, "question", "reason", null, null, null, null,
        TurnProvenance.initial(), List.of(), AnswerProcessingStatus.RETRYABLE, null,
        CodeRepairTask.QuestionType.CODE_REPAIR, task, root, code, null);
  }

  private CodeRepairTask task(String initial) {
    return new CodeRepairTask(initial, List.of("requirement"), List.of("assumption"),
        new CodeRepairTask.ReviewGuide(List.of(new CodeRepairTask.Check("C1", "defect", "trigger", "acceptance"))));
  }
}
