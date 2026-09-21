package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation.AdoptableSource;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseQuestionRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation;

@Component
@RequiredArgsConstructor
public class QuestionSearchTool {
  private static final int CANDIDATE_MULTIPLIER = 3;
  private final VectorStore vectorStore;
  private final KnowledgeBaseQuestionRepository questions;
  private final ToolProperties properties;

  @Tool(name = "question_search", description = "检索 ACTIVE 题库题目，返回可采用的 question 来源。用于换场景出题。")
  public DecisionObservation query(
      @ToolParam(description = "非空检索内容") String query,
      @ToolParam(description = "可省略的难度过滤，提供时不能为空", required = false) String difficulty,
      ToolContext toolContext) {
    var scope = InterviewToolContext.from(toolContext);
    return scope.observe("question_search", read(query, difficulty));
  }

  ReadToolResult read(String query, String difficulty) {
    requireText(query, "query");
    if (difficulty != null) requireText(difficulty, "difficulty");
    var documents = vectorStore.similaritySearch(SearchRequest.builder()
        .query(query)
        .topK(properties.getQuestionBankLimit() * CANDIDATE_MULTIPLIER)
        .similarityThreshold(properties.getQuestionBankMinScore())
        .filterExpression("document_type == '" + QuestionBankVectorIndexer.DOCUMENT_TYPE + "'")
        .build());
    var ids = documents.stream().map(document -> Long.parseLong(
        (String) document.getMetadata().get("question_id"))).distinct().toList();
    var available = questions.findAllById(ids).stream().collect(Collectors.toMap(
        KnowledgeBaseQuestionEntity::getId, Function.identity()));
    var hits = ids.stream().map(available::get)
        .filter(question -> question != null && question.getStatus() == KnowledgeBaseQuestionStatus.ACTIVE)
        .filter(question -> difficulty == null || difficulty.equals(question.getDifficulty()))
        .limit(properties.getQuestionBankLimit()).map(this::hit).toList();
    if (hits.isEmpty()) return new ReadToolResult.Empty("没有可用的 ACTIVE 题库命中");
    return new ReadToolResult.Success(Map.of("hits", hits), hits.stream().map(hit ->
        new AdoptableSource("question:" + hit.questionId(), "question",
            Long.toString(hit.questionId()), null)).toList());
  }

  private Hit hit(KnowledgeBaseQuestionEntity question) {
    return new Hit(question.getId(), question.getQuestion(), question.getTopicSummary(),
        question.getCategory(), question.getDifficulty());
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new ReadToolValidationException("arguments." + field, "必须为非空字符串");
    }
  }

  record Hit(long questionId, String question, String topicSummary, String category, String difficulty) {}
}
