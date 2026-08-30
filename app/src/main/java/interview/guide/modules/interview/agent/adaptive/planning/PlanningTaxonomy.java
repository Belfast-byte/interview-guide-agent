package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.interview.skill.InterviewSkillService.SkillDTO;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 规划分类校验器，负责校验计划维度、预算和分类是否符合平台约束。
 */
@Component
@RequiredArgsConstructor
public class PlanningTaxonomy {

  private final InterviewSkillService skillService;

  public List<PlanningSkill> catalog() {
    return skillService.getAllSkills().stream()
        .map(skill -> new PlanningSkill(
            skill.id(),
            skill.categories().stream().map(category -> category.key()).toList()
        ))
        .toList();
  }

  public void validate(InterviewPlan plan) {
    Map<String, SkillDTO> skills = skillService.getAllSkills().stream()
        .collect(Collectors.toMap(SkillDTO::id, Function.identity()));
    for (PlannedDimension dimension : plan.dimensions()) {
      SkillDTO skill = skills.get(dimension.suggestedSkill());
      if (skill == null) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "规划结果包含未知 Skill 标识");
      }
      boolean knownFocus = skill.categories().stream()
          .anyMatch(category -> category.key().equals(dimension.focusId()));
      if (!knownFocus) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "规划结果包含未知考察重点标识");
      }
    }
  }
}
