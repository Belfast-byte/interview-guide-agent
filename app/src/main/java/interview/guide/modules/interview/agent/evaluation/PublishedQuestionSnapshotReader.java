package interview.guide.modules.interview.agent.evaluation;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** 显式离线读取，短只读事务完成归属核验与材料快照；不读取正式评估或当前记忆。 */
public final class PublishedQuestionSnapshotReader {
  private final AdaptiveAgentSessionRepository sessions;
  private final AdaptiveAgentTurnRepository turns;
  private final AdaptiveAgentPlanRepository plans;
  private final TransactionTemplate read;
  private final ObjectMapper json;

  public PublishedQuestionSnapshotReader(AdaptiveAgentSessionRepository sessions,
      AdaptiveAgentTurnRepository turns, AdaptiveAgentPlanRepository plans,
      PlatformTransactionManager transactions, ObjectMapper json) {
    this.sessions = sessions;
    this.turns = turns;
    this.plans = plans;
    this.read = new TransactionTemplate(transactions);
    this.read.setReadOnly(true);
    this.read.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
    this.json = json;
  }

  /** owner 来自受信离线入口，个人和租户归属分别核验；不接受任意 fixture 声称已发布。 */
  public QuestionSnapshot read(String owner, String tenant, String sessionId, int turnIndex) {
    QuestionSnapshot.requireText(owner);
    QuestionSnapshot.requireText(sessionId);
    return Objects.requireNonNull(read.execute(status -> {
      var session = (tenant == null
          ? sessions.findByIdAndCandidateIdAndTenantIdIsNull(sessionId, owner)
          : sessions.findByIdAndCandidateIdAndTenantId(sessionId, owner, tenant))
          .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
      var all = turns.findBySessionIdOrderByTurnIndex(sessionId).stream().map(t -> t.toDomain()).toList();
      var question = all.stream().filter(t -> t.turnIndex() == turnIndex).findFirst()
          .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
      var history = all.stream().filter(t -> t.turnIndex() < turnIndex)
          .map(t -> new QuestionSnapshot.History(t.turnIndex(), t.question(), null, null)).toList();
      // Formal assessments can be revised later. Without an as-of snapshot, do not copy today's conclusions.
      var context = new ArrayList<String>();
      if (question.codeTaskTurnIndex() != null) {
        var root = all.stream().filter(t -> t.turnIndex() == question.codeTaskTurnIndex()).findFirst();
        if (root.isPresent() && root.get().codeTask() != null) {
          context.add(json.writeValueAsString(root.get().codeTask().publicView()));
        }
      }
      String target = plans.findBySessionIdOrderByDimensionOrder(sessionId).stream()
          .filter(p -> Objects.equals(p.dimensionOrder(), question.dimensionOrder()))
          .map(p -> p.dimension() + ": " + p.focus()).findFirst().orElse(null);
      // questionReason is not exposed by AdaptiveInterviewTurnResponse; it is not public material.
      return new QuestionSnapshot(QuestionSnapshot.digest(sessionId + ":" + turnIndex),
          QuestionSnapshot.Source.PUBLISHED, turnIndex, question.questionType().name(),
          new QuestionSnapshot.PublicQuestion(question.question(), null, List.copyOf(context)),
          target, null, history, history.size() == turnIndex - 1, session.llmModelSnapshot(), "unknown");
    }));
  }
}
