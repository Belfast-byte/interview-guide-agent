package interview.guide.modules.interview.agent.adaptive.mcp;

/**
 * MCP 题库失败原因枚举。
 */
public enum McpQuestionBankFailureReason {
  SERVER_NOT_FOUND,
  TIMEOUT,
  REMOTE_ERROR,
  MALFORMED_RESPONSE
}
