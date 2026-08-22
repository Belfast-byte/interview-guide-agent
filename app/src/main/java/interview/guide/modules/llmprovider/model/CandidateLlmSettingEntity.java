package interview.guide.modules.llmprovider.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor
@Table(name = "candidate_llm_settings")
public class CandidateLlmSettingEntity {

  @Id
  @Column(name = "candidate_id")
  private UUID candidateId;

  @Column(name = "default_chat_provider_id", length = 64)
  private String defaultChatProviderId;

  @Column(name = "default_embedding_provider_id", length = 64)
  private String defaultEmbeddingProviderId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public CandidateLlmSettingEntity(UUID candidateId) {
    this.candidateId = candidateId;
  }

  public void setDefaultChatProviderId(String providerId) {
    defaultChatProviderId = providerId;
  }

  public void setDefaultEmbeddingProviderId(String providerId) {
    defaultEmbeddingProviderId = providerId;
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
