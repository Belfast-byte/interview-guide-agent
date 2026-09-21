package interview.guide.modules.interview.agent.adaptive.tool.nativeapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Pins the actual Spring AI version: the manager executes one batch, never calls a model. */
class SpringAiToolContractTest {
  @Test
  void executesInOrderWithTrustedContextAndPreservesNativeCallIds() {
    var calls = new ArrayList<String>();
    var callbacks = ToolCallbacks.from(new Queries(calls));
    var options = ToolCallingChatOptions.builder().toolCallbacks(callbacks)
        .toolContext(Map.of("owner", "server-owner")).build();
    var prompt = new Prompt(List.of(new UserMessage("query")), options);
    var assistant = AssistantMessage.builder().content("").toolCalls(List.of(
        new AssistantMessage.ToolCall("a", "function", "lookup", "{\"query\":\"first\"}"),
        new AssistantMessage.ToolCall("b", "function", "lookup", "{\"query\":\"second\"}")
    )).build();

    var result = ToolCallingManager.builder().toolCallbackResolver(name -> null).build()
        .executeToolCalls(prompt, new ChatResponse(List.of(new Generation(assistant))));

    assertThat(calls).containsExactly("server-owner:first", "server-owner:second");
    assertThat(result.returnDirect()).isFalse();
    assertThat(result.conversationHistory()).hasSize(3);
    assertThat(result.conversationHistory().get(1)).isEqualTo(assistant);
    var responses = (ToolResponseMessage) result.conversationHistory().get(2);
    assertThat(responses.getResponses()).extracting(ToolResponseMessage.ToolResponse::id)
        .containsExactly("a", "b");
    assertThat(callbacks[0].getToolDefinition().inputSchema()).contains("query")
        .doesNotContain("owner", "toolContext");
  }

  @Test
  void defaultBindingDoesNotEnforceThePublishedSchema() {
    var calls = new ArrayList<String>();
    var callback = ToolCallbacks.from(new Queries(calls))[0];
    callback.call("{\"query\":\"known\",\"unexpected\":true}",
        new ToolContext(Map.of("owner", "server-owner")));
    assertThat(calls).containsExactly("server-owner:known");
  }

  static class Queries {
    private final List<String> calls;
    Queries(List<String> calls) { this.calls = calls; }

    @Tool(name = "lookup", description = "Look up a query")
    String lookup(@ToolParam(description = "Query text") String query, ToolContext context) {
      String value = context.getContext().get("owner") + ":" + query;
      calls.add(value);
      return value;
    }
  }
}
