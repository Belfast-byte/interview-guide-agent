package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisInterviewContextService;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerWorkView;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.InterviewerContextInput;
import interview.guide.modules.interview.agent.adaptive.memory.ToolResultContextInput;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.role.AgentRole;
import interview.guide.modules.interview.agent.adaptive.role.AgentRoleRegistry;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AdaptiveInterviewRequestFactory {

  private static final String TARGET_PREFIX = "target-";

  private final ContextAssembler contextAssembler;
  private final CodeAnalysisInterviewContextService codeAnalysisContextService;
  private final AgentRoleRegistry roleRegistry;

  ReActRequest create(InterviewerDecisionInput input) {
    PlannedDimension dimension = input.dimension();
    return new ReActRequest(
        input.sessionId(),
        AgentRole.INTERVIEWER,
        input.llmProvider(),
        contextAssembler.interviewer(new InterviewerContextInput(
            input.jd(),
            input.resume(),
            input.maxTurns(),
            dimension.order(),
            dimension.dimension(),
            dimension.focus(),
            allowedTools(dimension),
            dimension.suggestedSkill(),
            input.turns(),
            input.candidateAnswer(),
            input.working(),
            codeAnalysisContextService.findForSession(input.sessionId()).orElse(null)
        ))
    );
  }

  ReActRequest action(
      PlannedInterview interview,
      CandidateAnswer answer,
      InterviewerWorkView working
  ) {
    PlannedDimension dimension = interview.plan().dimension(targetOrder(working.targetId()));
    return create(new InterviewerDecisionInput(
        interview.history().session().id(),
        interview.history().llmProvider(),
        interview.history().jd(),
        interview.history().resume(),
        interview.history().session().maxTurns(),
        dimension,
        interview.history().turns(),
        answer,
        working
    ));
  }

  ReActRequest toolResult(
      PlannedInterview interview,
      ToolResultEvent event,
      String issueId
  ) {
    PlannedDimension dimension = interview.plan().dimension(
        interview.workState().activeTarget().target().identity().order());
    String sessionId = interview.history().session().id();
    return new ReActRequest(
        sessionId,
        AgentRole.INTERVIEWER,
        interview.history().llmProvider(),
        contextAssembler.toolResult(new ToolResultContextInput(
            interview.history().jd(),
            interview.history().resume(),
            interview.history().session().maxTurns(),
            dimension.order(),
            dimension.dimension(),
            dimension.focus(),
            allowedTools(dimension),
            dimension.suggestedSkill(),
            interview.history().turns(),
            event,
            InterviewerWorkView.from(interview.workState(), issueId),
            codeAnalysisContextService.findForSession(sessionId).orElse(null)
        ))
    );
  }

  private List<String> allowedTools(PlannedDimension dimension) {
    return dimension.suggestedTools().stream()
        .filter(roleRegistry.get(AgentRole.INTERVIEWER).allowedTools()::contains)
        .toList();
  }

  private int targetOrder(String targetId) {
    return Integer.parseInt(targetId.substring(TARGET_PREFIX.length()));
  }
}
