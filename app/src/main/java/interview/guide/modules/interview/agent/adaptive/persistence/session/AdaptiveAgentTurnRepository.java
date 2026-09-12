package interview.guide.modules.interview.agent.adaptive.persistence.session;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AdaptiveAgentTurnRepository 数据访问接口，提供相关实体的 Spring Data Repository。
 */
public interface AdaptiveAgentTurnRepository
    extends JpaRepository<AdaptiveAgentTurnEntity, Long> {

  Optional<AdaptiveAgentTurnEntity> findBySessionIdAndTurnIndex(
      String sessionId,
      int turnIndex
  );

  default AdaptiveAgentTurnEntity requireOriginalCodeTask(String sessionId, int turnIndex) {
    var turn = findBySessionIdAndTurnIndex(sessionId, turnIndex)
        .orElseThrow(() -> new IllegalArgumentException("代码任务不属于本场会话"));
    var code = turn.codeRepair();
    if (code.codeTask() == null || !java.util.Objects.equals(code.codeTaskTurnIndex(), turnIndex)) {
      throw new IllegalArgumentException("代码任务引用必须指向原始轮次");
    }
    return turn;
  }

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AdaptiveAgentTurnEntity> findLockedBySessionIdAndTurnIndex(
      String sessionId,
      int turnIndex
  );

  List<AdaptiveAgentTurnEntity> findBySessionIdOrderByTurnIndex(String sessionId);

  Optional<AdaptiveAgentTurnEntity>
      findFirstBySessionIdAndWorkingMemoryIsNotNullOrderByTurnIndexDesc(String sessionId);
}
