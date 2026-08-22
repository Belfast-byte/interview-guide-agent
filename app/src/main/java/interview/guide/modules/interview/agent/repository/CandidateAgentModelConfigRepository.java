package interview.guide.modules.interview.agent.repository;

import interview.guide.modules.interview.agent.model.CandidateAgentModelConfigEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateAgentModelConfigRepository
    extends JpaRepository<CandidateAgentModelConfigEntity, UUID> {
}
