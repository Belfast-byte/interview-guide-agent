package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import org.springframework.stereotype.Component;

/**
 * Assessment 修订原子工作流的依赖组。
 */
@Component
public record AssessmentReconciliationDependencies(
    EpisodeFactRepository episodes,
    AbilityCounterRepository counters,
    EpisodeAssessmentCorrectionPersistence episodeCorrection,
    AbilityProfileSnapshotService profiles,
    AdaptiveAgentSessionRepository sessions
) {}
