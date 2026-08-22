package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeFactUsage;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
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
    uniqueConstraints = @UniqueConstraint(
        name = "uk_agent_turn_session_index",
        columnNames = {"session_id", "turn_index"}
    )
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

  @Enumerated(EnumType.STRING)
  @Column(name = "response_type", length = 20)
  private AgentResponseType responseType;

  @Column(name = "response_content", columnDefinition = "TEXT")
  private String responseContent;

  @Column(name = "decision_reason", length = 500)
  private String decisionReason;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected AdaptiveAgentTurnEntity() {}

  public AdaptiveAgentTurnEntity(
      String sessionId,
      int turnIndex,
      int dimensionOrder,
      RespondAction questionAction
  ) {
    this.sessionId = sessionId;
    this.turnIndex = turnIndex;
    this.dimensionOrder = dimensionOrder;
    applyQuestion(questionAction);
  }

  /**
   * 用基于工具结果的追问替换尚未作答的占位问题。
   */
  public void replaceQuestion(RespondAction questionAction) {
    applyQuestion(questionAction);
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
    answer = candidateAnswer.content();
    responseType = action.type();
    responseContent = action.content();
    decisionReason = action.reason();
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
        decisionReason
    );
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
}
