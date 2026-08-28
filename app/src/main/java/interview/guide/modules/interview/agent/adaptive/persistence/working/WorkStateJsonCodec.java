package interview.guide.modules.interview.agent.adaptive.persistence.working;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionResultType;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.TargetWorkStatus;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkBudgetType;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkEvidenceRef;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkIssue;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkIssueStatus;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStateOperation;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** WorkState 与封闭 operation 集合的 JSON 编解码。 */
@Component
public class WorkStateJsonCodec {

  private static final TypeReference<List<OperationDocument>> OPERATIONS_TYPE =
      new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  public WorkStateJsonCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String encodeState(InterviewWorkState state) {
    try {
      return objectMapper.writeValueAsString(state);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("WorkState 序列化失败", e);
    }
  }

  public InterviewWorkState decodeState(String json) {
    try {
      return objectMapper.readValue(json, InterviewWorkState.class);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("WorkState 反序列化失败", e);
    }
  }

  public String encodeOperations(List<WorkStateOperation> operations) {
    try {
      return objectMapper.writeValueAsString(operations.stream()
          .map(this::toDocument)
          .toList());
    } catch (JacksonException e) {
      throw new IllegalArgumentException("WorkState operations 序列化失败", e);
    }
  }

  public List<WorkStateOperation> decodeOperations(String json) {
    try {
      return objectMapper.readValue(json, OPERATIONS_TYPE).stream()
          .map(this::fromDocument)
          .toList();
    } catch (JacksonException e) {
      throw new IllegalArgumentException("WorkState operations 反序列化失败", e);
    }
  }

  private OperationDocument toDocument(WorkStateOperation operation) {
    return switch (operation) {
      case WorkStateOperation.AddEvidenceRef value -> OperationDocument.evidence(value);
      case WorkStateOperation.OpenIssue value -> OperationDocument.issue(value);
      case WorkStateOperation.CloseIssue value -> OperationDocument.close(value);
      case WorkStateOperation.UpdateTargetDepth value -> OperationDocument.depth(value);
      case WorkStateOperation.SetFocus value -> OperationDocument.focus(value);
      case WorkStateOperation.ConsumeBudget value -> OperationDocument.budget(value);
      default -> transitionDocument(operation);
    };
  }

  private OperationDocument transitionDocument(WorkStateOperation operation) {
    return switch (operation) {
      case WorkStateOperation.SwitchTarget value -> OperationDocument.target(value);
      case WorkStateOperation.SetPendingAction value -> OperationDocument.pending(value);
      case WorkStateOperation.RetryPendingAction value -> OperationDocument.retry(value);
      case WorkStateOperation.ApplyActionResult value -> OperationDocument.action(value);
      case WorkStateOperation.CompleteAnswer value -> OperationDocument.answer(value);
      case WorkStateOperation.FinishSession value -> OperationDocument.finish(value);
      default -> throw new IllegalStateException("未知 WorkState operation");
    };
  }

  private WorkStateOperation fromDocument(OperationDocument document) {
    return switch (document.type()) {
      case ADD_EVIDENCE_REF -> new WorkStateOperation.AddEvidenceRef(document.evidenceRef());
      case OPEN_ISSUE -> new WorkStateOperation.OpenIssue(document.issue());
      case CLOSE_ISSUE -> new WorkStateOperation.CloseIssue(
          document.issueId(), document.issueStatus(), document.reason());
      case UPDATE_TARGET_DEPTH -> new WorkStateOperation.UpdateTargetDepth(
          document.targetId(), document.depth());
      case SET_FOCUS -> new WorkStateOperation.SetFocus(document.focus());
      case CONSUME_BUDGET -> new WorkStateOperation.ConsumeBudget(
          document.targetId(), document.budgetType());
      default -> transitionFromDocument(document);
    };
  }

  private WorkStateOperation transitionFromDocument(OperationDocument document) {
    return switch (document.type()) {
      case SWITCH_TARGET -> new WorkStateOperation.SwitchTarget(
          document.nextTargetId(), document.targetStatus());
      case SET_PENDING_ACTION -> new WorkStateOperation.SetPendingAction(document.intentId());
      case RETRY_PENDING_ACTION -> new WorkStateOperation.RetryPendingAction(
          document.targetId(), document.intentId());
      case APPLY_ACTION_RESULT -> new WorkStateOperation.ApplyActionResult(
          document.actionResultType(), document.turnIndex(), document.issueId());
      case COMPLETE_ANSWER -> new WorkStateOperation.CompleteAnswer(document.turnIndex());
      case FINISH_SESSION -> new WorkStateOperation.FinishSession(document.targetStatus());
      default -> throw new IllegalStateException("未知 WorkState operation document");
    };
  }

