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
      return requireText(finish.decisionSummary(), "decisionSummary");
    }
    if (decision.action() instanceof AgentDecision.Ask ask) {
      return validateAsk(ask, context, observations);
    }
    return rejection("action", "必须提交问题或结束提案");
  }

  private Optional<DecisionObservation> validateAsk(
      AgentDecision.Ask ask,
      AgentContext context,
      List<DecisionObservation> observations
  ) {
    Optional<DecisionObservation> target = validateTarget(ask, context.facts().coverage());
    if (target.isPresent()) {
      return target;
    }
    if (ask.question() == null) {
      return rejection("question", "字段不能为空");
    }
    Optional<DecisionObservation> content = requireText(
        ask.question().content(), "question.content");
    if (content.isPresent()) {
      return content;
    }
    Optional<DecisionObservation> summary = requireText(
        ask.question().decisionSummary(), "question.decisionSummary");
    if (summary.isPresent()) {
      return summary;
    }
    try {
      CodeQuestionValidator.validate(ask, context);
    } catch (IllegalArgumentException e) {
      return rejection("question", e.getMessage());
    }
    return validateAdoptedSources(ask.question().adoptedSourceRefs(), context, observations);
  }

  private Optional<DecisionObservation> validateAdoptedSources(
      List<String> adoptedRefs,
      AgentContext context,
      List<DecisionObservation> observations
  ) {
    if (adoptedRefs == null) {
      return rejection("question.adoptedSourceRefs", "字段不能为空");
    }
    Set<String> available = observations.stream()
        .filter(observation -> observation.kind() == DecisionObservation.Kind.TOOL_SUCCESS)
        .flatMap(observation -> observation.adoptableSources().stream())
        .map(DecisionObservation.AdoptableSource::reference)
        .collect(Collectors.toSet());
    context.workingMemory().deliberation().adoptedObservationRefs().stream()
        .filter(this::stableSource).forEach(available::add);
    return available.containsAll(adoptedRefs)
        ? Optional.empty()
        : rejection("question.adoptedSourceRefs", "引用不在成功工具结果或已保存的采用来源中");
  }

  private Optional<DecisionObservation> validateTarget(
      AgentDecision.Ask ask,
      CoverageView coverage
  ) {
    boolean targetExists = coverage.targets().stream()
        .anyMatch(target -> target.targetId().equals(ask.targetId()));
    if (!targetExists) {
      return rejection("targetId", "Target 不属于当前 Plan");
    }
    if (ask.sourceGapId() == null) {
      return Optional.empty();
    }
    boolean gapMatches = coverage.openProbeGaps().stream()
        .anyMatch(gap -> gap.gapId() == ask.sourceGapId()
            && gap.targetId().equals(ask.targetId()));
    return gapMatches
        ? Optional.empty()
        : rejection("sourceGapId", "Gap 不属于所选 Target 的开放事实");
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
        memoryReferences(context, observations)
    );
  }

  private Set<String> memoryReferences(AgentContext context, List<DecisionObservation> observations) {
    var references = observations.stream().map(DecisionObservation::reference)
        .collect(Collectors.toSet());
    observations.stream().filter(item -> item.kind() == DecisionObservation.Kind.TOOL_SUCCESS)
        .flatMap(item -> item.adoptableSources().stream())
        .filter(source -> stableSource(source.reference()))
        .map(DecisionObservation.AdoptableSource::reference).forEach(references::add);
    // 上游按归属读取的首题来源或已提交快照可信，不从待校验的模型提案建立引用白名单。
    context.workingMemory().deliberation().adoptedObservationRefs().stream()
        .filter(this::stableSource).forEach(references::add);
    return references;
  }

  private boolean stableSource(String ref) {
    return ref.startsWith("episode:") || ref.startsWith("question:") || ref.startsWith("rubric:");
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
