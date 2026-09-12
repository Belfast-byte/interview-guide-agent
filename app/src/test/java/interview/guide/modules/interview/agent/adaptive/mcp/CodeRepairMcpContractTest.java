package interview.guide.modules.interview.agent.adaptive.mcp;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testDimension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.api.SubmitAdaptiveAnswerRequest;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewApplicationService;
import interview.guide.modules.interview.agent.adaptive.assessment.report.AssessmentReportService;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeRepairReview;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.AnswerProcessingStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask;
import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class CodeRepairMcpContractTest {
  private static final String CODE = "void reserve() {\n  stocks.tryReserve();\n}\n";
  private static final JsonMapper MAPPER = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  @Mock private AdaptiveInterviewApplicationService applicationService;
  @Mock private AssessmentReportService reportService;
  @Mock private AdaptiveMcpAuditService auditService;
  @Mock private McpSyncRequestContext context;
  @Mock private McpTransportContext transport;
  private AdaptiveInterviewMcpTools tools;

  @BeforeEach
  void setUp() {
    tools = new AdaptiveInterviewMcpTools(applicationService, reportService, auditService);
  }

  @Test
  void codeOnlySubmissionUsesCredentialTenantAndRetainsCodeWhitespace() {
    McpTenantPrincipal principal = authorize(McpInterviewScope.INTERVIEW_WRITE);
    var answer = new CandidateAnswer(1, null, null, new CandidateAnswer.CodeRepairAnswer(CODE));
    when(applicationService.submitAnswerForTenant("tenant-a", "session-a", answer))
        .thenReturn(interview(SessionMode.PRACTICE, AdaptiveSessionStatus.IN_PROGRESS));

    var response = tools.submitAnswer(context, "session-a", new McpSubmitAnswerRequest(
        1, "  ", new McpSubmitAnswerRequest.CodeRepairAnswerRequest(CODE)));

    assertThat(response.currentTurnDetails().submittedCode()).isEqualTo(CODE);
    verify(applicationService).submitAnswerForTenant("tenant-a", "session-a", answer);
    verify(auditService).record(principal, "interview.submit_answer", "session-a", McpAuditOutcome.SUCCEEDED);
  }

  @Test
  void codeSubmissionStillRequiresWriteScopeAndAuditsRejection() {
    McpTenantPrincipal principal = authorize(McpInterviewScope.INTERVIEW_READ);
    assertThatThrownBy(() -> tools.submitAnswer(context, "session-a", new McpSubmitAnswerRequest(
        1, null, new McpSubmitAnswerRequest.CodeRepairAnswerRequest(CODE))))
        .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());
    verify(auditService).record(principal, "interview.submit_answer", null, McpAuditOutcome.FORBIDDEN);
    verifyNoInteractions(applicationService);
  }

  @ParameterizedTest
  @MethodSource("invalidInputs")
  void rejectsEmptyAndInvalidCodeInputsBeforeApplication(McpSubmitAnswerRequest request) {
    assertThatThrownBy(() -> tools.submitAnswer(context, "session-a", request))
        .hasFieldOrPropertyWithValue("code", ErrorCode.BAD_REQUEST.getCode());
    verifyNoInteractions(applicationService);
  }

  static Stream<McpSubmitAnswerRequest> invalidInputs() {
    return Stream.of(new McpSubmitAnswerRequest(0, "回答", null),
        new McpSubmitAnswerRequest(1, null, null), new McpSubmitAnswerRequest(1, "  ", null),
        new McpSubmitAnswerRequest(1, "说明", new McpSubmitAnswerRequest.CodeRepairAnswerRequest(null)),
        new McpSubmitAnswerRequest(1, "说明", new McpSubmitAnswerRequest.CodeRepairAnswerRequest(" \n")));
  }

  @ParameterizedTest
  @MethodSource("unknownFields")
  void bothBoundariesRejectUnknownFieldsEvenWithPermissiveGlobalMapper(String json) {
    assertThatThrownBy(() -> MAPPER.readValue(json, McpSubmitAnswerRequest.class))
        .hasStackTraceContaining("不支持字段");
    assertThatThrownBy(() -> MAPPER.readValue(json, SubmitAdaptiveAnswerRequest.class))
        .hasStackTraceContaining("不支持字段");
  }

  static Stream<String> unknownFields() {
    return Stream.of("""
        {"turnIndex":1,"answer":"说明","owner":"other"}
        """, """
        {"turnIndex":1,"codeRepair":{"code":"class Main {}","language":"JAVA"}}
        """, """
        {"turnIndex":1,"codeRepair":{"code":"class Main {}","reviewGuide":{"secret":"x"}}}
        """);
  }

  @Test
  void httpNestedValidationPreservesLegacyProtocolAndRejectsEmptyOrMixedRepair() {
    try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      var valid = MAPPER.readValue("""
          {"turnIndex":1,"codeRepair":{"code":"class Main {}"}}
          """, SubmitAdaptiveAnswerRequest.class);
      assertThat(validator.validate(valid)).isEmpty();
      var empty = MAPPER.readValue("""
          {"turnIndex":1,"answer":"说明","codeRepair":{"code":"  "}}
          """, SubmitAdaptiveAnswerRequest.class);
      assertThat(validator.validate(empty)).extracting(v -> v.getPropertyPath().toString())
          .contains("codeRepair.code");
      var mixed = MAPPER.readValue("""
          {"turnIndex":1,"codeRepair":{"code":"class Main {}"},"codeSubmission":{
          "problemId":"p1","language":"JAVA","runMode":"FULL"}}
          """, SubmitAdaptiveAnswerRequest.class);
      assertThat(validator.validate(mixed)).extracting(v -> v.getMessage())
          .contains("代码改错与旧代码执行提交互斥");
    }
  }

  @ParameterizedTest
  @MethodSource("feedbackModes")
  void statusUsesPublicTurnProjectionAndFeedbackTiming(SessionMode mode, AdaptiveSessionStatus status, boolean visible) {
    var response = McpInterviewStatusResponse.from(interview(mode, status));
    var json = MAPPER.valueToTree(response);
    assertThat(json.propertyNames()).containsExactlyInAnyOrder(
        "sessionId", "status", "currentTurn", "maxTurns", "currentQuestion", "currentTurnDetails", "turns");
    assertThat(response.currentTurnDetails().questionType()).isEqualTo(CodeRepairTask.QuestionType.CODE_REPAIR);
    assertThat(response.currentTurnDetails().codeTask().initialCode()).isEqualTo("void reserve() {}");
    assertThat(response.currentTurnDetails().submittedCode()).isEqualTo(CODE);
    assertThat(response.currentTurnDetails().assessmentFeedback() != null).isEqualTo(visible);
    assertThat(response.currentTurnDetails().codeReview() != null).isEqualTo(visible);
    assertThat(json.toString()).doesNotContain("reviewGuide", "private-defect", "private-trigger", "private-acceptance");
  }

  static Stream<Arguments> feedbackModes() {
    return Stream.of(Arguments.of(SessionMode.PRACTICE, AdaptiveSessionStatus.IN_PROGRESS, true),
        Arguments.of(SessionMode.EVALUATION, AdaptiveSessionStatus.IN_PROGRESS, false),
        Arguments.of(SessionMode.EVALUATION, AdaptiveSessionStatus.COMPLETED, true));
  }

  @ParameterizedTest
  @EnumSource(SessionMode.class)
  void submissionAndRecoveryKeepPriorReviewAccordingToModeAfterNextTurn(SessionMode mode) {
    authorize(McpInterviewScope.INTERVIEW_WRITE, McpInterviewScope.INTERVIEW_READ);
    PlannedInterview advanced = withFollowup(mode);
    var answer = new CandidateAnswer(1, null, null, new CandidateAnswer.CodeRepairAnswer(CODE));
    when(applicationService.submitAnswerForTenant("tenant-a", "session-a", answer)).thenReturn(advanced);
    when(applicationService.getForTenant("tenant-a", "session-a")).thenReturn(advanced);

    var submitted = tools.submitAnswer(context, "session-a", new McpSubmitAnswerRequest(
        1, null, new McpSubmitAnswerRequest.CodeRepairAnswerRequest(CODE)));
    var recovered = tools.getStatus(context, "session-a");

    assertThat(recovered).isEqualTo(submitted);
    assertThat(recovered.currentTurnDetails().turnIndex()).isEqualTo(2);
    assertThat(recovered.currentTurnDetails().assessmentFeedback()).isNull();
    assertThat(recovered.turns()).hasSize(2);
    assertThat(recovered.turns().getFirst().submittedCode()).isEqualTo(CODE);
    assertThat(recovered.turns().getFirst().codeReview() != null).isEqualTo(mode == SessionMode.PRACTICE);
    assertThat(recovered.turns().getFirst().assessmentFeedback() != null).isEqualTo(mode == SessionMode.PRACTICE);
    assertThat(MAPPER.writeValueAsString(recovered)).doesNotContain("reviewGuide", "private-acceptance");
  }

  private PlannedInterview withFollowup(SessionMode mode) {
    PlannedInterview original = interview(mode, AdaptiveSessionStatus.IN_PROGRESS);
    var firstSession = original.history().session();
    var session = new AdaptiveInterviewSession(firstSession.id(), firstSession.runtimeVersion(),
        firstSession.status(), 2, firstSession.maxTurns(), firstSession.settings());
    var next = new AdaptiveInterviewTurn(2, 0, "解释库存不足的处理", null, null, null, null, null,
        TurnProvenance.agentDecision(1), List.of(), AnswerProcessingStatus.WAITING, null,
        CodeRepairTask.QuestionType.TEXT, null, 1, null, null);
    var history = new AdaptiveInterviewHistory(session, "candidate-a", "JD", "Resume", null,
        List.of(codeTurn(), next));
    return new PlannedInterview(history, original.plan());
  }

  private McpTenantPrincipal authorize(McpInterviewScope... scopes) {
    var principal = new McpTenantPrincipal("tenant-a", "credential-a", Set.of(scopes));
    when(context.transportContext()).thenReturn(transport);
    when(transport.get(McpTenantTransportConfiguration.PRINCIPAL_KEY)).thenReturn(principal);
    return principal;
  }

  private PlannedInterview interview(SessionMode mode, AdaptiveSessionStatus status) {
    var scope = mode == SessionMode.PRACTICE
        ? new PracticeScope(List.of(new TopicKey("java-backend", "concurrency"))) : PracticeScope.none();
    var settings = new InterviewSessionSettings(mode, EVALUATION_SETTINGS.candidateLevel(), scope);
    var session = new AdaptiveInterviewSession("session-a", AdaptiveInterviewSession.RUNTIME_VERSION, status, 1, 6, settings);
    var history = new AdaptiveInterviewHistory(session, "candidate-a", "JD", "Resume", null, List.of(codeTurn()));
    var plan = new InterviewPlan("session-a", 6, List.of(testDimension(
        new DimensionProposal("并发", "库存", "JAVA", 2, "java-backend"), 0, 0)));
    return new PlannedInterview(history, plan);
  }

  private AdaptiveInterviewTurn codeTurn() {
    var task = new CodeRepairTask("void reserve() {}", List.of("不能超卖"), List.of("共享数据库"),
        new CodeRepairTask.ReviewGuide(List.of(new CodeRepairTask.Check(
            "C1", "private-defect", "private-trigger", "private-acceptance"))));
    var review = new CodeRepairReview(List.of(new CodeRepairReview.CheckReview(
        "C1", CodeRepairReview.Result.SATISFIED, "使用原子扣减")));
    var feedback = new AdaptiveInterviewTurn.AssessmentFeedback(DepthLevel.L2, "满足要求", review, List.of());
    return new AdaptiveInterviewTurn(1, 0, "修复库存", "私有决策", null, null, null, null,
        TurnProvenance.initial(), List.of(), AnswerProcessingStatus.COMPLETED, null,
        CodeRepairTask.QuestionType.CODE_REPAIR, task, 1, CODE, feedback);
  }
}
