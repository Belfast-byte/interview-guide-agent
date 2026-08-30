package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemoryReferences;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemoryValidator;
import java.util.List;
import java.util.Map;
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
      AgentContext context,
      List<DecisionObservation> observations
  ) {
    if (decision == null || decision.workingMemory() == null) {
      return rejection("workingMemory", "必须返回完整 WorkingMemory");
    }
    try {
      memoryValidator.validate(decision.workingMemory(), references(context, observations));
      return Optional.empty();
    } catch (BusinessException e) {
      return rejection("workingMemory", e.getMessage());
    }
  }

  public Optional<DecisionObservation> validateAction(
      AgentDecision decision,
      AgentContext context,
      List<DecisionObservation> observations
  ) {
    if (decision.action() instanceof AgentDecision.Finish finish) {
      return requireText(finish.decisionSummary(), "action.decisionSummary");
    }
    if (decision.action() instanceof AgentDecision.CallReadTools call) {
      return validateCalls(call);
    }
    if (decision.action() instanceof AgentDecision.Ask ask) {
      return validateAsk(ask, context.facts().coverage(), observations);
    }
    return rejection("action", "必须返回 ASK、CALL_READ_TOOLS 或 FINISH");
  }

  private Optional<DecisionObservation> validateCalls(AgentDecision.CallReadTools action) {
    if (action.calls() == null || action.calls().isEmpty()) {
      return rejection("action.callReadTools.calls", "至少需要一个只读工具调用");
    }
    for (int index = 0; index < action.calls().size(); index++) {
      ReadToolCall call = action.calls().get(index);
      String field = "action.callReadTools.calls[" + index + "]";
      if (call == null) {
        return rejection(field, "调用不能为空");
      }
      Optional<DecisionObservation> name = requireText(call.toolName(), field + ".toolName");
      if (name.isPresent()) {
        return name;
      }
      Optional<DecisionObservation> reason = requireText(call.reason(), field + ".reason");
      if (reason.isPresent()) {
        return reason;
      }
      if (call.arguments() == null) {
        return rejection(field + ".arguments", "字段不能为空");
      }
    }
    return Optional.empty();
  }

  private Optional<DecisionObservation> validateAsk(
      AgentDecision.Ask ask,
      CoverageView coverage,
      List<DecisionObservation> observations
  ) {
    Optional<DecisionObservation> target = validateTarget(ask, coverage);
    if (target.isPresent()) {
      return target;
    }
    if (ask.question() == null) {
      return rejection("action.ask.question", "字段不能为空");
    }
    Optional<DecisionObservation> content = requireText(
        ask.question().content(), "action.ask.question.content");
    if (content.isPresent()) {
      return content;
    }
    Optional<DecisionObservation> summary = requireText(
        ask.question().decisionSummary(), "action.ask.question.decisionSummary");
    if (summary.isPresent()) {
      return summary;
    }
    return validateAdoptedSources(ask.question().adoptedSourceRefs(), observations);
  }

  private Optional<DecisionObservation> validateAdoptedSources(
      List<String> adoptedRefs,
      List<DecisionObservation> observations
  ) {
    if (adoptedRefs == null) {
      return rejection("action.ask.question.adoptedSourceRefs", "字段不能为空");
    }
    Set<String> available = observations.stream()
        .filter(observation -> observation.kind() == DecisionObservation.Kind.TOOL_SUCCESS)
        .flatMap(observation -> observation.adoptableSources().stream())
        .map(DecisionObservation.AdoptableSource::reference)
        .collect(Collectors.toSet());
    return available.containsAll(adoptedRefs)
        ? Optional.empty()
        : rejection("action.ask.question.adoptedSourceRefs", "引用不在成功 Tool Observation 中");
  }

  private Optional<DecisionObservation> validateTarget(
      AgentDecision.Ask ask,
      CoverageView coverage
  ) {
    boolean targetExists = coverage.targets().stream()
        .anyMatch(target -> target.targetId().equals(ask.targetId()));
    if (!targetExists) {
      return rejection("action.ask.targetId", "Target 不属于当前 Plan");
    }
    if (ask.sourceGapId() == null) {
      return Optional.empty();
    }
    boolean gapMatches = coverage.openProbeGaps().stream()
        .anyMatch(gap -> gap.gapId() == ask.sourceGapId()
            && gap.targetId().equals(ask.targetId()));
    return gapMatches
        ? Optional.empty()
        : rejection("action.ask.sourceGapId", "Gap 不属于所选 Target 的开放事实");
  }

  private WorkingMemoryReferences references(
      AgentContext context,
      List<DecisionObservation> observations
  ) {
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
        observations.stream().map(DecisionObservation::reference).collect(Collectors.toSet())
    );
  }

  private Optional<DecisionObservation> requireText(String value, String field) {
    return value == null || value.isBlank()
        ? rejection(field, "字段不能为空")
        : Optional.empty();
  }

  private Optional<DecisionObservation> rejection(String field, String message) {
    return Optional.of(new DecisionObservation(
        "validation",
        DecisionObservation.Kind.VALIDATION_REJECTION,
        field,
        message,
        null,
        Map.of(),
        List.of()
    ));
  }
}
