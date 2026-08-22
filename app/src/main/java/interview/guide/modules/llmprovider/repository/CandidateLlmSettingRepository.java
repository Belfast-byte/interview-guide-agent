package interview.guide.modules.llmprovider.repository;

import interview.guide.modules.llmprovider.model.CandidateLlmSettingEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateLlmSettingRepository
    extends JpaRepository<CandidateLlmSettingEntity, UUID> {
}
