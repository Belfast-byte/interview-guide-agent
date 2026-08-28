package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionExposure;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionIdentity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_question_exposures")
public class QuestionExposureEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", length = 64)
  private String tenantId;

  @Column(name = "candidate_id", nullable = false, length = 64)
  private String candidateId;

  @Column(name = "session_id", nullable = false, length = 36)
  private String sessionId;

  @Column(name = "turn_id", nullable = false, unique = true)
  private long turnId;

  @Column(name = "skill_id", nullable = false, length = 64)
  private String skillId;

  @Column(name = "focus_id", nullable = false, length = 64)
  private String focusId;

  @Column(name = "evidence_objective", nullable = false, length = 500)
  private String evidenceObjective;

  @Enumerated(EnumType.STRING)
  @Column(name = "probe_depth", nullable = false, length = 8)
  private DepthLevel probeDepth;

  @Column(nullable = false, length = 32)
  private String difficulty;

  @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
  private String questionText;

  @Column(name = "scenario_fingerprint", nullable = false, length = 64)
  private String scenarioFingerprint;

  @Column(name = "wording_fingerprint", nullable = false, length = 64)
  private String wordingFingerprint;

  @Column(name = "source_exposure_id")
  private Long sourceExposureId;

  @Column(name = "source_episode_id")
  private Long sourceEpisodeId;

  @Column(name = "embedding_document_id", nullable = false, length = 80)
  private String embeddingDocumentId;

  @Column(name = "asked_at", nullable = false)
  private LocalDateTime askedAt;

  protected QuestionExposureEntity() {}

  public QuestionExposureEntity(QuestionExposureCreation creation) {
    QuestionIdentity identity = creation.publication().identity();
    tenantId = creation.owner().tenantId();
    candidateId = creation.owner().candidateId();
    sessionId = creation.sessionId();
    turnId = creation.turnId();
    skillId = identity.topic().skillId();
    focusId = identity.topic().focusId();
    evidenceObjective = identity.evidenceObjective();
    probeDepth = identity.probeDepth();
    difficulty = identity.difficulty();
    questionText = creation.publication().action().content();
    scenarioFingerprint = identity.scenarioFingerprint();
    wordingFingerprint = identity.wordingFingerprint();
    sourceExposureId = creation.publication().sourceExposureId();
    sourceEpisodeId = creation.publication().sourceEpisodeId();
    embeddingDocumentId = creation.documentId();
  }

  @PrePersist
  void prePersist() {
    askedAt = LocalDateTime.now();
  }

  public QuestionExposure toDomain() {
    return new QuestionExposure(
        id,
        new MemoryOwner(tenantId, candidateId),
        sessionId,
        turnId,
        new QuestionIdentity(
            new TopicKey(skillId, focusId), evidenceObjective, probeDepth, difficulty,
            scenarioFingerprint, wordingFingerprint),
        questionText,
        sourceExposureId,
        sourceEpisodeId,
        embeddingDocumentId,
        askedAt
    );
  }

  public long id() {
    return id;
  }

  public long turnId() {
    return turnId;
  }
}
