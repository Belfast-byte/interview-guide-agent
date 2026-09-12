package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CodeTaskReadTool implements ReadOnlyAgentTool {
  private final AdaptiveAgentSessionRepository sessions;
  private final AdaptiveAgentTurnRepository turns;

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
