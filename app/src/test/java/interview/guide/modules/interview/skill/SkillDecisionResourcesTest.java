package interview.guide.modules.interview.skill;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.PromptSanitizer;
import interview.guide.common.ai.StructuredOutputInvoker;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.ByteArrayResource;

class SkillDecisionResourcesTest {
  @Test
  void everyPresetHasShortDecisionInstructionsAndResolvableFullReferences() throws Exception {
    var service = service(new DefaultResourceLoader());
    service.loadPresetSkills();
    assertThat(service.getAllSkills()).isNotEmpty();
    for (var skill : service.getAllSkills()) {
      assertThat(service.decisionInstructions(skill.id())).isNotBlank().hasSizeLessThan(600);
    }
    var references = service.referenceResources();
    assertThat(references).isNotEmpty();
    assertThat(references).allSatisfy(reference -> {
      assertThat(reference.text()).isNotBlank().doesNotContain("单文件内容已截断");
      assertThat(reference.sourceId()).startsWith("classpath:skills/");
    });
    assertThat(service.buildEvaluationReferenceSection("java-backend"))
        .isNotEqualTo(service.decisionInstructions("java-backend"));
  }

  @Test
  void missingDecisionResourceDoesNotFallBackToEvaluationReference() throws Exception {
    var loader = spy(new DefaultResourceLoader());
    var service = service(loader);
    service.loadPresetSkills();
    doReturn(new ByteArrayResource(new byte[0])).when(loader)
        .getResource("classpath:skills/java-backend/decision.md");
    assertThatThrownBy(() -> service.decisionInstructions("java-backend"))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("资源为空");
  }

  private InterviewSkillService service(ResourceLoader loader) throws Exception {
    return new InterviewSkillService(mock(LlmProviderRegistry.class), mock(StructuredOutputInvoker.class),
        loader, mock(PromptSanitizer.class));
  }
}
