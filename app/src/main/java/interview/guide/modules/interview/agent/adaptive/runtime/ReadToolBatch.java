package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import java.util.List;

/** 一次模型响应中的有序只读工具调用批次，共享绝对截止时间。 */
public record ReadToolBatch(
    AgentContext context,
    List<ReadToolCall> calls,
    long deadlineNanos,
    int batchIndex
) {

  public ReadToolBatch {
    calls = List.copyOf(calls);
  }
}
