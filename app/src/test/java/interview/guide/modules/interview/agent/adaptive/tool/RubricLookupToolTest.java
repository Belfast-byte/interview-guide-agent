package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseQuestionRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RubricLookupToolTest {

  @Test
  @DisplayName("超大结果集的结果 ID 仍是定长摘要，不因顺序变化而改变")
  void shouldUseBoundedDigestResultIdForLargeResultSets() {
    KnowledgeBaseQuestionRepository questionRepository = mock(KnowledgeBaseQuestionRepository.class);
    RubricLookupTool tool = new RubricLookupTool(questionRepository, new ToolProperties());
    List<KnowledgeBaseQuestionEntity> rubrics = LongStream.rangeClosed(1, 50)
        .mapToObj(this::rubricQuestion)
        .toList();
    when(questionRepository.findRubricsByDimension(
            eq(KnowledgeBaseQuestionStatus.ACTIVE),
            eq("Java 后端"),
            any()
        ))
        .thenReturn(rubrics)
        .thenReturn(rubrics.reversed());

    String resultId = tool.execute(Map.of("dimension", "Java 后端")).resultId();
    String reversedResultId = tool.execute(Map.of("dimension", "Java 后端")).resultId();

    assertThat(resultId)
        .startsWith("rubric-search:")
        .hasSize("rubric-search:".length() + 64);
    assertThat(reversedResultId).isEqualTo(resultId);
  }

  private KnowledgeBaseQuestionEntity rubricQuestion(long id) {
    KnowledgeBaseQuestionEntity question = mock(KnowledgeBaseQuestionEntity.class);
    when(question.getId()).thenReturn(id);
    when(question.getCategory()).thenReturn("Java 后端");
    when(question.getScoringRubric()).thenReturn("能说明缓存穿透的成因与防护");
    return question;
  }
}
