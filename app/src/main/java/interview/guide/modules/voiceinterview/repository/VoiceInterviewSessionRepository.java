package interview.guide.modules.voiceinterview.repository;

import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionEntity.InterviewPhase;
import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 语音面试会话Repository
 */
@Repository
public interface VoiceInterviewSessionRepository extends JpaRepository<VoiceInterviewSessionEntity, Long> {

    /**
     * 根据用户ID查找所有会话，按开始时间倒序
     */
    Optional<VoiceInterviewSessionEntity> findByIdAndCandidateId(Long id, UUID candidateId);

    /**
     * 查找指定状态且结束时间早于给定时间的会话
     * Note: Queries the AsyncTaskStatus field, not InterviewPhase
     */
    Optional<VoiceInterviewSessionEntity> findByStatusAndEndTimeBefore(
        interview.guide.common.model.AsyncTaskStatus status,
        LocalDateTime time
    );

    /**
     * Find all sessions for a user, ordered by update time
     */
    List<VoiceInterviewSessionEntity> findByCandidateIdOrderByUpdatedAtDesc(UUID candidateId);

    /**
     * Find sessions by user and status, ordered by update time
     */
    List<VoiceInterviewSessionEntity> findByCandidateIdAndStatusOrderByUpdatedAtDesc(
        UUID candidateId,
        VoiceInterviewSessionStatus status
    );

    List<VoiceInterviewSessionEntity> findByStatusAndStartTimeBefore(
        VoiceInterviewSessionStatus status,
        LocalDateTime time
    );

    List<VoiceInterviewSessionEntity> findByEvaluateStatusAndUpdatedAtBefore(
        interview.guide.common.model.AsyncTaskStatus evaluateStatus,
        LocalDateTime time
    );
}
