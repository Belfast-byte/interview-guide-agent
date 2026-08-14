package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseQuestionRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

@Component("localQuestionBankSearchSource")
@RequiredArgsConstructor
class QuestionBankSemanticSearch implements QuestionBankSearchSource {

  private final VectorStore vectorStore;
  private final KnowledgeBaseQuestionRepository questionRepository;
  private final ToolProperties properties;

  @Override
  public List<QuestionBankQuestion> search(String query, String difficulty) {
    List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
        .query(query)
        .topK(properties.getQuestionBankLimit() * 3)
        .similarityThreshold(properties.getQuestionBankMinScore())
        .filterExpression("document_type == '" + QuestionBankVectorIndexer.DOCUMENT_TYPE + "'")
        .build());
    if (hits.isEmpty()) {
      return List.of();
    }
    List<Long> rankedIds = hits.stream()
        .map(document -> Long.parseLong(
            (String) document.getMetadata().get("question_id")
        ))
        .toList();
    Map<Long, KnowledgeBaseQuestionEntity> activeQuestions = questionRepository
        .findAllById(rankedIds)
        .stream()
        .filter(question -> question.getStatus() == KnowledgeBaseQuestionStatus.ACTIVE)
        .collect(Collectors.toMap(KnowledgeBaseQuestionEntity::getId, Function.identity()));
    return rankedIds.stream()
        .map(activeQuestions::get)
        .filter(question -> question != null)
        .filter(question -> difficulty == null || difficulty.equals(question.getDifficulty()))
        .limit(properties.getQuestionBankLimit())
        .map(this::toResult)
        .toList();
  }

  private QuestionBankQuestion toResult(KnowledgeBaseQuestionEntity question) {
    return new QuestionBankQuestion(
        "question:" + question.getId(),
        question.getId(),
        question.getCategory(),
        question.getDifficulty(),
        question.getQuestion()
    );
  }

}
