package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.tool.InterviewToolCallback;
import interview.guide.modules.interview.agent.adaptive.tool.InterviewToolContext;
import interview.guide.modules.interview.agent.adaptive.tool.ReadToolValidationException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.json.JsonMapper;

/** 唯一业务循环：一次模型响应、一次框架工具批次、最后才接受合法业务提案。 */
@Slf4j
public class InterviewAgentLoop {
  private final InterviewDecisionModel model;
  private final AgentDecisionValidator validator;
  private final ToolCallbackProvider queryTools;
  private final DeadlineExecutor deadlineExecutor;
  private final AdaptiveAgentProperties properties;
  private final ToolCallingManager manager;

  public InterviewAgentLoop(InterviewDecisionModel model, AgentDecisionValidator validator,
      ToolCallbackProvider queryTools, DeadlineExecutor deadlineExecutor,
      AdaptiveAgentProperties properties) {
    this.model = model;
    this.validator = validator;
    this.queryTools = queryTools;
    this.deadlineExecutor = deadlineExecutor;
    this.properties = properties;
    // 未注册名称只有拒绝 callback，绝不回落到全局 Spring Bean resolver。
    this.manager = ToolCallingManager.builder().toolCallbackResolver(name -> new InterviewToolCallback(new RejectedTool(name), true))
        .toolExecutionExceptionProcessor(exception -> { throw exception; }).build();
  }

  public AgentDecision run(AgentContext context, Duration timeout) {
    return run(context, List.of(), timeout);
  }

  public AgentDecision run(AgentContext context, List<DecisionObservation> initial, Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    try (var scope = new InterviewToolContext(context, deadline, properties.getMaxReadToolCalls())) {
      var proposals = new Proposals(scope, validator);
      var callbacks = new ArrayList<ToolCallback>();
      for (var callback : queryTools.getToolCallbacks()) {
        if (context.facts().allowedReadTools().contains(callback.getToolDefinition().name())) {
          callbacks.add(callback);
        }
      }
      for (var callback : ToolCallbacks.from(proposals)) {
        callbacks.add(new InterviewToolCallback(callback, false));
      }
      List<Message> history = new ArrayList<>();
      for (int step = 0; step < properties.getMaxDecisionSteps(); step++) {
        log.info("adaptive_decision_step sessionId={} step={}", context.session().identity().sessionId(), step);
        var visible = new ArrayList<>(initial);
        visible.addAll(scope.observations());
        var request = new DecisionModelContext(context, initial, history, callbacks);
        var response = deadlineExecutor.invoke(() -> model.decide(request), deadline, "Interview Agent 决策");
        scope.requireActive();
        if (response == null || response.getResult() == null) {
          throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "模型未返回有效响应");
        }
        if (!response.hasToolCalls()) {
          history.add(response.getResult().getOutput());
          history.add(new UserMessage("缺少最终提案。请通过已定义工具查询，或提交唯一的出题/结束提案。"));
          continue;
        }
        long count = response.getResult().getOutput().getToolCalls().stream()
            .filter(call -> Proposals.isProposal(call.name())).count();
        proposals.beginBatch(visible, count > 1);
        var options = ToolCallingChatOptions.builder().toolCallbacks(callbacks)
            .toolContext(scope.values()).build();
        var prompt = new Prompt(history, options);
        var result = deadlineExecutor.invoke(() -> manager.executeToolCalls(prompt, response),
            deadline, "Interview Agent 工具执行");
        scope.requireActive();
        history = new ArrayList<>(result.conversationHistory());
        if (proposals.accepted != null) return proposals.accepted;
      }
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "本轮模型决策次数已达上限，请重试");
    }
  }

  /** 每次 run 独占。仅登记候选提案，不触碰持久化/SSE。 */
  static final class Proposals {
    private final InterviewToolContext scope;
    private final AgentDecisionValidator validator;
    private List<DecisionObservation> visible = List.of();
    private boolean conflict;
    private AgentDecision accepted;

    Proposals(InterviewToolContext scope, AgentDecisionValidator validator) {
      this.scope = scope;
      this.validator = validator;
    }

    static boolean isProposal(String name) {
      return "propose_question".equals(name) || "propose_finish".equals(name);
    }

    void beginBatch(List<DecisionObservation> visible, boolean conflict) {
      this.visible = List.copyOf(visible);
      this.conflict = conflict;
      this.accepted = null;
    }

    @Tool(name = "propose_question", description = "提交唯一下一题建议及完整 WorkingMemory。仅请求内校验，不代表已发布。采用来源必须在之前已见的成功查询或正式快照中。")
    public Map<String, String> question(
        @ToolParam(description = "当前 Plan 目标 ID") String targetId,
        @ToolParam(description = "可选的本场开放 Gap ID", required = false) Long sourceGapId,
        @ToolParam(description = "完整题面、采用来源及代码任务（如适用）") AgentDecision.QuestionDraft question,
        @ToolParam(description = "完整工作记忆快照") WorkingMemory workingMemory) {
      return accept(new AgentDecision(workingMemory, new AgentDecision.Ask(targetId, sourceGapId, question)));
    }

    @Tool(name = "propose_finish", description = "提交唯一结束建议及完整 WorkingMemory。仅请求内校验，不直接结束正式会话。")
    public Map<String, String> finish(
        @ToolParam(description = "简短业务结束理由") String decisionSummary,
        @ToolParam(description = "完整工作记忆快照") WorkingMemory workingMemory) {
      return accept(new AgentDecision(workingMemory, new AgentDecision.Finish(decisionSummary)));
    }

    private Map<String, String> accept(AgentDecision decision) {
      synchronized (scope) {
        scope.requireActive();
        if (conflict) throw new ReadToolValidationException("proposal", "同一响应包含多个最终提案，请只提交一个");
        var rejection = validator.validateMemory(decision, scope.context(), visible);
        if (rejection.isEmpty()) rejection = validator.validateAction(decision, scope.context(), visible);
        if (rejection.isPresent()) {
          var reason = rejection.orElseThrow();
          throw new ReadToolValidationException(reason.field(), reason.message());
        }
        accepted = decision;
        return Map.of("status", "VALID_PROPOSAL", "message", "提案校验通过，等待本批执行完成及正式提交");
      }
    }
  }

  /** 将未知调用作为原生工具错误返回，由框架保留原 call ID。 */
  private record RejectedTool(String name) implements ToolCallback {
    @Override
    public ToolDefinition getToolDefinition() {
      return ToolDefinition.builder().name(name).description("不可用工具").inputSchema("{\"type\":\"object\"}").build();
    }
    @Override
    public String call(String input) { throw new IllegalStateException("缺少可信 ToolContext"); }
    @Override
    public String call(String input, ToolContext context) {
      var scope = InterviewToolContext.from(context);
      return new JsonMapper().writeValueAsString(scope.reject(name, "toolName", "工具未注册或不在当前会话白名单中"));
    }
  }
}
