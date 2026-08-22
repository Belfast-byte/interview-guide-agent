package interview.guide.modules.interview.agent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "candidate_agent_model_configs")
public class CandidateAgentModelConfigEntity {

  @Id
  @Column(name = "candidate_id", nullable = false)
  private UUID candidateId;

  @Column(name = "base_url", nullable = false, length = 512)
  private String baseUrl;

  @Column(name = "api_key_ciphertext", nullable = false, length = 4096)
  private String apiKeyCiphertext;

  @Column(name = "api_key_nonce", nullable = false, length = 64)
  private String apiKeyNonce;

  @Column(nullable = false, length = 128)
  private String model;

  private Double temperature;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected CandidateAgentModelConfigEntity() {}

  public CandidateAgentModelConfigEntity(UUID candidateId) {
    this.candidateId = candidateId;
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

  public UUID getCandidateId() {
    return candidateId;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getApiKeyCiphertext() {
    return apiKeyCiphertext;
  }

  public void setApiKeyCiphertext(String apiKeyCiphertext) {
    this.apiKeyCiphertext = apiKeyCiphertext;
  }

  public String getApiKeyNonce() {
    return apiKeyNonce;
  }

  public void setApiKeyNonce(String apiKeyNonce) {
    this.apiKeyNonce = apiKeyNonce;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public Double getTemperature() {
    return temperature;
  }

  public void setTemperature(Double temperature) {
    this.temperature = temperature;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
