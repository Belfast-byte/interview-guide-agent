package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.CodeFactUsage;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
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

  @Column(name = "answered_at")
  private LocalDateTime answeredAt;

  protected AdaptiveAgentTurnEntity() {}

  AdaptiveAgentTurnEntity(
      String sessionId,
      int turnIndex,
      int dimensionOrder,
      RespondAction questionAction
  ) {
    this.sessionId = sessionId;
    this.turnIndex = turnIndex;
    this.dimensionOrder = dimensionOrder;
    this.question = questionAction.content();
    this.questionReason = questionAction.reason();
    if (questionAction.questionProvenance() != null) {
      this.questionSourceId = questionAction.questionProvenance().stableId();
      this.questionDifficulty = questionAction.questionProvenance().difficulty();
    }
    if (questionAction.codeProvenance() != null) {
      this.codeSourceId = questionAction.codeProvenance().sourceId();
      this.codeAnchor = questionAction.codeProvenance().anchor();
      this.codeFactUsage = questionAction.codeProvenance().usage();
    }
  }

  void complete(CandidateAnswer candidateAnswer, RespondAction action) {
    answer = candidateAnswer.content();
    responseType = action.type();
    responseContent = action.content();
    decisionReason = action.reason();
    answeredAt = LocalDateTime.now();
  }

  AdaptiveInterviewTurn toDomain() {
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

  int turnIndex() {
    return turnIndex;
  }

  long id() {
    return id;
  }

  int dimensionOrder() {
    return dimensionOrder;
  }

  String question() {
    return question;
  }

  String answer() {
    return answer;
  }

  String questionSourceId() {
    return questionSourceId;
  }

  String questionDifficulty() {
    return questionDifficulty;
  }

  String codeSourceId() {
    return codeSourceId;
  }

  String codeAnchor() {
    return codeAnchor;
  }

  CodeFactUsage codeFactUsage() {
    return codeFactUsage;
  }
}
