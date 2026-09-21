package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.core.action.CodeFactUsage;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation;

@Component
@RequiredArgsConstructor
public class AssessmentReadTool {
  private final AdaptiveAgentSessionRepository sessions;
  private final AdaptiveAgentTurnRepository turns;
  private final AdaptiveAgentAssessmentRepository assessments;
  private final AdaptiveAgentEvidenceRepository evidences;

  @Tool(name = "assessment_read", description = "读取本场指定轮次已提交的正式评估和证据。尚未提交返回空，不能读取其他会话。")
  public DecisionObservation query(
      @ToolParam(description = "本场正整数轮次") int turnIndex,
      ToolContext toolContext) {
    var scope = InterviewToolContext.from(toolContext);
    return scope.observe("assessment_read", read(scope.context(), turnIndex));
  }

  ReadToolResult read(AgentContext context, int turnIndex) {
    SessionReadBoundary.requireTurnIndex(turnIndex);
    var session = SessionReadBoundary.session(context, sessions);
    int index = turnIndex;
    var turn = turns.findBySessionIdAndTurnIndex(session.id(), index)
        .orElseThrow(() -> new ReadToolValidationException("arguments.turnIndex", "本场轮次不存在"));
    var assessment = assessments.findBySessionIdAndTurnIndex(session.id(), index);
    if (assessment.isEmpty()) return new ReadToolResult.Empty("该轮正式评估尚未提交");
    var fact = assessment.orElseThrow();
    var target = context.facts().coverage().targets().stream()
        .filter(item -> item.target().identity().order() == fact.dimensionOrder())
        .findFirst().orElseThrow(() -> new IllegalStateException("评估维度不属于本场计划"));
    var data = new LinkedHashMap<String, Object>();
    data.put("assessmentId", fact.id());
    data.put("turnIndex", fact.turnIndex());
    data.put("targetId", target.targetId());
    data.put("depthLevel", fact.depthLevel());
    data.put("rationaleSummary", fact.rationaleSummary());
    data.put("questionType", turn.codeRepair().questionType());
    if (turn.codeRepair().codeTaskTurnIndex() != null) {
      data.put("codeTaskTurnIndex", turn.codeRepair().codeTaskTurnIndex());
    }
    if (fact.codeReview() != null) data.put("codeReview", fact.codeReview());
    data.put("evidences", evidences.findByAssessmentIdOrderById(fact.id()).stream()
        .map(this::evidence).toList());
    return new ReadToolResult.Success(data, List.of());
  }

  private Evidence evidence(AdaptiveAgentEvidenceEntity entity) {
    return new Evidence(entity.id(), entity.evidenceType(), entity.sourceTurnIndex(),
        entity.quoteText(), entity.quoteLocator(), entity.sandboxExecutionId(),
        entity.codeSourceId(), entity.codeAnchor(), entity.codeFactUsage());
  }

  record Evidence(long id, EvidenceType type, int sourceTurnIndex, String quote,
      SourceQuote.Locator locator, String sandboxExecutionId, String codeSourceId,
      String codeAnchor, CodeFactUsage codeFactUsage) {}
}
