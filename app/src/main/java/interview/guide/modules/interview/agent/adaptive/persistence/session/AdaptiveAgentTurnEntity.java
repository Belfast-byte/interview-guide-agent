package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateCodeSubmission;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeFactUsage;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTrigger;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTrigger.AssessmentGapSource;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * AdaptiveAgentTurnEntity JPA 实体，对应数据库中的相关表。
 */
@Entity
@Table(
    name = "agent_turns",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_agent_turn_session_index",
            columnNames = {"session_id", "turn_index"}
        ),
        @UniqueConstraint(
            name = "uk_agent_turn_source_probe_gap",
            columnNames = "source_probe_gap_id"
        )
    }
)
public class AdaptiveAgentTurnEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false, length = 36)
  private String sessionId;

  @Column(name = "turn_index", nullable = false)
  private int turnIndex;

  @Column(name = "dimension_order")
  private Integer dimensionOrder;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String question;

  @Column(name = "question_reason", length = 500)
  private String questionReason;

  @Column(name = "question_source_id", length = 128)
  private String questionSourceId;

  @Column(name = "question_difficulty", length = 16)
  private String questionDifficulty;

  @Column(name = "code_source_id", length = 128)
  private String codeSourceId;

  @Column(name = "code_anchor", length = 500)
  private String codeAnchor;

  @Enumerated(EnumType.STRING)
  @Column(name = "code_fact_usage", length = 24)
  private CodeFactUsage codeFactUsage;

  @Column(columnDefinition = "TEXT")
  private String answer;

  @Column(name = "code_problem_id", length = 128)
  private String codeProblemId;

  @Column(name = "code_scenario_id", length = 128)
  private String codeScenarioId;

  @Column(name = "code_language", length = 32)
  private String codeLanguage;

  @Column(name = "code_run_mode", length = 32)
  private String codeRunMode;

  @Enumerated(EnumType.STRING)
  @Column(name = "response_type", length = 20)
  private AgentResponseType responseType;

  @Column(name = "response_content", columnDefinition = "TEXT")
  private String responseContent;

  @Column(name = "decision_reason", length = 500)
  private String decisionReason;

  @Column(name = "parent_turn_index")
  private Integer parentTurnIndex;

  @Enumerated(EnumType.STRING)
  @Column(name = "trigger_type", nullable = false, length = 24)
  private TurnTriggerType triggerType;

  @Column(name = "source_assessment_id")
  private Long sourceAssessmentId;

  @Column(name = "source_probe_gap_id")
  private Long sourceProbeGapId;

  @Column(name = "source_tool_result_event_id")
  private Long sourceToolResultEventId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected AdaptiveAgentTurnEntity() {}

  public AdaptiveAgentTurnEntity(AdaptiveTurnCreation creation) {
    this.sessionId = creation.sessionId();
    this.turnIndex = creation.turnIndex();
    this.dimensionOrder = creation.dimensionOrder();
    applyQuestion(creation.questionAction());
    applyProvenance(creation.provenance());
  }

  private void applyProvenance(TurnProvenance provenance) {
    provenance.validateForTurn(turnIndex);
    parentTurnIndex = provenance.parentTurnIndex();
    triggerType = provenance.trigger().type();
    sourceAssessmentId = provenance.trigger().sourceAssessmentId();
    sourceProbeGapId = provenance.trigger().sourceProbeGapId();
    sourceToolResultEventId = provenance.trigger().sourceToolResultEventId();
  }

  private void applyQuestion(RespondAction questionAction) {
    question = questionAction.content();
    questionReason = questionAction.reason();
    questionSourceId = null;
    questionDifficulty = null;
    codeSourceId = null;
    codeAnchor = null;
    codeFactUsage = null;
    if (questionAction.questionProvenance() != null) {
      questionSourceId = questionAction.questionProvenance().stableId();
      questionDifficulty = questionAction.questionProvenance().difficulty();
    }
    if (questionAction.codeProvenance() != null) {
      codeSourceId = questionAction.codeProvenance().sourceId();
      codeAnchor = questionAction.codeProvenance().anchor();
      codeFactUsage = questionAction.codeProvenance().usage();
    }
  }

  public void complete(CandidateAnswer candidateAnswer, RespondAction action) {
    recordAnswer(candidateAnswer);
    recordResponse(action);
  }

  public void recordAnswer(CandidateAnswer candidateAnswer) {
    answer = candidateAnswer.content();
    CandidateCodeSubmission submission = candidateAnswer.codeSubmission();
    if (submission != null) {
      codeProblemId = submission.problemId();
      codeScenarioId = submission.scenarioId();
      codeLanguage = submission.language();
      codeRunMode = submission.runMode();
    }
  }

  public void recordResponse(RespondAction action) {
    responseType = action.type();
    responseContent = action.content();
    decisionReason = action.reason();
  }

  public CandidateAnswer candidateAnswer() {
    CandidateCodeSubmission submission = codeProblemId == null && codeScenarioId == null
        ? null
        : new CandidateCodeSubmission(
            codeProblemId, codeScenarioId, codeLanguage, codeRunMode);
    return new CandidateAnswer(turnIndex, answer, submission);
  }

  public AdaptiveInterviewTurn toDomain() {
    return new AdaptiveInterviewTurn(
        turnIndex,
        dimensionOrder,
        question,
        questionReason,
        answer,
        responseType,
        responseContent,
        decisionReason,
        provenance()
    );
  }

  private TurnProvenance provenance() {
    return new TurnProvenance(
        parentTurnIndex,
        new TurnTrigger(triggerType, assessmentGapSource(), sourceToolResultEventId)
    );
  }

  private AssessmentGapSource assessmentGapSource() {
    if (sourceAssessmentId == null && sourceProbeGapId == null) {
      return null;
    }
    if (sourceAssessmentId == null || sourceProbeGapId == null) {
      throw new IllegalStateException("Assessment gap provenance 不完整");
    }
    return new AssessmentGapSource(sourceAssessmentId, sourceProbeGapId);
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }

  public int turnIndex() {
    return turnIndex;
  }

  public long id() {
    return id;
  }

  public int dimensionOrder() {
    return dimensionOrder;
  }

  public String question() {
    return question;
  }

  public String answer() {
    return answer;
  }

  public String questionSourceId() {
    return questionSourceId;
  }

  public String questionDifficulty() {
    return questionDifficulty;
  }

  public String codeSourceId() {
    return codeSourceId;
  }

  public String codeAnchor() {
    return codeAnchor;
  }

  public CodeFactUsage codeFactUsage() {
    return codeFactUsage;
  }

  public Integer parentTurnIndex() {
    return parentTurnIndex;
  }

  public TurnTriggerType triggerType() {
    return triggerType;
  }

  public Long sourceAssessmentId() {
    return sourceAssessmentId;
  }

  public Long sourceProbeGapId() {
    return sourceProbeGapId;
  }

  public Long sourceToolResultEventId() {
    return sourceToolResultEventId;
  }
}
