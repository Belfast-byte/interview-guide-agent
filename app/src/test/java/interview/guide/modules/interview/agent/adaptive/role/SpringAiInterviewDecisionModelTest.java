package interview.guide.modules.interview.agent.adaptive.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionModelContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;

@ExtendWith(MockitoExtension.class)
class SpringAiInterviewDecisionModelTest {

  @Mock
  private LlmProviderRegistry providerRegistry;
  @Mock
  private StructuredOutputInvoker outputInvoker;
  @Mock
  private InterviewDecisionPrompt prompt;
  @Mock
  private AdaptiveModelOptionsFactory modelOptionsFactory;
  @Mock
  private ChatClient baseClient;
  @Mock
  private ChatClient boundedClient;
  @Mock
  private ChatClient.Builder clientBuilder;
  @Mock
  private OpenAiChatOptions.Builder modelOptions;
  @Mock
  private BeanOutputConverter<InterviewDecisionOutput> converter;

  private SpringAiInterviewDecisionModel model;

  @BeforeEach
  void setUp() {
    model = new SpringAiInterviewDecisionModel(
        providerRegistry,
        outputInvoker,
        prompt,
        modelOptionsFactory
    );
  }

  @Test
  @DisplayName("决策调用应用 interviewer 模型预算")
  void shouldApplyInterviewerModelOptions() {
    DecisionModelContext context = context();
    InterviewDecisionPrompt.PreparedPrompt prepared =
        new InterviewDecisionPrompt.PreparedPrompt("system", "user", converter);
    InterviewDecisionOutput output = output();
    when(prompt.prepare(context)).thenReturn(prepared);
    when(providerRegistry.getPlainChatClient("provider-1")).thenReturn(baseClient);
    when(baseClient.mutate()).thenReturn(clientBuilder);
    when(modelOptionsFactory.interviewer(List.of())).thenReturn(modelOptions);
    when(clientBuilder.defaultOptions(modelOptions)).thenReturn(clientBuilder);
    when(clientBuilder.build()).thenReturn(boundedClient);
    when(outputInvoker.invokeOnce(
        eq(boundedClient), anyString(), anyString(), eq(converter), any(),
        anyString(), eq("interview_agent_decision"), any(Logger.class)
    )).thenReturn(output);

    AgentDecision decision = model.decide(context);

    assertThat(decision.action()).isInstanceOf(AgentDecision.Ask.class);
    verify(modelOptionsFactory).interviewer(List.of());
    verify(clientBuilder).defaultOptions(modelOptions);
  }

  private DecisionModelContext context() {
    AgentContext.SessionIdentity identity = new AgentContext.SessionIdentity(
        "session-1",
        "provider-1",
        new MemoryOwner(null, "candidate-1")
    );
    AgentContext agentContext = new AgentContext(
        new AgentContext.SessionWindow(identity, SessionMode.EVALUATION, 3),
        null,
        WorkingMemory.empty()
    );
    return new DecisionModelContext(agentContext, WorkingMemory.empty(), List.of());
  }

  private InterviewDecisionOutput output() {
    var question = new InterviewDecisionOutput.QuestionOutput(
        "请介绍一次并发冲突处理。",
        "验证并发处理能力",
        List.of()
    );
    var ask = new InterviewDecisionOutput.AskOutput("target-1", null, question);
    var action = new InterviewDecisionOutput.ActionOutput("ASK", ask, null, null);
    return new InterviewDecisionOutput(WorkingMemory.empty(), action);
  }
}
