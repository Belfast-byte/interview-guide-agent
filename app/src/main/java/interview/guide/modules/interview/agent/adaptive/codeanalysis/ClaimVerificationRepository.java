package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 声明核验仓储。
 */
interface ClaimVerificationRepository extends JpaRepository<ClaimVerificationEntity, Long> {

  List<ClaimVerificationEntity> findByRepositoryIdOrderByClaimId(String repositoryId);
}
