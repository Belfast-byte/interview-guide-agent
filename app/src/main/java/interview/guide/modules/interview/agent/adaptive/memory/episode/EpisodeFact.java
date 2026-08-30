package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import java.time.LocalDateTime;

/**
 * 已回答 turn 的最小长期事件事实。
 */
public record EpisodeFact(
    long id,
    MemoryOwner owner,
    String sessionId,
    SessionMode sessionMode,
    long turnId,
    int turnIndex,
    long assessmentId,
    TopicKey topic,
    String targetId,
    EpisodeAssistanceLevel assistanceLevel,
    EpisodeClosureStatus closureStatus,
    Long correctsEpisodeId,
    EpisodeEnrichmentStatus enrichmentStatus,
    String answerSummary,
    String enrichmentError,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
