package interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 场景卡片实体。
 */
@Entity
@Table(name = "scenario_cards")
public class ScenarioCardEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "scenario_id", nullable = false, length = 64)
  private String scenarioId;

  @Column(name = "repository_id", nullable = false, length = 36)
  private String repositoryId;

  @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
  private String payloadJson;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected ScenarioCardEntity() {}

  public ScenarioCardEntity(String scenarioId, String repositoryId, String payloadJson) {
    this.scenarioId = scenarioId;
    this.repositoryId = repositoryId;
    this.payloadJson = payloadJson;
  }

  public String payloadJson() {
    return payloadJson;
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }
}
