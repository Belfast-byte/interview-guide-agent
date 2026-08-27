package interview.guide.modules.interview.agent.adaptive.planning;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.context.PlanningSkill;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.interview.skill.InterviewSkillService.SkillCategoryDTO;
import interview.guide.modules.interview.skill.InterviewSkillService.SkillDTO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanningTaxonomyTest {

  @Mock
  private InterviewSkillService skillService;

  private PlanningTaxonomy taxonomy;

  @BeforeEach
  void setUp() {
    taxonomy = new PlanningTaxonomy(skillService);
    when(skillService.getAllSkills()).thenReturn(List.of(new SkillDTO(
        "java-backend",
        "Java 后端",
        "",
        List.of(
            new SkillCategoryDTO("JAVA", "Java", "CORE", null, true),
            new SkillCategoryDTO("REDIS", "Redis", "CORE", null, true)
        ),
        true,
        null,
        "persona",
        null
    )));
  }

  @Test
  @DisplayName("规划目录只暴露本地稳定 Skill 与 focus ID")
  void shouldBuildStablePlanningCatalog() {
    assertThat(taxonomy.catalog()).containsExactly(
        new PlanningSkill("java-backend", List.of("JAVA", "REDIS"))
    );
  }

  @Test
  @DisplayName("本地目录存在的 Skill 与 focus 组合可以进入会话计划")
  void shouldAcceptKnownTopic() {
    taxonomy.validate(plan("java-backend", "REDIS"));
  }

  @Test
  @DisplayName("模型生成的未知 focus ID 在写入会话前被拒绝")
  void shouldRejectUnknownFocus() {
    assertThatThrownBy(() -> taxonomy.validate(plan("java-backend", "EXPERT")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("未知考察重点");
  }

  private InterviewPlan plan(String skillId, String focusId) {
    return testPlan("session-1", new PlanProposal(List.of(
        new DimensionProposal(
            "专业基础",
            "缓存一致性",
            focusId,
            2,
            List.of(),
            skillId
        )
    )));
  }
}
