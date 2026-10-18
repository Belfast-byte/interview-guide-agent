package interview.guide.modules.interview.agent.adaptive.observability;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentProposal;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentRequest;
import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/** 独立 JSONL 观测输出；数据库仍是正式事实源，只有提交后的事件标记 COMMITTED。 */
public final class AssessmentObservationLog {
  private static final Logger LOG = LoggerFactory.getLogger("interview.assessment.observation");
  private static final Logger ERRORS = LoggerFactory.getLogger(AssessmentObservationLog.class);
  private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

  private AssessmentObservationLog() {}

  public record ReferenceObservation(String path, SourceQuote reference, String status, String reason) {}

  public static void assessed(AssessmentRequest request, String provider, String observationId,
      AssessmentProposal proposal, AssessmentDecision decision, List<ReferenceObservation> references,
      RuntimeException failure) {
    var context = request.context();
    var code = context.codeTaskContext();
    var event = event("assessment_attempt", request.sessionId(), request.turnIndex());
    event.put("status", failure == null ? "PROPOSED" : "FAILED");
    event.put("observationId", observationId);
    event.put("provider", provider);
    event.put("question", context.question());
    event.put("questionType", code != null && code.submittedCode() != null ? "CODE_REPAIR" : "TEXT");
    event.put("userInput", input(context.answer(), code == null ? null : code.submittedCode()));
    var reference = reference(context.adoptedRubrics(), code == null ? null : code.task());
    reference.put("depthRubric", context.rubric());
    reference.put("skillReference", request.skillReferenceSection());
    event.put("referenceAnswer", reference);
    event.put("assessmentContext", context);
    event.put("modelAssessment", proposal);
    event.put("assessmentResult", decision);
    event.put("references", references);
    event.put("errorType", failure == null ? null : failure.getClass().getSimpleName());
    event.put("error", failure == null ? null : failure.getMessage());
    write(event);
  }

  public static void published(String sessionId, AdaptiveInterviewTurn turn) {
    var event = event("question_published", sessionId, turn.turnIndex());
    event.put("status", "COMMITTED");
    event.put("question", turn.question());
    event.put("questionType", turn.questionType());
    event.put("codeTaskTurnIndex", turn.codeTaskTurnIndex());
    event.put("userInput", null);
    event.put("referenceAnswer", reference(turn.adoptedRubrics(), turn.codeTask()));
    event.put("assessmentResult", null);
    afterCommit(event);
  }

  public static void committed(String sessionId, AdaptiveInterviewTurn turn, CandidateAnswer answer,
      AssessmentDecision decision, String executionToken) {
    var event = event("assessment_committed", sessionId, turn.turnIndex());
    event.put("status", "COMMITTED");
    event.put("observationId", decision.observationId());
    event.put("executionToken", executionToken);
    event.put("question", turn.question());
    event.put("questionType", turn.questionType());
    event.put("codeTaskTurnIndex", turn.codeTaskTurnIndex());
    event.put("userInput", input(answer.content(), answer.codeRepair() == null ? null : answer.codeRepair().code()));
    event.put("referenceAnswer", reference(turn.adoptedRubrics(), turn.codeTask()));
    event.put("assessmentResult", decision);
    afterCommit(event);
  }

  public static void progressionFailed(String sessionId, int turnIndex, String executionToken,
      RuntimeException error) {
    var event = event("answer_progression_failed", sessionId, turnIndex);
    event.put("status", "FAILED");
    event.put("executionToken", executionToken);
    event.put("errorType", error.getClass().getSimpleName());
    event.put("error", error.getMessage());
    write(event);
  }

  private static Map<String, Object> input(String answer, String code) {
    var input = new LinkedHashMap<String, Object>();
    input.put("answer", answer);
    input.put("submittedCode", code);
    return input;
  }

  private static Map<String, Object> reference(Object rubrics, Object task) {
    var reference = new LinkedHashMap<String, Object>();
    reference.put("kind", "ASSESSMENT_CRITERIA");
    reference.put("standardAnswer", null);
    reference.put("adoptedRubrics", rubrics);
    reference.put("codeTask", task);
    return reference;
  }

  private static Map<String, Object> event(String type, String sessionId, int turnIndex) {
    var event = new LinkedHashMap<String, Object>();
    event.put("schemaVersion", 1);
    event.put("timestamp", Instant.now().toString());
    event.put("event", type);
    event.put("sessionId", sessionId);
    event.put("turnIndex", turnIndex);
    return event;
  }

  private static void afterCommit(Map<String, Object> event) {
    // 在事务内冻结 JSON，防止实体继续变化；回滚不输出成功事件。
    String line = serialize(event);
    if (line == null) return;
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException("正式评判观测必须在提交事务中注册");
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCommit() { LOG.info("{}", line); }
    });
  }

  private static void write(Map<String, Object> event) {
    String line = serialize(event);
    if (line != null) LOG.info("{}", line);
  }

  private static String serialize(Map<String, Object> event) {
    try {
      return JSON.writeValueAsString(event);
    } catch (RuntimeException error) {
      // 日志故障不能把已接受答案变成业务失败，也不把敏感输入输出到普通日志。
      ERRORS.error("Assessment observation serialization failed: event={}, sessionId={}, turnIndex={}",
          event.get("event"), event.get("sessionId"), event.get("turnIndex"), error);
      return null;
    }
  }
}
