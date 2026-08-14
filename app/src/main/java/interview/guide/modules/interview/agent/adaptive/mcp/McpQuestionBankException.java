package interview.guide.modules.interview.agent.adaptive.mcp;

public class McpQuestionBankException extends RuntimeException {

  private final McpQuestionBankFailureReason reason;

  McpQuestionBankException(McpQuestionBankFailureReason reason, String message) {
    super(message);
    this.reason = reason;
  }

  McpQuestionBankException(
      McpQuestionBankFailureReason reason,
      String message,
      Throwable cause
  ) {
    super(message, cause);
    this.reason = reason;
  }

  public McpQuestionBankFailureReason reason() {
    return reason;
  }
}
