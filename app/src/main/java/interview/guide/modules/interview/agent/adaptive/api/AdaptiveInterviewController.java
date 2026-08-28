package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.result.Result;
import interview.guide.common.security.AuthenticatedUser;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewAnswerExecutor;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewApplicationService;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewHistoryService;
import interview.guide.modules.interview.agent.adaptive.application.AnswerEventSink;
import interview.guide.modules.interview.agent.adaptive.application.CandidateInterviewCreationCommand;
import interview.guide.modules.interview.agent.adaptive.assessment.report.AssessmentReportService;
import interview.guide.modules.interview.agent.adaptive.assessment.report.CandidateAssessmentReport;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateCodeSubmission;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultFollowUp;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 自适应面试 REST 控制器，暴露创建、答题、查询、报告等接口。
 */
@RestController
@RequestMapping("/api/adaptive-agent-interviews")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    prefix = "app.interview.adaptive-agent",
    name = "enabled",
    havingValue = "true"
)
public class AdaptiveInterviewController {

  private static final long CREATION_STREAM_TIMEOUT_MILLIS = 130_000L;
  private static final long ANSWER_STREAM_TIMEOUT_MILLIS = 75_000L;

  private final AdaptiveInterviewApplicationService applicationService;
  private final AdaptiveInterviewHistoryService historyService;
  private final AssessmentReportService reportService;
  private final AdaptiveInterviewAnswerExecutor answerExecutor;

  @PostMapping
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 5)
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 5)
  public Result<AdaptiveInterviewResponse> create(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody CreateAdaptiveInterviewRequest request
  ) {
    return Result.success(AdaptiveInterviewResponse.from(
        applicationService.createForCandidate(new CandidateInterviewCreationCommand(
            principal.candidateId(),
            request.jd(),
            request.resume(),
            request.providerId(),
            request.settings()
        ))
    ));
  }

  @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 5)
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 5)
  public SseEmitter createStream(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody CreateAdaptiveInterviewRequest request
  ) {
    SseEmitter emitter = new SseEmitter(CREATION_STREAM_TIMEOUT_MILLIS);
    applicationService.createForCandidateStreaming(
        new CandidateInterviewCreationCommand(
            principal.candidateId(),
            request.jd(),
            request.resume(),
            request.providerId(),
            request.settings()
        ),
        new SseInterviewCreationEventSink(emitter)
    );
    return emitter;
  }

  @PostMapping("/{sessionId}/answers")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 10)
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 10)
  public Result<AdaptiveInterviewResponse> submitAnswer(
      @PathVariable String sessionId,
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody SubmitAdaptiveAnswerRequest request
  ) {
    return Result.success(AdaptiveInterviewResponse.from(
        applicationService.submitAnswerForCandidate(
            candidateId(principal),
            sessionId,
            toCandidateAnswer(request)
        )
    ));
  }

  /**
   * 流式提交文本回答：SSE 推送阶段事件（assessing/generating）与决策增量文本，
   * 完成时推送权威会话状态（done），失败推送 error 事件。代码提交回答走同步接口。
   */
  @PostMapping(
      value = "/{sessionId}/answers/stream",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE
  )
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 10)
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 10)
  public SseEmitter submitAnswerStream(
      @PathVariable String sessionId,
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody SubmitAdaptiveAnswerRequest request
  ) {
    if (request.codeSubmission() != null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "代码提交回答请使用同步接口");
    }
    SseEmitter emitter = new SseEmitter(ANSWER_STREAM_TIMEOUT_MILLIS);
    SseAnswerEventSink sink = new SseAnswerEventSink(emitter);
    answerExecutor.execute(() -> {
      try {
        PlannedInterview updated = applicationService.submitAnswerStreaming(
            candidateId(principal),
            sessionId,
            toCandidateAnswer(request),
            sink
        );
        sink.onDone(updated);
      } catch (BusinessException e) {
        sink.onError(e.getCode(), e.getMessage());
      } catch (Exception e) {
        log.error("流式答题推进失败: sessionId={}", sessionId, e);
        sink.onError(ErrorCode.AI_SERVICE_ERROR.getCode(), "面试推进失败，请重试");
      }
    });
    return emitter;
  }

  private static CandidateAnswer toCandidateAnswer(SubmitAdaptiveAnswerRequest request) {
    return new CandidateAnswer(
        request.turnIndex(),
        request.answer(),
        request.codeSubmission() == null
            ? null
            : new CandidateCodeSubmission(
                request.codeSubmission().problemId(),
                request.codeSubmission().scenarioId(),
                request.codeSubmission().language().name(),
                request.codeSubmission().runMode().name()
            )
    );
  }

  /** SSE 形态的答题事件回调：客户端断开后静默后续事件，任务继续跑完落库。 */
  private static final class SseAnswerEventSink implements AnswerEventSink {

    private final SseEventSender sender;

    private SseAnswerEventSink(SseEmitter emitter) {
      this.sender = new SseEventSender(emitter);
    }

    @Override
    public void onStage(AnswerStage stage) {
      sender.send("stage", stage.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public Consumer<String> deltaSink() {
      return delta -> sender.send("delta", delta);
    }

    private void onDone(PlannedInterview interview) {
      sender.send("done", AdaptiveInterviewResponse.from(interview));
      sender.complete();
    }

    private void onError(int code, String message) {
      sender.send("error", Result.error(code, message));
      sender.complete();
    }
  }

  @GetMapping("/{sessionId}")
  public Result<AdaptiveInterviewResponse> get(
      @PathVariable String sessionId,
      @AuthenticationPrincipal AuthenticatedUser principal
  ) {
    return Result.success(AdaptiveInterviewResponse.from(
        applicationService.getForCandidate(candidateId(principal), sessionId)
    ));
  }

  @PostMapping("/{sessionId}/action-intents/{intentId}/retry")
  public Result<AdaptiveInterviewResponse> retryActionIntent(
      @PathVariable String sessionId,
      @PathVariable String intentId,
      @AuthenticationPrincipal AuthenticatedUser principal
  ) {
    return Result.success(AdaptiveInterviewResponse.from(
        applicationService.retryActionIntentForCandidate(
            candidateId(principal), sessionId, intentId)
    ));
  }

  @GetMapping("/history")
  public Result<AdaptiveInterviewHistoryPageResponse> history(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam(defaultValue = "0")
      @Min(value = 0, message = "页码不能小于 0") int page
  ) {
    return Result.success(AdaptiveInterviewHistoryPageResponse.from(
        historyService.list(principal.candidateId(), page)
    ));
  }

  @GetMapping("/{sessionId}/report")
  public Result<CandidateAssessmentReport> getReport(
      @PathVariable String sessionId,
      @AuthenticationPrincipal AuthenticatedUser principal
  ) {
    applicationService.requireCandidateSession(candidateId(principal), sessionId);
    return Result.success(reportService.candidateReport(sessionId));
  }

  @GetMapping("/{sessionId}/tool-result-follow-ups")
  public Result<List<ToolResultFollowUp>> getToolResultFollowUps(
      @PathVariable String sessionId,
      @AuthenticationPrincipal AuthenticatedUser principal
  ) {
    return Result.success(applicationService.toolResultFollowUpsForCandidate(
        candidateId(principal),
        sessionId
    ));
  }

  private String candidateId(AuthenticatedUser principal) {
    return principal.candidateId().toString();
  }
}
