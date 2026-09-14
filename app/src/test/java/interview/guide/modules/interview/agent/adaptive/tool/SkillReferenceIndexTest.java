package interview.guide.modules.interview.agent.adaptive.tool;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.interview.skill.InterviewSkillService.ReferenceResource;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

class SkillReferenceIndexTest {
  @Test
  void sharedResourceIsIndexedOnceAndKeepsTopicMembership() {
    var catalog = SkillReferenceIndex.catalog(List.of(resource("A", "# 标题\n专业原文"), resource("B", "# 标题\n专业原文")));
    assertThat(catalog.chunks()).hasSize(1);
    assertThat(catalog.sources().get(new TopicKey("skill", "A"))).containsExactly("classpath:shared.md");
    assertThat(catalog.sources().get(new TopicKey("skill", "B"))).containsExactly("classpath:shared.md");
    assertThat(catalog.chunks().values().iterator().next().text()).contains("专业原文");
  }

  @Test
  void syncUpdatesChangedTextDeletesRemovedChunksAndRejectsStaleHits() throws Exception {
    var skills = mock(InterviewSkillService.class);
    var vectors = mock(VectorStore.class);
    var jdbc = mock(JdbcTemplate.class);
    var stored = new LinkedHashMap<String, String>();
    doAnswer(call -> {
      RowCallbackHandler handler = call.getArgument(1);
      for (var entry : stored.entrySet()) {
        var row = mock(ResultSet.class);
        when(row.getString("id")).thenReturn(entry.getKey());
        when(row.getString("content")).thenReturn(entry.getValue());
        handler.processRow(row);
      }
      return null;
    }).when(jdbc).query(anyString(), any(RowCallbackHandler.class), eq(SkillReferenceIndex.DOCUMENT_TYPE));
    doAnswer(call -> {
      List<Document> documents = call.getArgument(0);
      documents.forEach(document -> stored.put(document.getId(), document.getText()));
      return null;
    }).when(vectors).add(anyList());
    doAnswer(call -> { ((List<String>) call.getArgument(0)).forEach(stored::remove); return null; })
        .when(vectors).delete(anyList());
    var index = new SkillReferenceIndex(skills, vectors, jdbc);
    when(skills.referenceResources()).thenReturn(List.of(resource("A", "# One\nold\n# Two\nremoved")));
    index.sync();
    assertThat(stored).hasSize(2);
    var old = stored.entrySet().iterator().next();
    Document stale = Document.builder().id(old.getKey()).text(old.getValue())
        .metadata(Map.of("document_type", SkillReferenceIndex.DOCUMENT_TYPE, "source_id", "classpath:shared.md")).build();
    clearInvocations(vectors);
    index.sync();
    verifyNoInteractions(vectors);
    when(skills.referenceResources()).thenReturn(List.of(resource("A", "# One\nupdated")));
    index.sync();
    assertThat(stored).hasSize(1);
    assertThat(stored.values().iterator().next()).contains("updated");
    assertThat(index.authoritative(stale, index.sources(new TopicKey("skill", "A")))).isNull();
    when(skills.referenceResources()).thenReturn(List.of());
    index.sync();
    assertThat(stored).isEmpty();
    assertThat(index.sources(new TopicKey("skill", "A"))).isEmpty();
  }

  @Test
  void failedEmbeddingLeavesIndexUnavailable() {
    var skills = mock(InterviewSkillService.class);
    when(skills.referenceResources()).thenReturn(List.of(resource("A", "# New\ntext")));
    var vectors = mock(VectorStore.class);
    doThrow(new IllegalStateException("embedding unavailable")).when(vectors).add(anyList());
    var index = new SkillReferenceIndex(skills, vectors, mock(JdbcTemplate.class));
    assertThatThrownBy(index::sync).hasMessage("embedding unavailable");
    assertThat(index.ready()).isFalse();
  }

  private ReferenceResource resource(String focus, String text) {
    return new ReferenceResource("skill", focus, "classpath:shared.md", text);
  }
}
