package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 在一次共享 deadline 内循环执行模型决策与请求级只读 Tool。 */
public class InterviewAgentLoop {

  private final InterviewDecisionModel model;
  private final AgentDecisionValidator validator;
  private final ReadToolExecutor toolExecutor;
  private final DeadlineExecutor deadlineExecutor;

  public InterviewAgentLoop(
      InterviewDecisionModel model,
      AgentDecisionValidator validator,
      ReadToolExecutor toolExecutor,
      DeadlineExecutor deadlineExecutor
  ) {
    this.model = model;
    this.validator = validator;
    this.toolExecutor = toolExecutor;
    this.deadlineExecutor = deadlineExecutor;
  }

  public AgentDecision run(AgentContext context, Duration timeout) {
    return run(context, List.of(), timeout);
  }

  public AgentDecision run(
      AgentContext context,
      List<DecisionObservation> initialObservations,
      Duration timeout
  ) {
    RuntimeDeadline deadline = RuntimeDeadline.start(timeout);
    List<DecisionObservation> observations = new ArrayList<>(initialObservations);
    WorkingMemory memory = context.workingMemory();
    int batchIndex = 0;
    while (true) {
      AgentDecision decision = decide(context, memory, observations, deadline);
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
        observations.addAll(toolExecutor.execute(new ReadToolBatch(
            context, calls.calls(), deadline.deadlineNanos(), batchIndex++)));
        continue;
      }
      return decision;
    }
  }

  private AgentDecision decide(
      AgentContext context,
      WorkingMemory memory,
      List<DecisionObservation> observations,
      RuntimeDeadline deadline
  ) {
    DecisionModelContext modelContext = new DecisionModelContext(
        context, memory, List.copyOf(observations));
    return deadlineExecutor.invoke(
        () -> model.decide(modelContext),
        deadline.deadlineNanos(),
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
