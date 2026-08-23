package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActBudget;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Agent 角色注册表，集中管理各角色的 Prompt、工具和预算配置。
 */
@Component
public class AgentRoleRegistry {

  private final AgentRoleDefinition planner;
  private final AgentRoleDefinition interviewer;

  public AgentRoleRegistry(AdaptiveAgentProperties properties) {
    planner = new AgentRoleDefinition(
        AgentRole.PLANNER,
        new ReActBudget(1, 0, properties.getPlannerDeadline()),
        Set.of()
    );
    interviewer = new AgentRoleDefinition(
        AgentRole.INTERVIEWER,
        new ReActBudget(
            properties.getMaxSteps(),
            properties.getMaxToolCalls(),
            properties.getDeadline()
        ),
        // question_bank_search 暂时下线：embedding 依赖未就绪，题库索引为空，
        // 面试官先全部现场出题；恢复时把 "question_bank_search" 加回白名单并还原 prompt。
        Set.of("load_skill", "rubric_lookup", "sandbox_submit")
    );
  }

  public AgentRoleDefinition get(AgentRole role) {
    return switch (role) {
      case PLANNER -> planner;
      case INTERVIEWER -> interviewer;
    };
  }
}
