package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseQuestionRepository;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class QuestionBankSearchTool implements AdaptiveAgentTool {

  public static final String NAME = "question_bank_search";

  private final KnowledgeBaseQuestionRepository questionRepository;
  private final int limit;
  private final ToolCallback callback;

  public QuestionBankSearchTool(
      KnowledgeBaseQuestionRepository questionRepository,
      ToolProperties properties
  ) {
    this.questionRepository = questionRepository;
    this.limit = properties.getQuestionBankLimit();
    this.callback = FunctionToolCallback
        .builder(NAME, (QuestionSearchInput input) -> unsupportedDirectCall())
        .description("Search active reviewed interview questions by topic, category, or text")
        .inputType(QuestionSearchInput.class)
        .build();
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public ToolCallback callback() {
    return callback;
  }

  @Override
  public ToolResult execute(Map<String, Object> arguments) {
    String query = ToolArguments.requiredString(arguments, "query", 200);
    String difficulty = ToolArguments.optionalString(arguments, "difficulty", 16);
    List<QuestionPayload> questions = questionRepository.searchActiveQuestions(
            KnowledgeBaseQuestionStatus.ACTIVE,
            query,
            difficulty,
            PageRequest.of(0, limit)
        ).stream()
        .map(this::toPayload)
        .toList();
    String resultId = "question-search:" + questions.stream()
        .map(QuestionPayload::id)
        .map(String::valueOf)
        .reduce((left, right) -> left + "," + right)
        .orElse("empty");
    return new ToolResult(
        resultId,
        questions,
        "matchedQuestionIds=" + questions.stream().map(QuestionPayload::id).toList()
    );
  }

  private QuestionPayload toPayload(KnowledgeBaseQuestionEntity question) {
    return new QuestionPayload(
        "question:" + question.getId(),
        question.getId(),
        question.getCategory(),
        question.getDifficulty(),
        question.getQuestion()
    );
  }

  private String unsupportedDirectCall() {
    throw new IllegalStateException("Tool execution must go through ToolGateway");
  }

  record QuestionSearchInput(String query, String difficulty) {}

  record QuestionPayload(
      String stableId,
      Long id,
      String category,
      String difficulty,
      String question
  ) {}
}
