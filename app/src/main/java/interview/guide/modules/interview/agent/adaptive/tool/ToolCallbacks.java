package interview.guide.modules.interview.agent.adaptive.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

/**
 * Agent 工具回调工厂。
 */
final class ToolCallbacks {

  private ToolCallbacks() {}

  /**
   * 构建仅供模型侧声明 schema 的回调；真实执行必须走 ToolGateway，直接执行会快速失败。
   *
   * @param name 工具名称
   * @param description 工具描述
   * @param inputType 输入类型
   * @return 工具回调
   */
  static <I> ToolCallback gatewayOnly(String name, String description, Class<I> inputType) {
    return FunctionToolCallback
        .builder(name, (I input) -> unsupportedDirectCall())
        .description(description)
        .inputType(inputType)
        .build();
  }

  private static String unsupportedDirectCall() {
    throw new IllegalStateException("Tool execution must go through ToolGateway");
  }
}
