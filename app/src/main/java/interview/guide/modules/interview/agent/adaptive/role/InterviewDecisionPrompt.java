package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.common.ai.PromptLoader;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionModelContext;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.tool.ReferenceSearchTool;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** 新 Agent Loop 的 Prompt 与结构化输出契约。 */
@Component
@Slf4j
class InterviewDecisionPrompt {

  private final ObjectMapper objectMapper;
  private final PromptTemplate systemTemplate;
  private final PromptTemplate userTemplate;
  private final BeanOutputConverter<InterviewDecisionOutput> outputConverter;
  private final AdaptiveInputTokenBudget budget;

  InterviewDecisionPrompt(
      ObjectMapper objectMapper,
      PromptLoader promptLoader,
      AdaptiveAgentProperties properties,
      AdaptiveInputTokenBudget budget
  ) {
    this.objectMapper = objectMapper;
    this.budget = budget;
    this.systemTemplate = promptLoader.loadTemplate(
        properties.getDecisionSystemPromptPath());
    this.userTemplate = promptLoader.loadTemplate(
        properties.getDecisionUserPromptPath());
    this.outputConverter = interview.guide.common.ai.StructuredOutputInvoker.strictConverter(InterviewDecisionOutput.class);
  }

  PreparedPrompt prepare(DecisionModelContext context) {
    String system = systemTemplate.render() + "\n\n" + outputConverter.getFormat();
    ObjectNode projected = objectMapper.valueToTree(DecisionContextProjection.project(context));
    List<ObjectNode> references = referenceResults(projected);
    String user = render(projected);
    // 只移除可选参考的完整片段，给协议提示留余量；不能裁剪当前答案、源码或正式量规。
    int target = Math.max(0, budget.limit() - 256);
    while (budget.estimate(system + "\n" + user) > target && removeLastReference(references)) {
      user = render(projected);
    }
    var facts = projected.path("agentContext").path("facts");
    log.info("adaptive_decision_context sessionId={} turn={} observations={} skillTokens={} historyTokens={} coverageTokens={} toolTokens={}",
        context.agentContext().session().identity().sessionId(),
        context.agentContext().facts().recentTurns().stream().mapToInt(t -> t.turnIndex()).max().orElse(0),
        context.observations().size(), tokens(facts.path("skillGuidance")), tokens(facts.path("recentTurns")),
        tokens(facts.path("coverage")), tokens(projected.path("observations")));
    return new PreparedPrompt(system, user, outputConverter);
  }

  private String render(ObjectNode context) {
    try {
      return userTemplate.render(Map.of("contextJson", objectMapper.writeValueAsString(context)));
    } catch (JacksonException e) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AgentContext 序列化失败", e);
    }
  }

  private int tokens(JsonNode node) { return budget.estimate(node.toString()); }

  private List<ObjectNode> referenceResults(ObjectNode context) {
    var results = new ArrayList<ObjectNode>();
    var seen = new HashSet<String>();
    for (JsonNode observation : context.path("observations")) {
      if (!ReferenceSearchTool.NAME.equals(observation.path("toolName").asText())
          || !"TOOL_SUCCESS".equals(observation.path("kind").asText())) continue;
      ObjectNode data = (ObjectNode) observation.path("data");
      if (!(data.path("hits") instanceof ArrayNode hits)) continue;
      results.add(data);
      for (int i = 0; i < hits.size();) {
        if (seen.add(hits.get(i).path("chunkId").asText())) { i++; continue; }
        hits.remove(i);
        data.put("deduplicated", data.path("deduplicated").asInt(0) + 1);
        data.put("message", "重复参考正文已省略，请使用本次已有片段；不代表没有命中");
      }
    }
    return results;
  }

  private boolean removeLastReference(List<ObjectNode> results) {
    for (int i = results.size() - 1; i >= 0; i--) {
      var data = results.get(i);
      var hits = (ArrayNode) data.get("hits");
      if (hits.isEmpty()) continue;
      hits.remove(hits.size() - 1);
      data.put("budgetOmitted", data.path("budgetOmitted").asInt(0) + 1);
      data.put("message", "部分参考因本轮输入预算未展示；不要继续扩展参考，依据已有充分事实决策，不足时明确说明");
      return true;
    }
    return false;
  }

  record PreparedPrompt(
      String system,
      String user,
      BeanOutputConverter<InterviewDecisionOutput> converter
  ) {}
}
