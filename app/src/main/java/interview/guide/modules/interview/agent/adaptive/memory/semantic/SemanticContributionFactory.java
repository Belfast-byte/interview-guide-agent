package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeClosureStatus;
import org.springframework.stereotype.Component;

@Component
public final class SemanticContributionFactory {

  public SemanticContribution create(SemanticContributionInput input) {
    SemanticSource source = new SemanticSource(
        input.episode().id(),
        input.episode().owner(),
        input.episode().topic(),
        input.episode().createdAt()
    );
    if (input.episode().sessionMode() == SessionMode.EVALUATION) {
      return new EvaluationContribution(source, input.observedDepth());
    }
    PracticeOutcome outcome = input.episode().closureStatus() == EpisodeClosureStatus.RESOLVED
        ? PracticeOutcome.COMPLETED
        : PracticeOutcome.UNRESOLVED;
    return new PracticeContribution(source, new PracticeResult(
        outcome,
        input.episode().assistanceLevel(),
        input.targetDepth()
    ));
  }
}
