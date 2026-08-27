package interview.guide.modules.interview.agent.adaptive.persistence.working;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStateOperation;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatchSource;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStateReducer;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** WorkState 与 Patch 的短事务持久化入口。 */
@Service
@RequiredArgsConstructor
public class WorkStatePersistenceService {

  private final AdaptiveWorkStateRepository stateRepository;
  private final AdaptiveWorkStatePatchRepository patchRepository;
  private final WorkStateJsonCodec codec;

  @Transactional
  public InterviewWorkState initialize(InterviewPlan plan) {
    InterviewWorkState state = plan.initialWorkState();
    stateRepository.save(new AdaptiveWorkStateEntity(state, codec));
    WorkStatePatch patch = new WorkStatePatch(
        plan.sessionId() + ":initialization",
        plan.sessionId(),
        0,
        1,
        WorkStatePatchSource.INITIALIZATION,
        "plan",
        List.of(new WorkStateOperation.SetFocus(state.attentionFocus()))
    );
    patchRepository.save(new AdaptiveWorkStatePatchEntity(patch, codec));
    return state;
  }

  @Transactional(readOnly = true)
  public InterviewWorkState get(String sessionId) {
    return entity(sessionId).toDomain(codec);
  }

  @Transactional(readOnly = true)
  public InterviewWorkState find(String sessionId) {
    return stateRepository.findById(sessionId)
        .map(entity -> entity.toDomain(codec))
        .orElse(null);
  }

  @Transactional
  public InterviewWorkState apply(WorkStatePatch patch) {
    if (patchRepository.existsBySessionIdAndSourceTypeAndSourceId(
        patch.sessionId(), patch.sourceType(), patch.sourceId())) {
      return get(patch.sessionId());
    }
    AdaptiveWorkStateEntity entity = entity(patch.sessionId());
    InterviewWorkState updated = WorkStateReducer.apply(entity.toDomain(codec), patch);
    entity.apply(updated, codec);
    patchRepository.save(new AdaptiveWorkStatePatchEntity(patch, codec));
    stateRepository.flush();
    return updated;
  }

  private AdaptiveWorkStateEntity entity(String sessionId) {
    return stateRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "面试 WorkState 不存在"
        ));
  }
}
