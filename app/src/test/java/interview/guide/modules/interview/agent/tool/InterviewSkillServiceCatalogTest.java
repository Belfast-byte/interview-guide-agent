package interview.guide.modules.interview.agent.tool;

import interview.guide.modules.interview.agent.runtime.LoadedSkill;
import interview.guide.modules.interview.agent.runtime.SkillDescriptor;
import interview.guide.modules.interview.skill.InterviewSkillService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewSkillServiceCatalogTest {

  @Mock
  private InterviewSkillService interviewSkillService;

  @Test
  @DisplayName("初始目录只暴露描述，load 后才返回完整 Skill body 和 hash")
  void shouldProgressivelyLoadSkillBody() {
    InterviewSkillService.SkillDTO skill = new InterviewSkillService.SkillDTO(
        "java-backend",
        "Java 后端",
        "Java 后端岗位面试",
        List.of(),
        true,
        null,
        "完整 SKILL.md body",
        null
    );
    when(interviewSkillService.getAllSkills()).thenReturn(List.of(skill));
    when(interviewSkillService.getSkill("java-backend")).thenReturn(skill);
    InterviewSkillServiceCatalog catalog = new InterviewSkillServiceCatalog(
        interviewSkillService
    );

    List<SkillDescriptor> descriptors = catalog.listDescriptors();
    LoadedSkill loaded = catalog.load("java-backend");

    assertThat(descriptors).containsExactly(
        new SkillDescriptor("java-backend", "Java 后端", "Java 后端岗位面试")
    );
    assertThat(loaded.body()).isEqualTo("完整 SKILL.md body");
    assertThat(loaded.hash()).hasSize(64);
  }
}
