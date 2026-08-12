package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActBudget;
import java.util.Set;
import org.springframework.stereotype.Component;

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
        Set.of("load_skill", "question_bank_search", "rubric_lookup")
    );
  }

  public AgentRoleDefinition get(AgentRole role) {
    return switch (role) {
      case PLANNER -> planner;
      case INTERVIEWER -> interviewer;
    };
  }
}
