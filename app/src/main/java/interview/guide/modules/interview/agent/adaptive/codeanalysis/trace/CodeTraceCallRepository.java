package interview.guide.modules.interview.agent.adaptive.codeanalysis.trace;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 代码调用轨迹仓储。
 */
interface CodeTraceCallRepository extends JpaRepository<CodeTraceCallEntity, Long> {

  long countBySessionId(String sessionId);
}
