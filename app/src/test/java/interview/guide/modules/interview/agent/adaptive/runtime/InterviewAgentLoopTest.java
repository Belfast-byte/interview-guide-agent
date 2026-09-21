package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemoryValidator;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.core.session.AnswerProcessingStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.tool.InterviewToolCallback;
import interview.guide.modules.interview.agent.adaptive.tool.InterviewToolContext;
import interview.guide.modules.interview.agent.adaptive.tool.ReadToolResult;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

class InterviewAgentLoopTest {
  private final JsonMapper json = new JsonMapper();

  @Test
  void acceptsModelSelectedTargetAndGap() {
    var expected = ask("target-1", 12L, memory("target-1", 12L));
    assertThat(loop(request -> proposal(expected)).run(context(), Duration.ofSeconds(5))).isEqualTo(expected);
  }

  @Test
  void invalidTargetAndForgedSourceReturnNativeRejectionsWithoutAdoptingInvalidMemory() {
    var requests = new ArrayList<DecisionModelContext>();
    var loop = loop(request -> {
      requests.add(request);
      return switch (requests.size()) {
        case 1 -> proposal(ask("missing", null, memory("target-0", null)));
        case 2 -> proposal(askWithSourceRef("forged"));
        default -> proposal(ask("target-1", 12L, memory("target-1", 12L)));
      };
    });
    assertThat(loop.run(context(), Duration.ofSeconds(5)).action()).isInstanceOf(AgentDecision.Ask.class);
    assertThat(requests).hasSize(3);
    assertThat(requests.get(1).observations()).isEmpty();
    assertThat(json.writeValueAsString(requests.get(1).history())).contains("VALIDATION_REJECTION", "Target");
    assertThat(json.writeValueAsString(requests.get(2).history())).contains("采用来源");
    assertThat(requests.get(2).agentContext().workingMemory()).isEqualTo(context().workingMemory());
  }

  @Test
  void nativeProposalBindsCompleteCodeRepairTaskAndRejectsUnknownNestedFields() {
    var task = new interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask(
        "class Account { int balance; }", List.of("原子扣款"), List.of("多线程"),
        new interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.ReviewGuide(List.of(
            new interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.Check(
                "C1", "并发更新丢失", "同时扣款", "扣款一致"))));
    var expected = new AgentDecision(memory("target-1", 12L), new AgentDecision.Ask("target-1", 12L,
        new AgentDecision.QuestionDraft("修复账户并发扣款", "检查并发边界", List.of(), QuestionType.CODE_REPAIR, task, null)));
    var requests = new ArrayList<DecisionModelContext>();
    var loop = loop(request -> {
      requests.add(request);
      if (requests.size() > 1) return proposal(expected);
      var call = proposal(expected).getResult().getOutput().getToolCalls().getFirst();
      var args = (tools.jackson.databind.node.ObjectNode) json.readTree(call.arguments());
      ((tools.jackson.databind.node.ObjectNode) args.path("question").path("codeTask")).put("owner", "forged");
      return response(call("bad", call.name(), json.writeValueAsString(args)));
    });
    assertThat(loop.run(context(), Duration.ofSeconds(5))).isEqualTo(expected);
    assertThat(requests).hasSize(2);
    assertThat(json.writeValueAsString(requests.get(1).history())).contains("VALIDATION_REJECTION");
  }

  @Test
  void finishIsARequestLocalProposal() {
    var expected = new AgentDecision(memory("target-1", 12L), new AgentDecision.Finish("事实充分"));
    assertThat(loop(request -> proposal(expected)).run(context(), Duration.ofSeconds(5))).isEqualTo(expected);
  }

  @Test
  void initialBudgetObservationIsPresentBeforeFirstModelCall() {
    var observation = new DecisionObservation("budget", DecisionObservation.Kind.BUDGET_EXHAUSTED,
        "target", "切换目标", null, Map.of(), List.of());
    var loop = loop(request -> {
      assertThat(request.observations()).containsExactly(observation);
      return proposal(ask("target-1", 12L, memory("target-1", 12L)));
    });
    loop.run(context(), List.of(observation), Duration.ofSeconds(5));
  }

  @Test
  void rejectsMultipleFinalProposalsEvenIfOneIsMalformed() {
    var requests = new ArrayList<DecisionModelContext>();
    var valid = proposal(ask("target-1", 12L, memory("target-1", 12L)));
    var loop = loop(request -> {
      requests.add(request);
      if (requests.size() > 1) return valid;
      var first = valid.getResult().getOutput().getToolCalls().getFirst();
      return response(first, call("conflict", "propose_finish", "{}"));
    });
    loop.run(context(), Duration.ofSeconds(5));
    assertThat(requests).hasSize(2);
    assertThat(json.writeValueAsString(requests.get(1).history())).contains("多个最终提案");
    var result = (ToolResponseMessage) requests.get(1).history().getLast();
    assertThat(result.getResponses()).hasSize(2);
  }

