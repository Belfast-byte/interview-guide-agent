package interview.guide.modules.interview.agent.adaptive.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdaptiveAgentAssessmentRepository
    extends JpaRepository<AdaptiveAgentAssessmentEntity, Long> {}
