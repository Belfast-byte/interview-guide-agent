package interview.guide.modules.interview.agent.adaptive.persistence.intent;

import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntent;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentKey;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentOutcome;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentProgress;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentStatus;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentTiming;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentType;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionResultType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "agent_action_intents",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_action_intent_idempotency",
        columnNames = "idempotency_key"
    )
)
public class AdaptiveActionIntentEntity {

  @Id
  @Column(name = "intent_id", length = 36)
  private String intentId;

  @Column(name = "session_id", nullable = false, length = 36)
  private String sessionId;

  @Column(name = "active_session_id", unique = true, length = 36)
  private String activeSessionId;

  @Column(name = "based_on_revision", nullable = false)
  private long basedOnRevision;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ActionIntentType type;

  @Column(name = "target_id", nullable = false, length = 36)
  private String targetId;

  @Column(name = "issue_id", length = 128)
  private String issueId;

  @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
  private String payloadJson;

  @Column(name = "idempotency_key", nullable = false, length = 64)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ActionIntentStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "result_type", length = 16)
  private ActionResultType resultType;

  @Column(name = "result_ref", length = 128)
  private String resultRef;

  @Column(columnDefinition = "TEXT")
  private String error;

  @Column(name = "execution_started_at")
  private LocalDateTime executionStartedAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Version
  private long version;

  protected AdaptiveActionIntentEntity() {}

  public AdaptiveActionIntentEntity(ActionIntent intent, ActionIntentJsonCodec codec) {
    intentId = intent.key().intentId();
    sessionId = intent.key().sessionId();
    basedOnRevision = intent.key().basedOnRevision();
    type = intent.payload().type();
    targetId = intent.payload().target().targetId();
    issueId = intent.payload().target().issueId();
    payloadJson = codec.encode(intent.payload());
    idempotencyKey = intent.payload().idempotencyKey();
    apply(intent);
  }

  public void apply(ActionIntent intent) {
    status = intent.progress().status();
    resultType = intent.progress().outcome().resultType();
    resultRef = intent.progress().outcome().resultRef();
    error = intent.progress().outcome().error();
    executionStartedAt = intent.progress().timing().executionStartedAt();
    createdAt = intent.progress().timing().createdAt();
    updatedAt = intent.progress().timing().updatedAt();
    activeSessionId = isTerminal(status) ? null : sessionId;
  }

  public ActionIntent toDomain(ActionIntentJsonCodec codec) {
    return new ActionIntent(
        new ActionIntentKey(intentId, sessionId, basedOnRevision),
        codec.decode(type, payloadJson),
        new ActionIntentProgress(
            status,
            new ActionIntentOutcome(resultType, resultRef, error),
            new ActionIntentTiming(executionStartedAt, createdAt, updatedAt)
        )
    );
  }

  public String intentId() {
    return intentId;
  }

  private static boolean isTerminal(ActionIntentStatus value) {
    return value == ActionIntentStatus.APPLIED || value == ActionIntentStatus.FAILED;
  }
}
