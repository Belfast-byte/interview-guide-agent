package interview.guide.modules.interview.agent.adaptive.tool;

import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionBankSearchToolTest {

  @Test
  @DisplayName("超大结果集的结果 ID 仍是定长摘要，且同一集合不因顺序变化而改变")
  void shouldUseBoundedDigestResultIdForLargeResultSets() {
    QuestionBankSearchSource searchSource = mock(QuestionBankSearchSource.class);
    QuestionBankSearchTool tool = new QuestionBankSearchTool(searchSource);
    List<QuestionBankQuestion> questions = LongStream.rangeClosed(100_000, 100_099)
        .mapToObj(id -> new QuestionBankQuestion(
            "stable-" + id,
            id,
            "Java 后端",
            "EASY",
            "什么是缓存穿透？"
        ))
        .toList();
    when(searchSource.search("缓存", null)).thenReturn(questions);
    when(searchSource.search("缓存逆序", null)).thenReturn(questions.reversed());

    String resultId = tool.execute(Map.of("query", "缓存")).resultId();
    String reversedResultId = tool.execute(Map.of("query", "缓存逆序")).resultId();

    assertThat(resultId)
        .startsWith("question-search:")
        .hasSize("question-search:".length() + 64);
    assertThat(reversedResultId).isEqualTo(resultId);
  }
}
