package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMemoryOwnerSource;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaPracticeMemoryOwnerSource implements PracticeMemoryOwnerSource {

  private final AdaptiveAgentSessionRepository repository;

  @Override
  public MemoryOwner findOwner(String sessionId) {
    return repository.findById(sessionId)
        .map(session -> new MemoryOwner(session.tenantId(), session.candidateId()))
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
  }
}
