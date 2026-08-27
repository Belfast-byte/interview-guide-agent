package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.TargetWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.TargetWorkStatus;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkBudget;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkBudgetType;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkPhase;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 面试计划值对象，包含维度列表、轮次预算和状态，并提供按轮次定位维度等行为。
 */
public record InterviewPlan(
    String sessionId,
    int maxTurns,
    List<PlannedDimension> dimensions
) {

  private static final int MAX_DIMENSIONS = 12;
  private static final int MAX_TURNS = AdaptiveInterviewSession.MAX_TURNS;
  private static final int MAX_DIMENSION_LENGTH = 100;
  private static final int MAX_FOCUS_LENGTH = 500;

  public InterviewPlan {
    dimensions = List.copyOf(dimensions);
  }

  public static InterviewPlan decide(
      String sessionId,
      PlanProposal proposal,
      InterviewSessionSettings settings
  ) {
    validate(proposal);
    validatePracticeScope(proposal, settings);
    InterviewLevelProfile profile = InterviewLevelProfile.forLevel(settings.candidateLevel());
    int maxTurns = Math.min(proposal.dimensions().size() * 2, MAX_TURNS);
    int[] allocatedTurns = new int[proposal.dimensions().size()];
    Arrays.fill(allocatedTurns, 1);
    int additionalTurns = maxTurns - allocatedTurns.length;
    while (additionalTurns > 0) {
      int selected = 0;
      int largestGap = Integer.MIN_VALUE;
      for (int index = 0; index < allocatedTurns.length; index++) {
        int gap = proposal.dimensions().get(index).suggestedTurns()
            - allocatedTurns[index];
        if (gap > largestGap) {
          selected = index;
          largestGap = gap;
        }
      }
      allocatedTurns[selected]++;
      additionalTurns--;
    }

    List<PlannedDimension> dimensions = new ArrayList<>();
    for (int index = 0; index < proposal.dimensions().size(); index++) {
      DimensionProposal proposed = proposal.dimensions().get(index);
      dimensions.add(new PlannedDimension(
          target(new TargetInput(index, proposed, allocatedTurns[index], profile))
      ));
    }
    return new InterviewPlan(sessionId, maxTurns, dimensions);
  }

  private static CapabilityTarget target(TargetInput input) {
    DimensionProposal proposed = input.proposed();
    List<String> tools = proposed.suggestedTools().stream().map(String::trim).toList();
    List<CapabilityTarget.EvidenceObjective> objectives = new ArrayList<>();
    objectives.add(new CapabilityTarget.EvidenceObjective(
        proposed.focus().trim(),
        CapabilityTarget.EvidenceMethod.CANDIDATE_ANSWER
    ));
    tools.forEach(tool -> objectives.add(new CapabilityTarget.EvidenceObjective(
        "通过 " + tool + " 获取事实",
        CapabilityTarget.EvidenceMethod.TOOL_FACT
    )));
    return new CapabilityTarget(
        new CapabilityTarget.Identity(
            input.order(),
            proposed.dimension().trim(),
            proposed.focus().trim(),
            new TopicKey(proposed.suggestedSkill().trim(), proposed.focusId().trim())
        ),
        new CapabilityTarget.Budget(
            proposed.suggestedTurns(),
            input.allocatedTurns(),
            input.profile().followUpBudget(),
            tools.size()
        ),
        input.profile().depth(),
        objectives,
        tools
    );
  }

  private record TargetInput(
      int order,
      DimensionProposal proposed,
      int allocatedTurns,
      InterviewLevelProfile profile
  ) {}

  public PlannedDimension dimension(int order) {
    return dimensions.stream()
        .filter(dimension -> dimension.order() == order)
        .findFirst()
        .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "面试维度不存在"));
  }

  /** Planner 产出的能力目标是初始化运行状态的唯一输入。 */
  public InterviewWorkState initialWorkState() {
    List<TargetWorkState> states = dimensions.stream()
        .map(this::initialTargetState)
        .toList();
    TargetWorkState active = states.getFirst().consume(WorkBudgetType.TURN);
    List<TargetWorkState> initialized = new ArrayList<>(states);
    initialized.set(0, active);
    return new InterviewWorkState(
        sessionId,
        1,
        WorkPhase.AWAITING_ANSWER,
        initialized,
        active.targetId(),
        active.target().identity().focus(),
        List.of(),
        List.of(),
        1,
        null,
        null
    );
  }

  private TargetWorkState initialTargetState(PlannedDimension dimension) {
    int order = dimension.order();
    return new TargetWorkState(
        "target-" + order,
        dimension.target(),
        new WorkBudget(
            dimension.allocatedTurns(),
            dimension.followUpBudget(),
            dimension.toolBudget()
        ),
        DepthLevel.L0,
        order == 0 ? TargetWorkStatus.ACTIVE : TargetWorkStatus.PENDING
    );
  }

  private static void validate(PlanProposal proposal) {
    if (proposal == null
        || proposal.dimensions().isEmpty()
        || proposal.dimensions().size() > MAX_DIMENSIONS) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "规划结果必须包含 1 到 12 个维度"
      );
    }

    Set<String> dimensionNames = new HashSet<>();
    Set<TopicKey> topicKeys = new HashSet<>();
    for (DimensionProposal dimension : proposal.dimensions()) {
      if (dimension.dimension() == null
          || dimension.dimension().isBlank()
          || dimension.focus() == null
          || dimension.focus().isBlank()
          || dimension.focusId() == null
          || dimension.focusId().isBlank()) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "规划维度和考察重点不能为空");
      }
      if (dimension.dimension().trim().length() > MAX_DIMENSION_LENGTH
          || dimension.focus().trim().length() > MAX_FOCUS_LENGTH) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "规划维度或考察重点超过长度限制");
      }
      if (dimension.suggestedTurns() < 1
          || dimension.suggestedTurns() > MAX_TURNS) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "规划建议轮次必须在 1 到 12 之间");
      }
      String normalizedName = dimension.dimension().trim().toLowerCase(Locale.ROOT);
      if (!dimensionNames.add(normalizedName)) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "规划结果包含重复维度");
      }
      if (dimension.suggestedTools().stream().anyMatch(String::isBlank)) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "建议工具标识不能为空");
      }
      if (dimension.suggestedTools().stream().anyMatch(tool -> !isValidIdentifier(tool))) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Invalid suggested tool identifier");
      }
      if (dimension.suggestedSkill() == null || dimension.suggestedSkill().isBlank()) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "建议 Skill 标识不能为空");
      }
      if (!isValidIdentifier(dimension.suggestedSkill())) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Invalid suggested skill identifier");
      }
      if (!isValidIdentifier(dimension.focusId().toLowerCase(Locale.ROOT))) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Invalid focus identifier");
      }
      TopicKey topicKey = new TopicKey(dimension.suggestedSkill(), dimension.focusId());
      if (!topicKeys.add(topicKey)) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "规划结果包含重复主题");
      }
    }
  }

  private static void validatePracticeScope(
      PlanProposal proposal,
      InterviewSessionSettings settings
  ) {
    if (settings.mode() != SessionMode.PRACTICE) {
      return;
    }
    boolean outsideScope = proposal.dimensions().stream()
        .map(dimension -> new TopicKey(dimension.suggestedSkill(), dimension.focusId()))
        .anyMatch(topic -> !settings.practiceScope().topics().contains(topic));
    if (outsideScope) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "练习计划包含范围外主题");
    }
  }

  private static boolean isValidIdentifier(String value) {
    return value.trim().matches("[a-z][a-z0-9_-]{0,63}");
  }
}
