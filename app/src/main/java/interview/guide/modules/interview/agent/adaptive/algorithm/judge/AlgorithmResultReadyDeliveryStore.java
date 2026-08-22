package interview.guide.modules.interview.agent.adaptive.algorithm.judge;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 算法结果就绪投递存储端口：用于对账补偿“判题已落库但编排器唤醒未送达”的执行。
 *
 * <p>送达判定依据 agent_tool_result_events 的 (toolName, resultId) 幂等键；只有会话仍在进行中时
 * 该事件才可能被记录，因此同时要求会话处于进行中，避免对已结束面试反复补偿。
 */
public interface AlgorithmResultReadyDeliveryStore {

  /**
   * 查找在 settledBefore 之前进入终态、会话仍在进行中、且尚未写入对应工具结果事件的判题执行 ID。
   */
  List<String> findUndeliveredBefore(String toolName, LocalDateTime settledBefore);
}
