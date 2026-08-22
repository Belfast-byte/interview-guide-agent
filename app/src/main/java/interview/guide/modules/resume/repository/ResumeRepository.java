package interview.guide.modules.resume.repository;

import interview.guide.modules.resume.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 简历Repository
 */
@Repository
public interface ResumeRepository extends JpaRepository<ResumeEntity, Long> {
    
    /**
     * 根据文件哈希查找简历（用于去重）
     */
    Optional<ResumeEntity> findByCandidateIdAndFileHash(UUID candidateId, String fileHash);
    
    /**
     * 检查文件哈希是否存在
     */
    boolean existsByCandidateIdAndFileHash(UUID candidateId, String fileHash);

    Optional<ResumeEntity> findByIdAndCandidateId(Long id, UUID candidateId);

    List<ResumeEntity> findAllByCandidateIdOrderByUploadedAtDesc(UUID candidateId);
}
