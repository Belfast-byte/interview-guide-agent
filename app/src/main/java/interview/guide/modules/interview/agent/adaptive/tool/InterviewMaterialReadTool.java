package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation;

@Component
@RequiredArgsConstructor
public class InterviewMaterialReadTool implements ReadOnlyAgentTool {
  private final AdaptiveAgentSessionRepository sessions;

  @Tool(name = "interview_material_read", description = "读取本场已保存的简历或 JD 原文。用于核对背景；结果不是候选人回答证据。")
  public DecisionObservation query(
      @ToolParam(description = "resume 或 jd") String source,
      ToolContext toolContext) {
    var scope = InterviewToolContext.from(toolContext);
    var arguments = new java.util.LinkedHashMap<String, Object>();
    arguments.put("source", source);
    var request = new ReadToolRequest(scope.context(), arguments, scope.deadlineNanos());
    validate(request);
    return scope.observe("interview_material_read", execute(request));
  }

  public String name() { return "interview_material_read"; }

  public void validate(ReadToolRequest request) {
    if (!request.arguments().keySet().equals(Set.of("source"))
        || !(request.arguments().get("source") instanceof String source)
        || !(source.equals("resume") || source.equals("jd"))) {
      throw new ReadToolValidationException("arguments.source", "只接受 source: resume 或 jd");
    }
  }

  public ReadToolResult execute(ReadToolRequest request) {
    var session = SessionReadBoundary.session(request, sessions);
    String source = (String) request.arguments().get("source");
    String text = source.equals("resume") ? session.resume() : session.jd();
    if (text == null || text.isBlank()) return new ReadToolResult.Empty(source + " 原文为空");
    return new ReadToolResult.Success(Map.of("source", source, "text", text), List.of());
  }
}
