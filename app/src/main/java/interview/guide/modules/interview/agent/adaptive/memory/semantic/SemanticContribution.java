package interview.guide.modules.interview.agent.adaptive.memory.semantic;

public sealed interface SemanticContribution
    permits EvaluationContribution, PracticeContribution {

  SemanticSource source();

  SemanticTrack track();
}
