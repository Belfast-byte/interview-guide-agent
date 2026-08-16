package interview.guide.modules.interview.agent.adaptive.algorithm;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 沙箱执行日志仓储。
 */
interface SandboxExecutionLogRepository extends JpaRepository<SandboxExecutionLogEntity, Long> {}
