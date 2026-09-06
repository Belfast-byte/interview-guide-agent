package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.knowledgebase.repository.KnowledgeBaseQuestionRepository;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RubricSnapshotResolverTest {
  @Test void publishedSnapshotSurvivesSourceChangesAndRejectsNewStaleAdoption() {
    var repo=mock(KnowledgeBaseQuestionRepository.class);
    var question=new KnowledgeBaseQuestionEntity();
    question.setStatus(KnowledgeBaseQuestionStatus.ACTIVE);
    question.setScoringRubric("  original\r\nbody  ");
    when(repo.findById(1L)).thenReturn(Optional.of(question));
    var resolver=new RubricSnapshotResolver(repo);
    String ref="rubric:question:1:rubric@"+RubricSnapshotResolver.version("original\nbody");
    var snapshot=resolver.resolve(List.of(ref));
    question.setScoringRubric("changed");
    var converter=new AdoptedRubricsJsonConverter();
    assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(snapshot)))
        .isEqualTo(snapshot);
    assertThat(snapshot.getFirst().body()).isEqualTo("original\nbody");
    assertThatThrownBy(() -> resolver.resolve(List.of(ref))).isInstanceOf(IllegalStateException.class);
    question.setStatus(KnowledgeBaseQuestionStatus.DRAFT);
    assertThatThrownBy(() -> resolver.resolve(List.of(ref))).isInstanceOf(IllegalStateException.class);
  }
  @Test void legacyReferenceDeserializesWithoutInventingBody() {
    var sources=new AdoptedRubricsJsonConverter().convertToEntityAttribute(
        "[{\"reference\":\"rubric:question:1:rubric@abc\",\"entryId\":\"question:1:rubric\",\"version\":\"abc\"}]");
    assertThat(sources.getFirst().body()).isNull();
  }
}
