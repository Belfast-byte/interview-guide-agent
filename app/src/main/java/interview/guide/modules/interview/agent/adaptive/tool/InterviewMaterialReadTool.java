package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation;

@Component
@RequiredArgsConstructor
public class InterviewMaterialReadTool {
  private final AdaptiveAgentSessionRepository sessions;

  @Tool(name = "interview_material_read", description = "读取本场已保存的简历或 JD 原文。用于核对背景；结果不是候选人回答证据。")
  public DecisionObservation query(
      @ToolParam(description = "resume 或 jd") String source,
      ToolContext toolContext) {
    var scope = InterviewToolContext.from(toolContext);
    return scope.observe("interview_material_read", read(scope.context(), source));
  }

  ReadToolResult read(AgentContext context, String source) {
    if (!"resume".equals(source) && !"jd".equals(source)) {
      throw new ReadToolValidationException("source", "只接受 resume 或 jd");
    }
    var session = SessionReadBoundary.session(context, sessions);
    String text = source.equals("resume") ? session.resume() : session.jd();
    if (text == null || text.isBlank()) return new ReadToolResult.Empty(source + " 原文为空");
    return new ReadToolResult.Success(Map.of("source", source, "text", text), List.of());
  }
}
