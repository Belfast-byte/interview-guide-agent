package interview.guide.modules.interview.agent.adaptive.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdaptiveAgentSessionRepository
    extends JpaRepository<AdaptiveAgentSessionEntity, String> {

  Optional<AdaptiveAgentSessionEntity> findByIdAndTenantId(String id, String tenantId);

  Optional<AdaptiveAgentSessionEntity> findByIdAndTenantIdIsNull(String id);
}
