package interview.guide.modules.interview.agent.adaptive.tool;

/** 由模型按需调用的同步只读工具。 */
public interface ReadOnlyAgentTool {

  String name();

  void validate(ReadToolRequest request);

  ReadToolResult execute(ReadToolRequest request);
}
