package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemoryReferences;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemoryValidator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 校验模型提案的业务引用，不替模型选择或改写动作。 */
@Component
public class AgentDecisionValidator {

  private final WorkingMemoryValidator memoryValidator;

  public AgentDecisionValidator(WorkingMemoryValidator memoryValidator) {
    this.memoryValidator = memoryValidator;
  }

  public Optional<DecisionObservation> validateMemory(
      AgentDecision decision,
      AgentContext context
  ) {
    if (decision == null || decision.workingMemory() == null) {
      return rejection("workingMemory", "必须返回完整 WorkingMemory");
    }
    try {
      memoryValidator.validate(decision.workingMemory(), references(context));
      return Optional.empty();
    } catch (BusinessException e) {
      return rejection("workingMemory", e.getMessage());
    }
  }

  public Optional<DecisionObservation> validateAction(
      AgentDecision decision,
      AgentContext context
  ) {
    if (decision.action() instanceof AgentDecision.Finish finish) {
      return requireText(finish.decisionSummary(), "action.decisionSummary");
    }
    if (!(decision.action() instanceof AgentDecision.Ask ask)) {
      return rejection("action", "必须返回 ASK 或 FINISH");
    }
    Optional<DecisionObservation> target = validateTarget(ask, context.facts().coverage());
    if (target.isPresent()) {
      return target;
    }
    if (ask.question() == null) {
      return rejection("action.question", "字段不能为空");
    }
    Optional<DecisionObservation> question = requireText(
        ask.question().content(), "action.question");
    if (question.isPresent()) {
      return question;
    }
    Optional<DecisionObservation> summary = requireText(
        ask.question().decisionSummary(), "action.decisionSummary");
    if (summary.isPresent()) {
      return summary;
    }
    if (ask.question().adoptedSourceRefs() == null) {
      return rejection("action.adoptedSourceRefs", "字段不能为空");
    }
    return ask.question().adoptedSourceRefs().isEmpty()
        ? Optional.empty()
        : rejection("action.adoptedSourceRefs", "引用不在当前 Observation 中");
  }

  private Optional<DecisionObservation> validateTarget(
      AgentDecision.Ask ask,
      CoverageView coverage
  ) {
    boolean targetExists = coverage.targets().stream()
        .anyMatch(target -> target.targetId().equals(ask.targetId()));
    if (!targetExists) {
      return rejection("action.targetId", "Target 不属于当前 Plan");
    }
    if (ask.sourceGapId() == null) {
      return Optional.empty();
    }
    boolean gapMatches = coverage.openProbeGaps().stream()
        .anyMatch(gap -> gap.gapId() == ask.sourceGapId()
            && gap.targetId().equals(ask.targetId()));
    return gapMatches
        ? Optional.empty()
        : rejection("action.sourceGapId", "Gap 不属于所选 Target 的开放事实");
  }

  private WorkingMemoryReferences references(AgentContext context) {
    CoverageView coverage = context.facts().coverage();
    return new WorkingMemoryReferences(
        new WorkingMemoryReferences.ContextIds(
            context.facts().recentTurns().stream()
                .map(turn -> turn.turnIndex()).collect(Collectors.toSet()),
            coverage.targets().stream()
                .map(CoverageView.TargetCoverage::targetId).collect(Collectors.toSet()),
            coverage.openProbeGaps().stream()
                .map(CoverageView.OpenProbeGap::gapId).collect(Collectors.toSet())
        ),
        Set.copyOf(coverage.evidenceIds()),
        Set.of()
    );
  }

  private Optional<DecisionObservation> requireText(String value, String field) {
    return value == null || value.isBlank()
        ? rejection(field, "字段不能为空")
        : Optional.empty();
  }

  private Optional<DecisionObservation> rejection(String field, String message) {
    return Optional.of(new DecisionObservation("INVALID_DECISION", field, message));
  }
}
