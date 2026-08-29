package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 在共享 deadline 内让模型修正非法提案，ASK/FINISH 时结束。 */
public class InterviewAgentLoop {

  private final InterviewDecisionModel model;
  private final AgentDecisionValidator validator;
  private final DeadlineExecutor deadlineExecutor;

  public InterviewAgentLoop(
      InterviewDecisionModel model,
      AgentDecisionValidator validator,
      DeadlineExecutor deadlineExecutor
  ) {
    this.model = model;
    this.validator = validator;
    this.deadlineExecutor = deadlineExecutor;
  }

  public AgentDecision run(AgentContext context, Duration timeout) {
    RuntimeDeadline deadline = RuntimeDeadline.start(timeout);
    List<DecisionObservation> observations = new ArrayList<>();
    WorkingMemory memory = context.workingMemory();
    while (true) {
      DecisionModelContext modelContext = new DecisionModelContext(
          context, memory, List.copyOf(observations));
      AgentDecision decision = deadlineExecutor.invoke(
          () -> model.decide(modelContext),
          deadline.deadlineNanos(),
          "Interview Agent 决策"
      );
      Optional<DecisionObservation> memoryRejection = validator.validateMemory(decision, context);
      if (memoryRejection.isPresent()) {
        observations.add(memoryRejection.orElseThrow());
        continue;
      }
      memory = decision.workingMemory();
      Optional<DecisionObservation> actionRejection = validator.validateAction(decision, context);
      if (actionRejection.isEmpty()) {
        return decision;
      }
      observations.add(actionRejection.orElseThrow());
    }
  }
}
