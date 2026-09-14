package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/** 在一次共享 deadline 内循环执行模型决策与请求级只读 Tool。 */
@Slf4j
public class InterviewAgentLoop {

  private final InterviewDecisionModel model;
  private final AgentDecisionValidator validator;
  private final ReadToolExecutor toolExecutor;
  private final DeadlineExecutor deadlineExecutor;
  private final AdaptiveAgentProperties properties;

  public InterviewAgentLoop(
      InterviewDecisionModel model,
      AgentDecisionValidator validator,
      ReadToolExecutor toolExecutor,
      DeadlineExecutor deadlineExecutor,
      AdaptiveAgentProperties properties
  ) {
    this.model = model;
    this.validator = validator;
    this.toolExecutor = toolExecutor;
    this.deadlineExecutor = deadlineExecutor;
    this.properties = properties;
  }

  public AgentDecision run(AgentContext context, Duration timeout) {
    return run(context, List.of(), timeout);
  }

  public AgentDecision run(
      AgentContext context,
      List<DecisionObservation> initialObservations,
      Duration timeout
  ) {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    List<DecisionObservation> observations = new ArrayList<>(initialObservations);
    WorkingMemory memory = context.workingMemory();
    int batchIndex = 0;
    int toolCalls = 0;
    Set<ToolRequestKey> executed = new HashSet<>();
    for (int step = 0; step < properties.getMaxDecisionSteps(); step++) {
      log.info("adaptive_decision_step sessionId={} turn={} step={}",
          context.session().identity().sessionId(),
          context.facts().recentTurns().stream().mapToInt(t -> t.turnIndex()).max().orElse(0), step);
      AgentDecision decision = decide(context, memory, observations, deadlineNanos);
      Optional<DecisionObservation> memoryRejection =
          validator.validateMemory(decision, context, observations);
      if (memoryRejection.isPresent()) {
        observations.add(numberedRejection(memoryRejection.orElseThrow(), observations.size()));
        continue;
      }
      memory = decision.workingMemory();
      Optional<DecisionObservation> actionRejection =
          validator.validateAction(decision, context, observations);
      if (actionRejection.isPresent()) {
        observations.add(numberedRejection(actionRejection.orElseThrow(), observations.size()));
        continue;
      }
      if (decision.action() instanceof AgentDecision.CallReadTools calls) {
        List<ReadToolCall> pending = new ArrayList<>();
        for (ReadToolCall call : calls.calls()) {
          if (!executed.add(new ToolRequestKey(call.toolName(), call.arguments()))) {
            observations.add(new DecisionObservation("duplicate-" + step + "-" + observations.size(),
                DecisionObservation.Kind.VALIDATION_REJECTION, "action.callReadTools.calls",
                "相同工具和参数已执行，请使用已有结果或调整请求，不要重复调用", call.toolName(), Map.of(), List.of()));
          } else {
            pending.add(call);
          }
        }
        toolCalls += pending.size();
        if (toolCalls > properties.getMaxReadToolCalls()) {
          throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "本轮工具调用次数已达上限，请重试");
        }
        if (!pending.isEmpty()) {
          observations.addAll(toolExecutor.execute(new ReadToolBatch(
              context, pending, deadlineNanos, batchIndex++)));
        }
        continue;
      }
      return decision;
    }
    throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "本轮模型决策次数已达上限，请重试");
  }

  private record ToolRequestKey(String name, Map<String, Object> arguments) {}

  private AgentDecision decide(
      AgentContext context,
      WorkingMemory memory,
      List<DecisionObservation> observations,
      long deadlineNanos
  ) {
    DecisionModelContext modelContext = new DecisionModelContext(
        new AgentContext(context.session(), context.facts(), memory), observations);
    return deadlineExecutor.invoke(
        () -> model.decide(modelContext),
        deadlineNanos,
        "Interview Agent 决策"
    );
  }

  private DecisionObservation numberedRejection(
      DecisionObservation rejection,
      int observationIndex
  ) {
    return new DecisionObservation(
        "validation-" + observationIndex,
        rejection.kind(),
        rejection.field(),
        rejection.message(),
        rejection.toolName(),
        rejection.data(),
        rejection.adoptableSources()
    );
  }
}
