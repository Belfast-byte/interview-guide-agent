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
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** 事实投影、原生工具消息与输入总预算。 */
@Component
@Slf4j
class InterviewDecisionPrompt {

  private final ObjectMapper objectMapper;
  private final PromptTemplate systemTemplate;
  private final PromptTemplate userTemplate;
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
  }

  PreparedPrompt prepare(DecisionModelContext context) {
    String system = systemTemplate.render();
    ObjectNode projected = objectMapper.valueToTree(DecisionContextProjection.project(context));
    List<ObjectNode> references = referenceResults(projected);
    List<Message> history = new ArrayList<>(context.history());
    var nativeResults = new java.util.LinkedHashMap<String, ObjectNode>();
    for (Message message : history) {
      if (!(message instanceof ToolResponseMessage results)) continue;
      for (var result : results.getResponses()) {
        if (!ReferenceSearchTool.NAME.equals(result.name())) continue;
        var value = objectMapper.readTree(result.responseData());
        if (value instanceof ObjectNode observation
            && "TOOL_SUCCESS".equals(observation.path("kind").asText())
            && observation.path("data") instanceof ObjectNode data
            && data.path("hits") instanceof ArrayNode) {
          nativeResults.put(result.id(), observation);
          references.add(data);
        }
      }
    }
    deduplicate(references);
    history = referenceHistory(history, nativeResults);
    String schemas = objectMapper.writeValueAsString(context.tools().stream()
        .map(tool -> tool.getToolDefinition()).toList());
    String user = render(projected);
    // 只移除可选参考的完整片段，给协议提示留余量；不能裁剪当前答案、源码或正式量规。
    int target = Math.max(0, budget.limit() - 256);
    while (budget.estimate(system + "\n" + user + schemas + objectMapper.writeValueAsString(history)) > target
        && removeLastReference(references)) {
      user = render(projected);
      history = referenceHistory(history, nativeResults);
    }
    var facts = projected.path("agentContext").path("facts");
    log.info("adaptive_decision_context sessionId={} turn={} observations={} skillTokens={} historyTokens={} coverageTokens={} toolTokens={}",
        context.agentContext().session().identity().sessionId(),
        context.agentContext().facts().recentTurns().stream().mapToInt(t -> t.turnIndex()).max().orElse(0),
        context.observations().size(), tokens(facts.path("skillGuidance")), tokens(facts.path("recentTurns")),
        tokens(facts.path("coverage")), tokens(projected.path("observations")));
    return new PreparedPrompt(system, user, history, user + schemas + objectMapper.writeValueAsString(history));
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
    for (JsonNode observation : context.path("observations")) {
      if (ReferenceSearchTool.NAME.equals(observation.path("toolName").asText())
          && "TOOL_SUCCESS".equals(observation.path("kind").asText())
          && observation.path("data") instanceof ObjectNode data
          && data.path("hits") instanceof ArrayNode) results.add(data);
    }
    return results;
  }

  private void deduplicate(List<ObjectNode> results) {
    var seen = new HashSet<String>();
    for (var data : results) {
      var hits = (ArrayNode) data.path("hits");
      for (int i = 0; i < hits.size();) {
        if (seen.add(hits.get(i).path("chunkId").asText())) { i++; continue; }
        hits.remove(i);
        data.put("deduplicated", data.path("deduplicated").asInt(0) + 1);
        data.put("message", "重复参考正文已省略，请使用本次已有片段；不代表没有命中");
      }
    }
  }

  private List<Message> referenceHistory(List<Message> history, Map<String, ObjectNode> results) {
    return history.stream().map(message -> {
      if (!(message instanceof ToolResponseMessage tools)) return message;
      return (Message) ToolResponseMessage.builder().metadata(tools.getMetadata())
          .responses(tools.getResponses().stream().map(result -> results.containsKey(result.id())
              ? new ToolResponseMessage.ToolResponse(result.id(), result.name(),
                  objectMapper.writeValueAsString(results.get(result.id()))) : result).toList()).build();
    }).toList();
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
      List<Message> history,
      String budgetInput
  ) {}
}
