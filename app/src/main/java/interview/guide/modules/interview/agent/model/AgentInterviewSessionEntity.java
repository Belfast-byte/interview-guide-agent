package interview.guide.modules.interview.agent.model;

import interview.guide.modules.interview.agent.runtime.AgentLoopStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * Agent 面试会话 JPA 实体（agent-loop-v2）。
 */
@Entity
@Table(name = "agent_interview_sessions", indexes = {
    @Index(name = "idx_agent_interview_status_created", columnList = "status,created_at")
})
public class AgentInterviewSessionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 36)
  private String sessionId;

  @Column(nullable = false, length = 32)
  private String runtimeVersion;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String jd;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String resume;

  @Column(nullable = false)
  private Integer currentTurn;

  @Column(nullable = false)
  private Integer maxTurns;

  @Column(length = 64)
  private String selectedSkillId;

  @Column(length = 255)
  private String selectedSkillName;

  @Column(columnDefinition = "TEXT")
  private String selectedSkillDescription;

  @Column(columnDefinition = "TEXT")
  private String selectedSkillBody;

  @Column(length = 64)
  private String selectedSkillHash;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String turnsJson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AgentLoopStatus status;

  @Column(length = 500)
  private String finishReason;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  private LocalDateTime completedAt;

  @Version
  private Long version;

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getRuntimeVersion() {
    return runtimeVersion;
  }

  public void setRuntimeVersion(String runtimeVersion) {
    this.runtimeVersion = runtimeVersion;
  }

  public String getJd() {
    return jd;
  }

  public void setJd(String jd) {
    this.jd = jd;
  }

  public String getResume() {
    return resume;
  }

  public void setResume(String resume) {
    this.resume = resume;
  }

  public Integer getCurrentTurn() {
    return currentTurn;
  }

  public void setCurrentTurn(Integer currentTurn) {
    this.currentTurn = currentTurn;
  }

  public Integer getMaxTurns() {
    return maxTurns;
  }

  public void setMaxTurns(Integer maxTurns) {
    this.maxTurns = maxTurns;
  }

  public String getSelectedSkillId() {
    return selectedSkillId;
  }

  public void setSelectedSkillId(String selectedSkillId) {
    this.selectedSkillId = selectedSkillId;
  }

  public String getSelectedSkillName() {
    return selectedSkillName;
  }

  public void setSelectedSkillName(String selectedSkillName) {
    this.selectedSkillName = selectedSkillName;
  }

  public String getSelectedSkillDescription() {
    return selectedSkillDescription;
  }

  public void setSelectedSkillDescription(String selectedSkillDescription) {
    this.selectedSkillDescription = selectedSkillDescription;
  }

  public String getSelectedSkillBody() {
    return selectedSkillBody;
  }

  public void setSelectedSkillBody(String selectedSkillBody) {
    this.selectedSkillBody = selectedSkillBody;
  }

  public String getSelectedSkillHash() {
    return selectedSkillHash;
  }

  public void setSelectedSkillHash(String selectedSkillHash) {
    this.selectedSkillHash = selectedSkillHash;
  }

  public String getTurnsJson() {
    return turnsJson;
  }

  public void setTurnsJson(String turnsJson) {
    this.turnsJson = turnsJson;
  }

  public AgentLoopStatus getStatus() {
    return status;
  }

  public void setStatus(AgentLoopStatus status) {
    this.status = status;
  }

  public String getFinishReason() {
    return finishReason;
  }

  public void setFinishReason(String finishReason) {
    this.finishReason = finishReason;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(LocalDateTime completedAt) {
    this.completedAt = completedAt;
  }
}
