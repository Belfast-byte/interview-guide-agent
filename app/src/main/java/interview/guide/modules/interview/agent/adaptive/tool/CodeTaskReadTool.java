package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation;

@Component
@RequiredArgsConstructor
public class CodeTaskReadTool implements ReadOnlyAgentTool {
  private final AdaptiveAgentSessionRepository sessions;
  private final AdaptiveAgentTurnRepository turns;

  @Tool(name = "code_task_read", description = "读取本场指定轮次的代码任务、回答和提交代码；不公开私有审阅指南。")
  public DecisionObservation query(
      @ToolParam(description = "本场正整数轮次") int turnIndex,
      ToolContext toolContext) {
    var scope = InterviewToolContext.from(toolContext);
    var arguments = new java.util.LinkedHashMap<String, Object>();
    arguments.put("turnIndex", turnIndex);
    var request = new ReadToolRequest(scope.context(), arguments, scope.deadlineNanos());
    validate(request);
    return scope.observe("code_task_read", execute(request));
  }

  public String name() { return "code_task_read"; }

  public void validate(ReadToolRequest request) { SessionReadBoundary.turnIndex(request); }

  public ReadToolResult execute(ReadToolRequest request) {
    var session = SessionReadBoundary.session(request, sessions);
    int index = SessionReadBoundary.turnIndex(request);
    var selected = turns.findBySessionIdAndTurnIndex(session.id(), index)
        .orElseThrow(() -> new ReadToolValidationException("arguments.turnIndex", "本场轮次不存在"));
    var code = selected.codeRepair();
    if (code.codeTaskTurnIndex() == null) return new ReadToolResult.Empty("该轮未关联代码任务");
    var original = turns.requireOriginalCodeTask(session.id(), code.codeTaskTurnIndex()).toDomain();
    var turn = selected.toDomain();
    var task = original.codeTask();
    return new ReadToolResult.Success(Map.of(
        "codeTaskTurnIndex", original.turnIndex(), "originalQuestion", original.question(),
        "codeTask", task.publicView(),
        "adoptedSourceRefs", selected.workingMemory() == null ? List.of()
            : selected.workingMemory().deliberation().adoptedObservationRefs(),
        "turn", new SelectedTurn(turn.turnIndex(), turn.questionType(), turn.question(),
            turn.answer(), turn.submittedCode())), List.of());
  }

  record SelectedTurn(int turnIndex, CodeRepairTask.QuestionType questionType,
      String question, String answer, String submittedCode) {}
}
