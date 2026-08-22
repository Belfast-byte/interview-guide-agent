package interview.guide.modules.llmprovider.repository;

import interview.guide.modules.llmprovider.model.LlmProviderEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmProviderRepository extends JpaRepository<LlmProviderEntity, String> {

  long countByCandidateIdIsNull();

  List<LlmProviderEntity> findByCandidateIdIsNullOrderByIdAsc();

  Optional<LlmProviderEntity> findFirstByCandidateIdIsNullOrderByIdAsc();

  Optional<LlmProviderEntity> findByIdAndCandidateIdIsNull(String id);

  List<LlmProviderEntity> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);

  Optional<LlmProviderEntity> findByIdAndCandidateId(String id, UUID candidateId);

  boolean existsByCandidateIdAndDisplayName(UUID candidateId, String displayName);

  boolean existsByCandidateIdAndDisplayNameAndIdNot(
      UUID candidateId,
      String displayName,
      String id
  );
}
