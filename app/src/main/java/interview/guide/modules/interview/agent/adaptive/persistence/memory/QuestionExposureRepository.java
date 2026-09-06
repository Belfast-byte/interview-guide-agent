package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionExposureRepository
    extends JpaRepository<QuestionExposureEntity, Long> {

  @Query("""
      SELECT exposure
      FROM QuestionExposureEntity exposure
      WHERE exposure.candidateId = :#{#owner.candidateId}
        AND ((:#{#owner.tenantId} IS NULL AND exposure.tenantId IS NULL)
          OR exposure.tenantId = :#{#owner.tenantId})
        AND exposure.skillId = :#{#topic.skillId}
        AND exposure.focusId = :#{#topic.focusId}
      ORDER BY exposure.askedAt DESC, exposure.id DESC
      """)
  List<QuestionExposureEntity> findByOwnerAndTopic(
      @Param("owner") MemoryOwner owner,
      @Param("topic") TopicKey topic
  );
  @Query("""
      SELECT exposure
      FROM QuestionExposureEntity exposure
      WHERE exposure.candidateId = :#{#owner.candidateId}
        AND ((:#{#owner.tenantId} IS NULL AND exposure.tenantId IS NULL)
          OR exposure.tenantId = :#{#owner.tenantId})
        AND exposure.skillId = :#{#topic.skillId}
        AND exposure.focusId = :#{#topic.focusId}
      ORDER BY exposure.askedAt DESC, exposure.id DESC
      """)
  List<QuestionExposureEntity> findByOwnerAndTopic(
      @Param("owner") MemoryOwner owner, @Param("topic") TopicKey topic,
      org.springframework.data.domain.Pageable pageable);

  @Query("""
      SELECT exposure FROM QuestionExposureEntity exposure
      WHERE exposure.candidateId = :#{#owner.candidateId}
        AND ((:#{#owner.tenantId} IS NULL AND exposure.tenantId IS NULL)
          OR exposure.tenantId = :#{#owner.tenantId})
        AND exposure.skillId = :#{#topic.skillId}
        AND exposure.focusId = :#{#topic.focusId}
        AND exposure.askedAt <= :before
        AND exposure.turnId <> :turnId
      ORDER BY exposure.askedAt DESC, exposure.id DESC
      """)
  List<QuestionExposureEntity> findPrior(
      @Param("owner") MemoryOwner owner, @Param("topic") TopicKey topic,
      @Param("before") java.time.LocalDateTime before, @Param("turnId") long turnId,
      org.springframework.data.domain.Pageable pageable);
}
