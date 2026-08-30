package interview.guide.modules.interview.agent.adaptive.persistence.intent;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntent;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentOutcome;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentKey;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionPayload;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentStatus;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStateOperation;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatchSource;
import interview.guide.modules.interview.agent.adaptive.persistence.working.WorkStatePersistenceService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActionIntentPersistenceService {

  private final AdaptiveActionIntentRepository repository;
  private final ActionIntentJsonCodec codec;
  private final WorkStatePersistenceService workStateService;

  @Transactional
  public ActionIntent plan(ActionIntent intent, WorkStatePatch pendingPatch) {
    if (repository.existsByActiveSessionId(intent.key().sessionId())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "会话已有未完成 ActionIntent");
    }
    if (intent.key().basedOnRevision() != pendingPatch.baseRevision()) {
      throw new IllegalStateException("ActionIntent 与 Pending Patch revision 不一致");
    }
    repository.save(new AdaptiveActionIntentEntity(intent, codec));
    workStateService.apply(pendingPatch);
    return intent;
  }

  @Transactional
  public ActionIntent start(String intentId) {
    return update(entity(intentId).toDomain(codec).start(LocalDateTime.now()));
  }

  @Transactional
  public ActionIntent restart(String intentId) {
    return update(entity(intentId).toDomain(codec).restart(LocalDateTime.now()));
  }

  @Transactional
  public ActionIntent succeed(String intentId, ActionIntentOutcome outcome) {
    return update(entity(intentId).toDomain(codec).succeed(outcome, LocalDateTime.now()));
  }

  @Transactional
  public ActionIntent apply(String intentId, WorkStatePatch resultPatch) {
    AdaptiveActionIntentEntity entity = entity(intentId);
    ActionIntent applied = entity.toDomain(codec).apply(LocalDateTime.now());
    workStateService.apply(resultPatch);
    entity.apply(applied);
    return applied;
  }

  @Transactional
  public ActionIntent fail(String intentId, String error) {
    return update(entity(intentId).toDomain(codec).fail(error, LocalDateTime.now()));
  }

  @Transactional
  public ActionIntent retry(String failedIntentId) {
    ActionIntent failed = entity(failedIntentId).toDomain(codec);
    if (failed.progress().status() != ActionIntentStatus.FAILED) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "只有 FAILED Intent 可以显式重试");
    }
    repository.flush();
    InterviewWorkState state = workStateService.get(failed.key().sessionId());
    String retryIntentId = UUID.randomUUID().toString();
    AskActionPayload failedPayload = (AskActionPayload) failed.payload();
    ActionIntent retry = ActionIntent.planned(
        new ActionIntentKey(retryIntentId, state.sessionId(), state.revision()),
        new AskActionPayload(failedPayload.target(), retryIntentId, failedPayload.context()),
        LocalDateTime.now()
    );
    repository.save(new AdaptiveActionIntentEntity(retry, codec));
    workStateService.apply(new WorkStatePatch(
        UUID.randomUUID().toString(),
        state.sessionId(),
        state.revision(),
        state.revision() + 1,
        WorkStatePatchSource.POLICY,
        "intent:" + retryIntentId + ":retry",
        List.of(new WorkStateOperation.RetryPendingAction(
            failedIntentId, retryIntentId))
    ));
    return retry;
  }

  @Transactional(readOnly = true)
  public ActionIntent get(String intentId) {
    return entity(intentId).toDomain(codec);
  }

  @Transactional(readOnly = true)
  public List<ActionIntent> recoverable(LocalDateTime executingCutoff) {
    return repository.findByStatusInOrStatusAndExecutionStartedAtBeforeOrderByCreatedAt(
        List.of(ActionIntentStatus.PLANNED, ActionIntentStatus.SUCCEEDED),
        ActionIntentStatus.EXECUTING,
        executingCutoff
    ).stream().map(entity -> entity.toDomain(codec)).toList();
  }

  private ActionIntent update(ActionIntent intent) {
    entity(intent.key().intentId()).apply(intent);
    return intent;
  }


  private AdaptiveActionIntentEntity entity(String intentId) {
    return repository.findById(intentId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.NOT_FOUND,
            "ActionIntent 不存在"
        ));
  }
}
