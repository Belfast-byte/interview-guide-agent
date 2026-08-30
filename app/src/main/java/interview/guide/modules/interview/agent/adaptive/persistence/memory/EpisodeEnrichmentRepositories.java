package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
import org.springframework.stereotype.Component;

/**
 * Episode enrichment 权威事实仓储组。
 */
@Component
public record EpisodeEnrichmentRepositories(
    EpisodeFactRepository episodes,
    AdaptiveAgentTurnRepository turns,
    AdaptiveAgentAssessmentRepository assessments,
    AdaptiveAgentEvidenceRepository evidences,
    AssessmentProbeGapRepository gaps
) {}
