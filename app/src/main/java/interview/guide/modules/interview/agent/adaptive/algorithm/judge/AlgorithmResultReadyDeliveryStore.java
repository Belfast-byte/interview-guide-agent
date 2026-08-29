package interview.guide.modules.interview.agent.adaptive.algorithm.judge;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 查找尚未消费的终态沙箱执行，供结果处理器补偿。
 */
public interface AlgorithmResultReadyDeliveryStore {

  /**
   * 查找在 settledBefore 之前进入终态、会话仍在进行中且尚未消费的执行 ID。
   */
  List<String> findUnconsumedBefore(LocalDateTime settledBefore);
}
