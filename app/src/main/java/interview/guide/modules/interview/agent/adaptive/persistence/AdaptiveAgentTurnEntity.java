package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
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

  @Column(nullable = false, columnDefinition = "TEXT")
  private String question;

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

  AdaptiveAgentTurnEntity(String sessionId, int turnIndex, String question) {
    this.sessionId = sessionId;
    this.turnIndex = turnIndex;
    this.question = question;
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
        question,
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
}
