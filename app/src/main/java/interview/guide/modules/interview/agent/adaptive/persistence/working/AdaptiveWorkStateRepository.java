package interview.guide.modules.interview.agent.adaptive.persistence.working;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdaptiveWorkStateRepository
    extends JpaRepository<AdaptiveWorkStateEntity, String> {}
