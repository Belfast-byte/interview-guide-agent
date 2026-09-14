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
