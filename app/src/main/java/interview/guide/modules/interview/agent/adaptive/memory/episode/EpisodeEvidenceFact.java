package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;

/**
 * enrichment 可引用的 Assessment evidence。
 */
public record EpisodeEvidenceFact(
    long id,
    EvidenceType type,
    String quote,
    String codeAnchor
) {}
