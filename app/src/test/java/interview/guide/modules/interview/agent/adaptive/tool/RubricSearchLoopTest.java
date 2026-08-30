package interview.guide.modules.interview.agent.adaptive.tool;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemoryValidator;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecisionValidator;
import interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation.AdoptableSource;
import interview.guide.modules.interview.agent.adaptive.runtime.InterviewAgentLoop;
import interview.guide.modules.interview.agent.adaptive.runtime.ReadToolCall;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RubricSearchLoopTest {

  private static final String SOURCE_REF = "rubric:question:1:rubric@v1";

  @Test
  @DisplayName("rubric_search Observation 回流模型后形成采用来源的 ASK")
  void shouldReturnObservationToModelBeforeAsk() {
    List<interview.guide.modules.interview.agent.adaptive.runtime.DecisionModelContext> requests =
        new ArrayList<>();
    InterviewAgentLoop loop = loop(requests);

    AgentDecision decision = loop.run(context(), Duration.ofSeconds(1));

    assertThat(requests).hasSize(2);
    assertThat(requests.get(1).observations()).singleElement().satisfies(observation -> {
      assertThat(observation.kind()).isEqualTo(DecisionObservation.Kind.TOOL_SUCCESS);
      assertThat(observation.reference()).isEqualTo("tool-0-0");
    });
    AgentDecision.Ask ask = (AgentDecision.Ask) decision.action();
    assertThat(ask.question().adoptedSourceRefs()).containsExactly(SOURCE_REF);
    assertThat(decision.workingMemory().deliberation().adoptedObservationRefs())
        .containsExactly("tool-0-0");
  }

  private InterviewAgentLoop loop(
      List<interview.guide.modules.interview.agent.adaptive.runtime.DecisionModelContext> requests
  ) {
    return new InterviewAgentLoop(
        model(requests),
        new AgentDecisionValidator(new WorkingMemoryValidator()),
        batch -> List.of(new DecisionObservation(
            "tool-0-0",
            DecisionObservation.Kind.TOOL_SUCCESS,
            null,
            null,
            RubricSearchTool.NAME,
            Map.of("rubric", "按边界事实评分"),
            List.of(new AdoptableSource(
                SOURCE_REF, "rubric", "question:1:rubric", "v1")))),
        new DeadlineExecutor()
    );
  }

  private interview.guide.modules.interview.agent.adaptive.runtime.InterviewDecisionModel model(
      List<interview.guide.modules.interview.agent.adaptive.runtime.DecisionModelContext> requests
  ) {
    return request -> {
      requests.add(request);
      if (request.observations().isEmpty()) {
        return new AgentDecision(
            WorkingMemory.empty(),
            new AgentDecision.CallReadTools(List.of(new ReadToolCall(
                RubricSearchTool.NAME,
                Map.of("query", "并发冲突", "intent", "校准追问", "levelHints", List.of("L3")),
                "需要评分边界"))));
      }
      WorkingMemory memory = new WorkingMemory(
          null,
          new WorkingMemory.Focus("target-0", null, List.of()),
          new WorkingMemory.Deliberation(List.of(), "验证并发边界", List.of("tool-0-0")));
      return new AgentDecision(memory, new AgentDecision.Ask(
          "target-0",
          null,
          new AgentDecision.QuestionDraft("发生写冲突时如何处理？", "验证并发边界", List.of(SOURCE_REF))
      ));
    };
  }

  private AgentContext context() {
    CoverageView coverage = new CoverageView(
        0,
        3,
        List.of(new CoverageView.TargetCoverage(
            "target-0", null, 0, null, List.of(), List.of())),
        List.of(),
        List.of()
    );
    return new AgentContext(
        new AgentContext.SessionWindow(
            new AgentContext.SessionIdentity(
                "session-1", "provider-1", new MemoryOwner("tenant-1", "candidate-1")),
            SessionMode.EVALUATION,
            3
        ),
        new AgentContext.Facts(
            coverage, List.of(), List.of(), List.of(RubricSearchTool.NAME)),
        WorkingMemory.empty()
    );
  }
}
