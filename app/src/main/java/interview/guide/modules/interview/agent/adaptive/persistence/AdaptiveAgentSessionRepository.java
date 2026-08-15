package interview.guide.modules.interview.agent.adaptive.persistence;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AdaptiveAgentSessionRepository
    extends JpaRepository<AdaptiveAgentSessionEntity, String> {

  Optional<AdaptiveAgentSessionEntity> findByIdAndTenantId(String id, String tenantId);

  Optional<AdaptiveAgentSessionEntity> findByIdAndTenantIdIsNull(String id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AdaptiveAgentSessionEntity> findLockedByIdAndTenantIdIsNull(String id);
}
