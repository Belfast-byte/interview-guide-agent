package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.session.AdoptedRubricSource;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateCodeSubmission;
import interview.guide.modules.interview.agent.adaptive.core.action.CodeFactUsage;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTrigger;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTrigger.AssessmentGapSource;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.List;

/** 轮次保存原始问答、已采用量规及当前 Working Memory。 */
@Entity
@Table(
    name = "agent_turns",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_agent_turn_session_index",
            columnNames = {"session_id", "turn_index"}
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

  @jakarta.persistence.Embedded
  private CodeRepairTurnFields codeRepair;

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

  @Convert(converter = WorkingMemoryJsonConverter.class)
  @Column(name = "working_memory_snapshot", columnDefinition = "TEXT")
  private WorkingMemory workingMemory;

  @Convert(converter = AdoptedRubricsJsonConverter.class)
  @Column(name = "adopted_rubrics_json", nullable = false, columnDefinition = "TEXT")
  private List<AdoptedRubricSource> adoptedRubrics;

  @Column(name = "answer_execution_token", length = 36)
  private String answerExecutionToken;

  @Column(name = "answer_lease_until")
  private LocalDateTime answerLeaseUntil;

  @Column(name = "answer_error", length = 500)
  private String answerError;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected AdaptiveAgentTurnEntity() {}

  public AdaptiveAgentTurnEntity(AdaptiveTurnCreation creation) {
    this.sessionId = creation.sessionId();
    this.turnIndex = creation.turnIndex();
    this.dimensionOrder = creation.dimensionOrder();
    applyQuestion(creation.questionAction());
    codeRepair = new CodeRepairTurnFields(creation.questionAction(), turnIndex);
    applyProvenance(creation.provenance());
    this.workingMemory = creation.workingMemory();
    this.adoptedRubrics = creation.adoptedRubrics();
  }

  private void applyProvenance(TurnProvenance provenance) {
    provenance.validateForTurn(turnIndex);
    parentTurnIndex = provenance.parentTurnIndex();
    triggerType = provenance.trigger().type();
    sourceAssessmentId = provenance.trigger().sourceAssessmentId();
    sourceProbeGapId = provenance.trigger().sourceProbeGapId();
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

  public void recordAnswer(CandidateAnswer candidateAnswer) {
    codeRepair.recordAnswer(candidateAnswer);
    answer = candidateAnswer.content();
    CandidateCodeSubmission submission = candidateAnswer.codeSubmission();
    if (submission != null) {
      codeProblemId = submission.problemId();
      codeScenarioId = submission.scenarioId();
      codeLanguage = submission.language();
      codeRunMode = submission.runMode();
    }
  }

  public boolean processing() {
    return responseType == null && answerExecutionToken != null && answerLeaseUntil != null
        && answerLeaseUntil.isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC));
  }

  public void claimExecution(String token, java.time.Duration lease) {
    answerExecutionToken = token;
    answerLeaseUntil = LocalDateTime.now(java.time.ZoneOffset.UTC).plus(lease);
    answerError = null;
  }

  public void requireExecution(String token) {
    if (!java.util.Objects.equals(answerExecutionToken, token) || !processing()) {
      throw new interview.guide.common.exception.BusinessException(
          interview.guide.common.exception.ErrorCode.BAD_REQUEST, "回答处理已过期或被其他请求接管");
    }
  }

  public void failExecution(String token, String message) {
    if (responseType == null && java.util.Objects.equals(answerExecutionToken, token)) {
      answerExecutionToken = null;
      answerLeaseUntil = null;
      answerError = message == null ? "回答处理失败，请重试" : message.substring(0, Math.min(500, message.length()));
    }
  }

  private interview.guide.modules.interview.agent.adaptive.core.session.AnswerProcessingStatus answerStatus() {
    if (responseType != null) return interview.guide.modules.interview.agent.adaptive.core.session.AnswerProcessingStatus.COMPLETED;
    if (!hasAnswer()) return interview.guide.modules.interview.agent.adaptive.core.session.AnswerProcessingStatus.WAITING;
    return processing() ? interview.guide.modules.interview.agent.adaptive.core.session.AnswerProcessingStatus.PROCESSING
        : interview.guide.modules.interview.agent.adaptive.core.session.AnswerProcessingStatus.RETRYABLE;
  }

  public void recordResponse(RespondAction action) {
    answerExecutionToken = null;
    answerLeaseUntil = null;
    answerError = null;
    responseType = action.type();
    responseContent = action.content();
    decisionReason = action.reason();
  }

  public CandidateAnswer candidateAnswer() {
    CandidateCodeSubmission submission = codeProblemId == null && codeScenarioId == null
        ? null
        : new CandidateCodeSubmission(
            codeProblemId, codeScenarioId, codeLanguage, codeRunMode);
    return new CandidateAnswer(turnIndex, answer, submission, codeRepair.submittedCode() == null
        ? null : new CandidateAnswer.CodeRepairAnswer(codeRepair.submittedCode()));
  }

  public AdaptiveInterviewTurn toDomain() {
    return new AdaptiveInterviewTurn(turnIndex, dimensionOrder, question, questionReason,
        answer, responseType, responseContent, decisionReason, provenance(), adoptedRubrics,
        answerStatus(), answerError, codeRepair.questionType(), codeRepair.codeTask(),
        codeRepair.codeTaskTurnIndex(), codeRepair.submittedCode(), null);
  }

  private TurnProvenance provenance() {
    return new TurnProvenance(
        parentTurnIndex,
        new TurnTrigger(triggerType, assessmentGapSource())
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

  public int turnIndex() { return turnIndex; }

  public long id() { return id; }

  public int dimensionOrder() { return dimensionOrder; }

  public String question() { return question; }

  public boolean hasAnswer() { return answer != null || codeRepair.submittedCode() != null; }

  public CodeRepairTurnFields codeRepair() { return codeRepair; }

  public String answer() { return answer; }

  public String codeSourceId() {
    return codeSourceId;
  }

  public String codeAnchor() {
    return codeAnchor;
  }

  public WorkingMemory workingMemory() {
    return workingMemory;
  }

  public CodeFactUsage codeFactUsage() {
    return codeFactUsage;
  }

}
