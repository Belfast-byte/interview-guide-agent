package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import java.time.LocalDateTime;

/** 候选人实际看到的一道题；即使尚未回答也参与后续去重。 */
public record QuestionExposure(
    long exposureId,
    MemoryOwner owner,
    String sessionId,
    long turnId,
    QuestionIdentity identity,
    String questionText,
    Long sourceExposureId,
    Long sourceEpisodeId,
    String embeddingDocumentId,
    LocalDateTime askedAt
) {}
