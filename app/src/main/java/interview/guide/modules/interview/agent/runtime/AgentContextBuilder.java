package interview.guide.modules.interview.agent.runtime;

import interview.guide.modules.interview.agent.tool.InterviewSkillCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AgentContextBuilder {

  private final AgentInterviewPersistenceService persistenceService;
  private final InterviewSkillCatalog skillCatalog;

  public InterviewAgentContext build(
      String sessionId,
      String currentAnswer,
      AssessmentResult currentAssessment
  ) {
    AgentLoopState snapshot = persistenceService.get(sessionId);
    return new InterviewAgentContext(
        snapshot.jd(),
        snapshot.resume(),
        snapshot.currentTurn(),
        snapshot.maxTurns(),
        skillCatalog.listDescriptors(),
        snapshot.loadedSkill(),
        toTranscript(snapshot.turns()),
        currentAnswer,
        currentAssessment
    );
  }

  public AssessmentContext buildAssessment(String sessionId, String currentAnswer) {
    AgentLoopState snapshot = persistenceService.get(sessionId);
    List<InterviewTranscriptTurn> previousTurns = snapshot.turns().stream()
        .filter(turn -> turn.answer() != null)
        .map(AgentContextBuilder::toTranscriptTurn)
        .toList();
    return new AssessmentContext(
        snapshot.currentQuestion(),
        currentAnswer,
        previousTurns
    );
  }

  private List<InterviewTranscriptTurn> toTranscript(List<Turn> turns) {
    return turns.stream()
        .map(AgentContextBuilder::toTranscriptTurn)
        .toList();
  }

  private static InterviewTranscriptTurn toTranscriptTurn(Turn turn) {
    return new InterviewTranscriptTurn(
        turn.turnNumber(),
        turn.question(),
        turn.answer()
    );
  }
}