  @Test
  void unknownToolAndPlainTextAreCorrectableInTheSameLoop() {
    var requests = new ArrayList<DecisionModelContext>();
    var loop = loop(request -> {
      requests.add(request);
      return switch (requests.size()) {
        case 1 -> response(call("unknown", "delete_database", "{}"));
        case 2 -> new ChatResponse(List.of(new Generation(new AssistantMessage("结束吧"))));
        default -> proposal(new AgentDecision(WorkingMemory.empty(), new AgentDecision.Finish("足够")));
      };
    });
    loop.run(context(), Duration.ofSeconds(5));
    assertThat(requests).hasSize(3);
    assertThat(json.writeValueAsString(requests.get(1).history())).contains("白名单", "unknown");
    assertThat(json.writeValueAsString(requests.get(2).history())).contains("缺少最终提案");
  }

  @Test
  void queryResultsAreSeenBeforeTheirSourcesCanBeAdoptedAndProposalDoesNotSpendReadBudget() {
    var requests = new ArrayList<DecisionModelContext>();
    var properties = new AdaptiveAgentProperties();
    properties.setMaxReadToolCalls(1);
    var read = new Read();
    var callbacks = java.util.Arrays.stream(ToolCallbacks.from(read))
        .map(callback -> new InterviewToolCallback(callback, true)).toArray(ToolCallback[]::new);
    var expected = askWithSourceRef("question:1");
    var loop = new InterviewAgentLoop(request -> {
      requests.add(request);
      var finalCall = proposal(expected).getResult().getOutput().getToolCalls().getFirst();
      return requests.size() == 1 ? response(call("read", "read", "{}"), finalCall) : response(finalCall);
    }, new AgentDecisionValidator(new WorkingMemoryValidator()), () -> callbacks,
        new DeadlineExecutor(), properties);
    var base = context();
    var context = new AgentContext(base.session(), new AgentContext.Facts(base.facts().coverage(),
        base.facts().recentTurns(), List.of(), List.of("read")), base.workingMemory());
    assertThat(loop.run(context, Duration.ofSeconds(5))).isEqualTo(expected);
    assertThat(requests).hasSize(2);
    assertThat(requests.get(1).observations()).isEmpty();
    assertThat(json.writeValueAsString(requests.get(1).history())).contains("TOOL_SUCCESS", "采用来源");
    assertThat(read.calls).isEqualTo(1);
  }

  @Test
  void isolatedQueryFailureStillReturnsSuccessfulSiblingToTheNextModelCall() {
    var requests = new ArrayList<DecisionModelContext>();
    var callbacks = java.util.Arrays.stream(ToolCallbacks.from(new Read(), new FailingRead()))
        .map(callback -> new InterviewToolCallback(callback, true)).toArray(ToolCallback[]::new);
    var expected = askWithSourceRef("question:1");
    var loop = new InterviewAgentLoop(request -> {
      requests.add(request);
      return requests.size() == 1
          ? response(call("failed-id", "fail", "{}"), call("successful-id", "read", "{}"))
          : proposal(expected);
    }, new AgentDecisionValidator(new WorkingMemoryValidator()), () -> callbacks,
        new DeadlineExecutor(), new AdaptiveAgentProperties());
    var base = context();
    var context = new AgentContext(base.session(), new AgentContext.Facts(base.facts().coverage(),
        base.facts().recentTurns(), List.of(), List.of("read", "fail")), base.workingMemory());
    assertThat(loop.run(context, Duration.ofSeconds(5))).isEqualTo(expected);
    assertThat(requests).hasSize(2);
    var results = ((ToolResponseMessage) requests.get(1).history().getLast()).getResponses();
    assertThat(results).extracting(ToolResponseMessage.ToolResponse::id)
        .containsExactly("failed-id", "successful-id");
    assertThat(results.getFirst().responseData()).contains("TOOL_ERROR").doesNotContain("private failure");
    assertThat(results.getLast().responseData()).contains("TOOL_SUCCESS", "question:1");
  }

  static class FailingRead {
    @Tool(name = "fail", description = "failing query")
    public String fail() {
      throw new BusinessException(interview.guide.common.exception.ErrorCode.AI_SERVICE_ERROR, "private failure");
    }
  }

  @Test
  void boundedStepsAndSharedDeadlineFailExplicitly() {
    var loop = loop(request -> new ChatResponse(List.of(new Generation(new AssistantMessage("text")))));
    assertThatThrownBy(() -> loop.run(context(), Duration.ofSeconds(5)))
        .isInstanceOf(BusinessException.class).hasMessageContaining("决策次数");
    assertThatThrownBy(() -> loop.run(context(), Duration.ZERO))
        .isInstanceOf(BusinessException.class).hasMessageContaining("超时");
  }

