package interview.guide.modules.interview.agent.adaptive.persistence.working;

import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkPhase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

/** 当前 WorkState 的 1:1 持久化实体。 */
@Entity
@Table(name = "agent_work_states")
public class AdaptiveWorkStateEntity {

  @Id
  @Column(name = "session_id", length = 36)
  private String sessionId;

  @Column(nullable = false)
  private long revision;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private WorkPhase phase;

  @Column(name = "active_action_intent_id", length = 36)
  private String activeActionIntentId;

  @Column(name = "state_json", nullable = false, columnDefinition = "TEXT")
  private String stateJson;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected AdaptiveWorkStateEntity() {}

  public AdaptiveWorkStateEntity(InterviewWorkState state, WorkStateJsonCodec codec) {
    sessionId = state.sessionId();
    apply(state, codec);
  }

  public InterviewWorkState toDomain(WorkStateJsonCodec codec) {
    return codec.decodeState(stateJson);
  }

  public void apply(InterviewWorkState state, WorkStateJsonCodec codec) {
    revision = state.revision();
    phase = state.phase();
    activeActionIntentId = state.activeActionIntentId();
    stateJson = codec.encodeState(state);
  }

  public long revision() {
    return revision;
  }

  @PrePersist
  void prePersist() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
