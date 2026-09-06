package interview.guide.modules.interview.listener;

import interview.guide.common.async.AbstractStreamProducer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.common.transaction.TransactionalExecutor;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 面试评估任务生产者
 * 负责发送评估任务到 Redis Stream
 */
@Slf4j
@Component
public class EvaluateStreamProducer extends AbstractStreamProducer<String> {

    private final InterviewSessionRepository sessionRepository;
    private final TransactionalExecutor transactionalExecutor;

    public EvaluateStreamProducer(
        RedisService redisService,
        InterviewSessionRepository sessionRepository,
        TransactionalExecutor transactionalExecutor
    ) {
        super(redisService);
        this.sessionRepository = sessionRepository;
        this.transactionalExecutor = transactionalExecutor;
    }

    /**
     * 发送评估任务到 Redis Stream
     *
     * @param sessionId 面试会话ID
     */
    public void sendEvaluateTask(String sessionId) {
        transactionalExecutor.runRequiresNew(() -> {
            var session = sessionRepository.findLockedBySessionId(sessionId).orElse(null);
            if (session == null || !session.isEvaluateDispatchPending()) return;
            if (sendTask(sessionId)) {
                session.setEvaluateDispatchPending(false);
                session.setEvaluateError(null);
            } else {
                session.setEvaluateError("评估任务投递失败，等待自动重试");
            }
            sessionRepository.save(session);
        });
    }

    @Override
    protected String taskDisplayName() {
        return "评估";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.INTERVIEW_EVALUATE_STREAM_KEY;
    }

    @Override
    protected Map<String, String> buildMessage(String sessionId) {
        return Map.of(
            AsyncTaskStreamConstants.FIELD_SESSION_ID, sessionId,
            AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"
        );
    }

    @Override
    protected String payloadIdentifier(String sessionId) {
        return "sessionId=" + sessionId;
    }

    @Override
    protected void onSendFailed(String sessionId, String error) {
        // 当前投递事务保留待投递标记；不能开启新事务争抢同一行锁。
        log.warn("评估任务等待重新投递: sessionId={}", sessionId);
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${app.interview.evaluation-dispatch-delay:30000}")
    public void recoverPendingDispatches() {
        for (String sessionId : sessionRepository.findPendingEvaluationDispatch(
                org.springframework.data.domain.PageRequest.of(0, 32))) {
            try {
                sendEvaluateTask(sessionId);
            } catch (RuntimeException error) {
                log.warn("评估任务重新投递失败: sessionId={}", sessionId, error);
            }
        }
    }
}
