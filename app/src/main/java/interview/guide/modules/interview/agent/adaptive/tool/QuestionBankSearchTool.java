package interview.guide.modules.interview.agent.adaptive.tool;

import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

@Component
public class QuestionBankSearchTool implements AdaptiveAgentTool {

  public static final String NAME = "question_bank_search";

  private final QuestionBankSearchSource searchSource;
  private final ToolCallback callback;

  public QuestionBankSearchTool(QuestionBankSearchSource searchSource) {
    this.searchSource = searchSource;
    this.callback = FunctionToolCallback
        .builder(NAME, (QuestionSearchInput input) -> unsupportedDirectCall())
        .description("Semantically search active reviewed interview questions")
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
    List<QuestionBankQuestion> questions = searchSource.search(query, difficulty);
    String resultId = "question-search:" + questions.stream()
        .map(QuestionBankQuestion::id)
        .map(String::valueOf)
        .reduce((left, right) -> left + "," + right)
        .orElse("empty");
    return new ToolResult(
        resultId,
        questions,
        "matchedQuestionIds=" + questions.stream().map(QuestionBankQuestion::id).toList()
    );
  }

  private String unsupportedDirectCall() {
    throw new IllegalStateException("Tool execution must go through ToolGateway");
  }

  record QuestionSearchInput(String query, String difficulty) {}
}
