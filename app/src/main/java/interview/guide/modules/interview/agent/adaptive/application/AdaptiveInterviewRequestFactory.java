package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisInterviewContextService;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerWorkView;
import interview.guide.modules.interview.agent.adaptive.core.context.PracticeCoachingContext;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.InterviewerContextInput;
import interview.guide.modules.interview.agent.adaptive.memory.ToolResultContextInput;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeCoachingMemoryAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeCoachingRequest;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMemorySession;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.role.AgentRole;
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
  private final PracticeCoachingMemoryAssembler practiceMemoryAssembler;

  ReActRequest create(InterviewerDecisionInput input) {
    PlannedDimension dimension = input.dimension();
    PracticeCoachingContext practiceMemory = practiceMemoryAssembler.assemble(
        new PracticeCoachingRequest(
            input.memorySession(), dimension.topic(), dimension.focus()));
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
            allowedTools(),
            dimension.suggestedSkill(),
            input.turns(),
            input.candidateAnswer(),
            input.working(),
            codeAnalysisContextService.findForSession(input.sessionId()).orElse(null),
            practiceMemory
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
        working,
        memorySession(interview)
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
    PracticeMemorySession memorySession = memorySession(interview);
    PracticeCoachingContext practiceMemory = practiceMemoryAssembler.assemble(
        new PracticeCoachingRequest(
            memorySession, dimension.topic(), dimension.focus()));
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
            allowedTools(),
            dimension.suggestedSkill(),
            interview.history().turns(),
            event,
            InterviewerWorkView.from(interview.workState(), issueId),
            codeAnalysisContextService.findForSession(sessionId).orElse(null),
            practiceMemory
        ))
    );
  }

  private List<String> allowedTools() {
    return List.of();
  }

  private int targetOrder(String targetId) {
    return Integer.parseInt(targetId.substring(TARGET_PREFIX.length()));
  }

  private PracticeMemorySession memorySession(PlannedInterview interview) {
    var history = interview.history();
    return new PracticeMemorySession(
        history.session().id(),
        history.session().settings().mode()
    );
  }
}
