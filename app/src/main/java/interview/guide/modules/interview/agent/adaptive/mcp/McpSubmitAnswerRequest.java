package interview.guide.modules.interview.agent.adaptive.mcp;

/** MCP 租户答题工具的不可变输入。 */
public record McpSubmitAnswerRequest(int turnIndex, String answer) {}
