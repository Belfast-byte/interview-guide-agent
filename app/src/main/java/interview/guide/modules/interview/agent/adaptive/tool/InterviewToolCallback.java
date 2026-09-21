package interview.guide.modules.interview.agent.adaptive.tool;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import interview.guide.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.metadata.ToolMetadata;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** 只补足框架未执行的 schema/资源边界；分派、参数绑定和消息转换均由 Spring AI 完成。 */
@Slf4j
public final class InterviewToolCallback implements ToolCallback {
  private static final JsonMapper JSON = JsonMapper.builder()
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
  private final ToolCallback delegate;
  private final Schema schema;
  private final boolean query;

  public InterviewToolCallback(ToolCallback delegate, boolean query) {
    this.delegate = delegate;
    this.query = query;
    this.schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
        .getSchema(JSON.readTree(delegate.getToolDefinition().inputSchema()));
  }

  @Override
  public ToolDefinition getToolDefinition() { return delegate.getToolDefinition(); }
  @Override
  public ToolMetadata getToolMetadata() { return delegate.getToolMetadata(); }
  @Override
  public String call(String input) { throw new IllegalStateException("必须提供可信 ToolContext"); }

  @Override
  public String call(String input, ToolContext context) {
    var scope = InterviewToolContext.from(context);
    String name = getToolDefinition().name();
    try {
      tools.jackson.databind.JsonNode arguments;
      try {
        arguments = JSON.readTree(input);
      } catch (JacksonException e) {
        if (query) scope.admitRead(name, JSON.getNodeFactory().textNode(input));
        throw new ReadToolValidationException("arguments", "参数必须是单个合法 JSON 对象");
      }
      if (query) scope.admitRead(name, arguments);
      if (arguments == null || !arguments.isObject() || !schema.validate(arguments).isEmpty()) {
        throw new ReadToolValidationException("arguments", "参数不符合工具定义，请检查未知字段、必填字段和类型");
      }
      String result = delegate.call(input, context);
      scope.requireActive();
      return result;
    } catch (ReadToolValidationException e) {
      return JSON.writeValueAsString(scope.reject(name, e.field(), e.getMessage()));
    } catch (ToolExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof tools.jackson.core.exc.InputCoercionException) {
        return JSON.writeValueAsString(scope.reject(name, "arguments", "参数无法绑定，请检查数值范围与类型"));
      }
      if (cause instanceof BusinessException business) throw business;
      if (cause instanceof ReadToolValidationException validation) {
        return JSON.writeValueAsString(scope.reject(name, validation.field(), validation.getMessage()));
      }
      log.error("面试工具执行失败: toolName={}", name, e);
      return JSON.writeValueAsString(scope.observe(name, new ReadToolResult.Error("只读工具执行失败")));
    }
  }
}