  private enum OperationType {
    ADD_EVIDENCE_REF,
    OPEN_ISSUE,
    CLOSE_ISSUE,
    UPDATE_TARGET_DEPTH,
    SET_FOCUS,
    CONSUME_BUDGET,
    SWITCH_TARGET,
    SET_PENDING_ACTION,
    RETRY_PENDING_ACTION,
    APPLY_ACTION_RESULT,
    COMPLETE_ANSWER,
    FINISH_SESSION
  }

  private record OperationDocument(
      OperationType type,
      WorkEvidenceRef evidenceRef,
      WorkIssue issue,
      String issueId,
      WorkIssueStatus issueStatus,
      String reason,
      String targetId,
      DepthLevel depth,
      String focus,
      WorkBudgetType budgetType,
      String nextTargetId,
      TargetWorkStatus targetStatus,
      String intentId,
      Integer turnIndex,
      ActionResultType actionResultType
  ) {

    private static OperationDocument evidence(WorkStateOperation.AddEvidenceRef value) {
      return empty(OperationType.ADD_EVIDENCE_REF, value.evidenceRef(), null);
    }

    private static OperationDocument issue(WorkStateOperation.OpenIssue value) {
      return empty(OperationType.OPEN_ISSUE, null, value.issue());
    }

    private static OperationDocument close(WorkStateOperation.CloseIssue value) {
      return new OperationDocument(OperationType.CLOSE_ISSUE, null, null, value.issueId(),
          value.status(), value.reason(), null, null, null, null, null, null, null, null, null);
    }

    private static OperationDocument depth(WorkStateOperation.UpdateTargetDepth value) {
      return new OperationDocument(OperationType.UPDATE_TARGET_DEPTH, null, null, null, null,
          null, value.targetId(), value.depth(), null, null, null, null, null, null, null);
    }

    private static OperationDocument focus(WorkStateOperation.SetFocus value) {
      return new OperationDocument(OperationType.SET_FOCUS, null, null, null, null, null,
          null, null, value.attentionFocus(), null, null, null, null, null, null);
    }

    private static OperationDocument budget(WorkStateOperation.ConsumeBudget value) {
      return new OperationDocument(OperationType.CONSUME_BUDGET, null, null, null, null, null,
          value.targetId(), null, null, value.budgetType(), null, null, null, null, null);
    }

    private static OperationDocument target(WorkStateOperation.SwitchTarget value) {
      return new OperationDocument(OperationType.SWITCH_TARGET, null, null, null, null, null,
          null, null, null, null, value.nextTargetId(), value.currentStatus(), null, null, null);
    }

    private static OperationDocument pending(WorkStateOperation.SetPendingAction value) {
      return new OperationDocument(OperationType.SET_PENDING_ACTION, null, null, null, null,
          null, null, null, null, null, null, null, value.intentId(), null, null);
    }

    private static OperationDocument retry(WorkStateOperation.RetryPendingAction value) {
      return new OperationDocument(OperationType.RETRY_PENDING_ACTION, null, null, null, null,
          null, value.failedIntentId(), null, null, null, null, null,
          value.retryIntentId(), null, null);
    }

    private static OperationDocument action(WorkStateOperation.ApplyActionResult value) {
      return new OperationDocument(OperationType.APPLY_ACTION_RESULT, null, null, value.issueId(),
          null, null, null, null, null, null, null, null, null, value.turnIndex(),
          value.resultType());
    }

    private static OperationDocument answer(WorkStateOperation.CompleteAnswer value) {
      return turn(OperationType.COMPLETE_ANSWER, value.turnIndex());
    }

    private static OperationDocument finish(WorkStateOperation.FinishSession value) {
      return new OperationDocument(OperationType.FINISH_SESSION, null, null, null, null, null,
          null, null, null, null, null, value.currentStatus(), null, null, null);
    }

    private static OperationDocument turn(OperationType type, int turnIndex) {
      return new OperationDocument(type, null, null, null, null, null, null, null, null, null,
          null, null, null, turnIndex, null);
    }

    private static OperationDocument empty(
        OperationType type,
        WorkEvidenceRef evidence,
        WorkIssue issue
    ) {
      return new OperationDocument(type, evidence, issue, null, null, null, null, null, null,
          null, null, null, null, null, null);
    }
  }
}