  @Test
  void invalidMemoryCannotEnterFinalSnapshot() {
    var invalidMemory = new WorkingMemory(null, WorkingMemory.empty().focus(),
        new WorkingMemory.Deliberation(List.of(), null, java.util.Collections.singletonList(null)));
    var expected = ask("target-1", 12L, memory("target-1", 12L));
    var requests = new ArrayList<DecisionModelContext>();
    var loop = loop(request -> {
      requests.add(request);
      return proposal(requests.size() == 1 ? ask("target-1", 12L, invalidMemory) : expected);
    });
    assertThat(loop.run(context(), Duration.ofSeconds(5))).isEqualTo(expected);
    assertThat(requests).hasSize(2);
    assertThat(json.writeValueAsString(requests.get(1).history())).contains("VALIDATION_REJECTION");
  }

  static class Read {
    int calls;
    @Tool(name = "read", description = "test query")
    public DecisionObservation read(ToolContext context) {
      calls++;
      return InterviewToolContext.from(context).observe("read", new ReadToolResult.Success(Map.of("question", "真实题目"),
          List.of(new DecisionObservation.AdoptableSource("question:1", "question", "1", null))));
    }
  }

  private ChatResponse proposal(AgentDecision decision) {
    var args = new java.util.LinkedHashMap<String, Object>();
    args.put("workingMemory", decision.workingMemory());
    if (decision.action() instanceof AgentDecision.Ask ask) {
      args.put("targetId", ask.targetId());
      if (ask.sourceGapId() != null) args.put("sourceGapId", ask.sourceGapId());
      args.put("question", ask.question());
      return response(call("proposal", "propose_question", json.writeValueAsString(args)));
    }
    args.put("decisionSummary", ((AgentDecision.Finish) decision.action()).decisionSummary());
    return response(call("proposal", "propose_finish", json.writeValueAsString(args)));
  }

  private AssistantMessage.ToolCall call(String id, String name, String arguments) {
    return new AssistantMessage.ToolCall(id, "function", name, arguments);
  }

  private ChatResponse response(AssistantMessage.ToolCall... calls) {
    return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
        .toolCalls(List.of(calls)).build())));
  }

  private InterviewAgentLoop loop(InterviewDecisionModel model) {
    return new InterviewAgentLoop(model, new AgentDecisionValidator(new WorkingMemoryValidator()),
        () -> new ToolCallback[0], new DeadlineExecutor(), new AdaptiveAgentProperties());
  }

  private AgentDecision ask(String targetId, Long gapId, WorkingMemory memory) {
    return new AgentDecision(
        memory,
        new AgentDecision.Ask(
            targetId,
            gapId,
            new AgentDecision.QuestionDraft(
                "请具体说明并发更新时的冲突处理。",
                "验证候选人的并发边界理解",
                List.of(),
        QuestionType.TEXT, null, null)
        )
    );
  }

  private AgentDecision askWithSourceRef(String sourceRef) {
    return new AgentDecision(
        memory("target-1", 12L),
        new AgentDecision.Ask(
            "target-1",
            12L,
            new AgentDecision.QuestionDraft("请展开说明。", "验证细节", List.of(sourceRef), QuestionType.TEXT, null, null)
        )
    );
  }

  private WorkingMemory memory(String targetId, Long gapId) {
    return new WorkingMemory(
        null,
        new WorkingMemory.Focus(targetId, gapId, List.of()),
        new WorkingMemory.Deliberation(List.of(), "继续验证边界", List.of())
    );
  }

  private AgentContext context() {
    CapabilityTarget first = target(0, "target-0", DepthLevel.L2);
    CapabilityTarget second = target(1, "target-1", DepthLevel.L1);
    CoverageView coverage = new CoverageView(
        1,
        3,
        List.of(
            targetCoverage("target-0", first, List.of()),
            targetCoverage("target-1", second, List.of(12L))
        ),
        List.of(new CoverageView.OpenProbeGap(
            12L, 22L, "target-1", 1,new SourceQuote(SourceQuote.Source.ANSWER_TEXT, "并发更新", null), "缺少冲突处理"
        )),
        List.of()
    );
    return new AgentContext(
        new AgentContext.SessionWindow(
            new AgentContext.SessionIdentity(
                "session-1", "provider-1", new MemoryOwner("tenant-1", "candidate-1")),
            SessionMode.EVALUATION,
            4
        ),
        new AgentContext.Facts(coverage, List.of(sourceTurn()), List.of(), List.of()),
        memory("target-0", null)
    );
  }

  private AdaptiveInterviewTurn sourceTurn() {
    return new AdaptiveInterviewTurn(1, 1, "如何处理并发更新", "验证冲突", "并发更新", null, null, null,
        TurnProvenance.initial(), List.of(), AnswerProcessingStatus.COMPLETED, null,
        QuestionType.TEXT, null, null, null, null);
  }

  private CoverageView.TargetCoverage targetCoverage(
      String id,
      CapabilityTarget target,
      List<Long> gaps
  ) {
    return new CoverageView.TargetCoverage(id, target, 0, null, gaps, List.of());
  }

  private CapabilityTarget target(int order, String id, DepthLevel expected) {
    return new CapabilityTarget(
        new CapabilityTarget.Identity(order, id, "focus", new TopicKey("skill", id)),
        new CapabilityTarget.Budget(2, 2),
        new CapabilityTarget.Depth(expected, DepthLevel.L4),
        List.of()
    );
  }
}
