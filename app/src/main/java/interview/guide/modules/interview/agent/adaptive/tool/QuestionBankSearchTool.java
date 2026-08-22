package interview.guide.modules.interview.agent.adaptive.tool;

import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * 题库搜索工具，供 Agent 检索面试题目。
 */
@Component
public class QuestionBankSearchTool implements AdaptiveAgentTool {

  public static final String NAME = "question_bank_search";

  private final QuestionBankSearchSource searchSource;
  private final ToolCallback callback;

  public QuestionBankSearchTool(QuestionBankSearchSource searchSource) {
    this.searchSource = searchSource;
    this.callback = ToolCallbacks.gatewayOnly(
        NAME,
        "Semantically search active reviewed interview questions",
        QuestionSearchInput.class
    );
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
    return new CompletedToolResult(
        resultId,
        questions,
        "matchedQuestionIds=" + questions.stream().map(QuestionBankQuestion::id).toList()
    );
  }

  record QuestionSearchInput(String query, String difficulty) {}
}
