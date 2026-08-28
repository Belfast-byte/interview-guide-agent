package interview.guide.modules.interview.agent.adaptive.persistence.session;

public record AdaptiveActionPreparationInput(
    AdaptiveAnswerFacts answer,
    AdaptiveAssessmentFacts assessment,
    AdaptiveActionPreparation preparation
) {}
