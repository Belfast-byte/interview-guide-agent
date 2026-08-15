package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import org.springframework.data.jpa.repository.JpaRepository;

interface CodeTraceCallRepository extends JpaRepository<CodeTraceCallEntity, Long> {

  long countBySessionId(String sessionId);
}
