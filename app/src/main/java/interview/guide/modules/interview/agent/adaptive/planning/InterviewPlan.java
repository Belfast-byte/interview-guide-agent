package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record InterviewPlan(
    String sessionId,
    int maxTurns,
    List<PlannedDimension> dimensions
) {

  private static final int MAX_DIMENSIONS = 12;
  private static final int MAX_TURNS = 12;
  private static final int MAX_DIMENSION_LENGTH = 100;
  private static final int MAX_FOCUS_LENGTH = 500;

  public InterviewPlan {
    dimensions = List.copyOf(dimensions);
  }

  public static InterviewPlan decide(String sessionId, PlanProposal proposal) {
    validate(proposal);
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
          index,
          proposed.dimension().trim(),
          proposed.focus().trim(),
          proposed.focusId().trim(),
          proposed.suggestedTurns(),
          proposed.suggestedTools().stream().map(String::trim).toList(),
          proposed.suggestedSkill() == null ? null : proposed.suggestedSkill().trim(),
          allocatedTurns[index],
          0,
          index == 0 ? PlanDimensionStatus.IN_PROGRESS : PlanDimensionStatus.PENDING
      ));
    }
    return new InterviewPlan(sessionId, maxTurns, dimensions);
  }

  public PlannedDimension dimensionForTurn(int turnIndex) {
    int remaining = turnIndex;
    for (PlannedDimension dimension : dimensions) {
      if (remaining <= dimension.allocatedTurns()) {
        return dimension;
      }
      remaining -= dimension.allocatedTurns();
    }
    throw new BusinessException(ErrorCode.BAD_REQUEST, "面试轮次超出规划预算");
  }

  public InterviewPlan answer(int turnIndex) {
    PlannedDimension current = dimensionForTurn(turnIndex);
    if (current.status() != PlanDimensionStatus.IN_PROGRESS) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "当前规划维度不能接收回答");
    }

    List<PlannedDimension> updated = new ArrayList<>(dimensions);
    PlannedDimension answered = current.answer();
    updated.set(current.order(), answered);
    if (answered.status() == PlanDimensionStatus.COMPLETED
        && turnIndex < maxTurns) {
      updated.set(current.order() + 1, updated.get(current.order() + 1).start());
    }
    return new InterviewPlan(sessionId, maxTurns, updated);
  }

  public boolean isLastTurn(int turnIndex) {
    return turnIndex == maxTurns;
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
    }
  }

  private static boolean isValidIdentifier(String value) {
    return value.trim().matches("[a-z][a-z0-9_-]{0,63}");
  }
}
