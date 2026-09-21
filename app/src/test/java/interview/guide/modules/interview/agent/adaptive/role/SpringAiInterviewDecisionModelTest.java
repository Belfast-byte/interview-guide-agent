package interview.guide.modules.interview.agent.adaptive.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.PromptLoader;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.core.context.*;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.observability.*;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionModelContext;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.ObjectMapper;

class SpringAiInterviewDecisionModelTest {
  @Test
  void returnsFirstNativeResponseWithoutExecutingToolsOrAutoLooping() {
    var calls = new AtomicInteger();
    var received = new AtomicReference<Prompt>();
    var tool = new Probe();
    var expected = new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
        .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "probe", "{}"))).build())));
    ChatModel provider = new ChatModel() {
      @Override
      public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
        return org.springframework.ai.openai.OpenAiChatOptions.builder().build();
      }
      @Override
      public ChatResponse call(Prompt prompt) {
        calls.incrementAndGet();
        received.set(prompt);
        return expected;
      }
    };
    var registry = mock(LlmProviderRegistry.class);
    when(registry.getPlainChatClient("provider-1")).thenReturn(ChatClient.builder(provider).build());
    var properties = new AdaptiveAgentProperties();
    var budget = new AdaptiveInputTokenBudget(properties, mock(AdaptiveAgentTelemetry.class),
        new JTokkitTokenCountEstimator());
    var prompt = new InterviewDecisionPrompt(new ObjectMapper(),
        new PromptLoader(new DefaultResourceLoader()), properties, budget);
    var model = new SpringAiInterviewDecisionModel(registry, prompt, new AdaptiveModelOptionsFactory(properties), budget);
    var context = new AgentContext(new AgentContext.SessionWindow(new AgentContext.SessionIdentity(
        "session-1", "provider-1", new MemoryOwner(null, "candidate-1")), SessionMode.EVALUATION, 3),
        new AgentContext.Facts(new CoverageView(0, 3, List.of(), List.of(), List.of()),
            List.of(), List.of(), List.of("probe")), WorkingMemory.empty());

    var response = model.decide(new DecisionModelContext(context, List.of(), List.of(),
        List.of(ToolCallbacks.from(tool))));

    assertThat(response).isEqualTo(expected);
    assertThat(calls).hasValue(1);
    assertThat(tool.calls).isZero();
    assertThat(received.get().getOptions().getMaxTokens()).isEqualTo(properties.getInterviewerMaxOutputTokens());
    assertThat(((ToolCallingChatOptions) received.get().getOptions()).getToolCallbacks()).hasSize(1);
  }

  static class Probe {
    int calls;
    @Tool(description = "probe")
    public String probe() { calls++; return "result"; }
  }
}
