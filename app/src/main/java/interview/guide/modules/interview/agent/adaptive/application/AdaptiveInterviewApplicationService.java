package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.persistence.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.runtime.BoundedReActRuntime;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActBudget;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdaptiveInterviewApplicationService {

  private final AdaptiveInterviewPersistenceService persistenceService;
  private final BoundedReActRuntime runtime;
  private final AdaptiveAgentProperties properties;

  public AdaptiveInterviewHistory create(String jd, String resume, String llmProvider) {
    String sessionId = UUID.randomUUID().toString();
    RespondAction firstQuestion = runtime.run(
        new ReActRequest(
            sessionId,
            llmProvider,
            jd,
            resume,
            properties.getMaxTurns(),
            List.of(),
            null
        ),
        budget()
    );
    return persistenceService.create(
        sessionId,
        jd,
        resume,
        llmProvider,
        properties.getMaxTurns(),
        firstQuestion.content()
    );
  }

  public AdaptiveInterviewHistory submitAnswer(
      String sessionId,
      CandidateAnswer answer
  ) {
    AdaptiveInterviewHistory history = persistenceService.get(sessionId);
    history.session().assertCanAnswer(answer);
    RespondAction action = runtime.run(
        new ReActRequest(
            sessionId,
            history.llmProvider(),
            history.jd(),
            history.resume(),
            history.session().maxTurns(),
            history.turns(),
            answer
        ),
        budget()
    );
    try {
      return persistenceService.recordDecision(sessionId, answer, action);
    } catch (OptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "面试会话已被其他请求推进，请刷新后重试", e);
    }
  }

  public AdaptiveInterviewHistory get(String sessionId) {
    return persistenceService.get(sessionId);
  }

  private ReActBudget budget() {
    return new ReActBudget(
        properties.getMaxSteps(),
        properties.getMaxToolCalls(),
        properties.getDeadline()
    );
  }
}
