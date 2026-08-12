package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseQuestionRepository;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class LocalQuestionToolsIntegrationTest {

  @Autowired
  private KnowledgeBaseRepository knowledgeBaseRepository;

  @Autowired
  private KnowledgeBaseQuestionRepository questionRepository;

  private ToolProperties properties;

  @BeforeEach
  void setUp() {
    properties = new ToolProperties();
    properties.setQuestionBankLimit(5);
  }

  @Test
  @DisplayName("题库工具只返回启用题并携带稳定题目 ID")
  void shouldReturnOnlyActiveQuestionsWithStableIds() {
    KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.save(knowledgeBase());
    KnowledgeBaseQuestionEntity active = question(
        knowledgeBase,
        "Redis 缓存雪崩如何治理？",
        KnowledgeBaseQuestionStatus.ACTIVE
    );
    KnowledgeBaseQuestionEntity draft = question(
        knowledgeBase,
        "Redis 缓存穿透如何治理？",
        KnowledgeBaseQuestionStatus.DRAFT
    );
    questionRepository.save(active);
    questionRepository.save(draft);
    QuestionBankSearchTool tool = new QuestionBankSearchTool(questionRepository, properties);

    ToolResult result = tool.execute(Map.of("query", "Redis"));

    assertThat(result.summary()).contains(active.getId().toString());
    assertThat(result.summary()).doesNotContain(draft.getId().toString());
    assertThat(result.value().toString()).contains("question:" + active.getId());
  }

  @Test
  @DisplayName("量规工具只按维度返回已审核题的局部量规")
  void shouldReturnReviewedRubricFragmentsByDimension() {
    KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.save(knowledgeBase());
    KnowledgeBaseQuestionEntity question = question(
        knowledgeBase,
        "如何设计高并发缓存？",
        KnowledgeBaseQuestionStatus.ACTIVE
    );
    question.setCategory("系统设计");
    question.setScoringRubric("回答应覆盖容量、失效和降级边界");
    questionRepository.save(question);
    RubricLookupTool tool = new RubricLookupTool(questionRepository, properties);

    ToolResult result = tool.execute(Map.of("dimension", "系统设计"));

    assertThat(result.value().toString())
        .contains("question:" + question.getId() + ":rubric")
        .contains("容量、失效和降级边界");
  }

  private KnowledgeBaseEntity knowledgeBase() {
    KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
    entity.setFileHash("a".repeat(64));
    entity.setName("审核题库");
    entity.setOriginalFilename("questions.md");
    return entity;
  }

  private KnowledgeBaseQuestionEntity question(
      KnowledgeBaseEntity knowledgeBase,
      String content,
      KnowledgeBaseQuestionStatus status
  ) {
    KnowledgeBaseQuestionEntity entity = new KnowledgeBaseQuestionEntity();
    entity.setKnowledgeBase(knowledgeBase);
    entity.setDifficulty("MEDIUM");
    entity.setCategory("Redis");
    entity.setQuestion(content);
    entity.setStatus(status);
    return entity;
  }
}
