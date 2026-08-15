package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "code_trace_calls")
class CodeTraceCallEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false, length = 36)
  private String sessionId;

  @Column(name = "query_hash", nullable = false, length = 64)
  private String queryHash;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected CodeTraceCallEntity() {}

  CodeTraceCallEntity(String sessionId, String queryHash) {
    this.sessionId = sessionId;
    this.queryHash = queryHash;
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }
}
