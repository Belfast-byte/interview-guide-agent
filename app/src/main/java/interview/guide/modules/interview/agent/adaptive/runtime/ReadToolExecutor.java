package interview.guide.modules.interview.agent.adaptive.runtime;

import java.util.List;

/** InterviewAgentLoop 依赖的只读工具执行端口。 */
public interface ReadToolExecutor {

  List<DecisionObservation> execute(ReadToolBatch batch);
}
