package interview.guide.modules.interview.agent.adaptive.role;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import interview.guide.common.ai.PromptLoader;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.core.context.*;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.observability.*;
import interview.guide.modules.interview.agent.adaptive.runtime.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.ObjectMapper;

class InterviewDecisionPromptBudgetTest {
  private final AdaptiveAgentProperties properties = new AdaptiveAgentProperties();
  private final AdaptiveInputTokenBudget budget = new AdaptiveInputTokenBudget(properties,
      mock(AdaptiveAgentTelemetry.class), new JTokkitTokenCountEstimator());
  private final InterviewDecisionPrompt prompt = new InterviewDecisionPrompt(new ObjectMapper(),
      new PromptLoader(new DefaultResourceLoader()), properties, budget);

  @Test
  void deduplicatesOnlyReferenceTextAndDoesNotMutateFactsOrToolResults() {
    var observation = reference("ONE", "参考原文");
    var context = context(List.of(observation, observation));
    String user = prompt.prepare(context).user();
    assertThat(user.split("参考原文", -1)).hasSize(2);
    assertThat(user).contains("deduplicated", "岗位短策略");
    assertThat(context.observations()).hasSize(2);
    assertThat(observation.data().toString()).contains("参考原文");
  }

  @Test
  void removesWholeOptionalChunksToFitActualPromptAndPreservesOtherTools() {
    var empty = prompt.prepare(context(List.of()));
    int base = budget.estimate(empty.system() + "\n" + empty.user());
    properties.setMaxInputTokens(base + 650);
    var other = new DecisionObservation("material", DecisionObservation.Kind.TOOL_SUCCESS, null, null,
        "interview_material_read", Map.of("text", "必要原文"), List.of());
    var prepared = prompt.prepare(context(List.of(reference("one", "专业参考片段".repeat(500)), other)));
    assertThat(prepared.user()).contains("budgetOmitted", "必要原文", "岗位短策略").doesNotContain("专业参考片段");
    assertThatCode(() -> budget.verify("interview_agent", prepared.system(), prepared.user())).doesNotThrowAnyException();
    properties.setMaxInputTokens(10);
    var impossible = prompt.prepare(context(List.of(other)));
    assertThat(impossible.user()).contains("必要原文");
    assertThatThrownBy(() -> budget.verify("interview_agent", impossible.system(), impossible.user()))
        .hasMessageContaining("输入超过");
  }

  @Test
  void nativeHistoryIsBudgetedOnceAndTrimmingPreservesCallResultPairs() {
    var json = new ObjectMapper();
    var value = reference("one", "NATIVE_REFERENCE_TEXT".repeat(800));
    var call = org.springframework.ai.chat.messages.AssistantMessage.builder().content("")
        .toolCalls(List.of(new org.springframework.ai.chat.messages.AssistantMessage.ToolCall(
            "call-one", "function", "reference_search", "{\"query\":\"background\"}"))).build();
    var result = org.springframework.ai.chat.messages.ToolResponseMessage.builder().responses(List.of(
        new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse("call-one", "reference_search",
            json.writeValueAsString(value)))).build();
    var original = context(List.of());
    var nativeContext = new DecisionModelContext(original.agentContext(), List.of(), List.of(call, result), List.of());
    var base = prompt.prepare(original);
    properties.setMaxInputTokens(budget.estimate(base.system() + base.user()) + 1000);
    var prepared = prompt.prepare(nativeContext);
    assertThat(prepared.user()).doesNotContain("NATIVE_REFERENCE_TEXT", "TOOL_SUCCESS");
    assertThat(prepared.budgetInput()).contains("budgetOmitted", "call-one").doesNotContain("NATIVE_REFERENCE_TEXT");
    assertThat(prepared.history()).hasSize(2);
    assertThat(prepared.history().getFirst()).isEqualTo(call);
    assertThat(((org.springframework.ai.chat.messages.ToolResponseMessage) prepared.history().getLast())
        .getResponses().getFirst().id()).isEqualTo("call-one");
    assertThat(result.getResponses().getFirst().responseData()).contains("NATIVE_REFERENCE_TEXT");
    assertThatCode(() -> budget.verify("interview_agent", prepared.system(), prepared.budgetInput())).doesNotThrowAnyException();
  }

  @Test
  void schemasAndToolArgumentsCountTowardTheSameInputLimit() {
    var original = context(List.of());
    var callback = org.springframework.ai.tool.function.FunctionToolCallback.builder("test_query", (String value) -> value)
        .description("SCHEMA_DESCRIPTION".repeat(1000)).inputType(String.class).build();
    var base = prompt.prepare(original);
    properties.setMaxInputTokens(budget.estimate(base.system() + base.user()) + 1000);
    var prepared = prompt.prepare(new DecisionModelContext(original.agentContext(), List.of(), List.of(), List.of(callback)));
    assertThat(prepared.budgetInput()).contains("SCHEMA_DESCRIPTION");
    assertThatThrownBy(() -> budget.verify("interview_agent", prepared.system(), prepared.budgetInput()))
        .hasMessageContaining("输入超过");
  }

  private DecisionObservation reference(String id, String text) {
    return new DecisionObservation("ref-" + id, DecisionObservation.Kind.TOOL_SUCCESS, null, null,
        "reference_search", Map.of("hits", List.of(Map.of("chunkId", id, "sourceId", "resource", "text", text))), List.of());
  }

  private DecisionModelContext context(List<DecisionObservation> observations) {
    return new DecisionModelContext(new AgentContext(new AgentContext.SessionWindow(
        new AgentContext.SessionIdentity("s", "p", new MemoryOwner(null, "u")), SessionMode.EVALUATION, 12),
        new AgentContext.Facts(new CoverageView(0, 12, List.of(), List.of(), List.of()), List.of(),
            List.of(new AgentContext.SkillGuidance("skill", "岗位短策略")), List.of("reference_search")), WorkingMemory.empty()), observations);
  }
}
