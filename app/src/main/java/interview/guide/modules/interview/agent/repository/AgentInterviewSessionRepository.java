package interview.guide.modules.interview.agent.repository;

import interview.guide.modules.interview.agent.model.AgentInterviewSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Agent 面试会话仓储。
 */
@Repository
public interface AgentInterviewSessionRepository
    extends JpaRepository<AgentInterviewSessionEntity, Long> {

  Optional<AgentInterviewSessionEntity> findBySessionId(String sessionId);
}
