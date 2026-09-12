package interview.guide.modules.interview.agent.adaptive.assessment.depth;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.context.*;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeRepairReview.CheckReview;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeRepairReview.Result;
import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote.Source;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CodeRepairAssessmentTest {
  private static final String CODE = "if (!reserve()) throw new SoldOut();";

  @Test
  void acceptsCodeOnlyAnswerAndPersistsCodeSourceForGapClosure() {
    var quote = new SourceQuote(Source.SUBMITTED_CODE, "reserve()", null);
    var review = review(Result.SATISFIED, Result.UNDETERMINED);
    var proposal = new AssessmentProposal(DepthLevel.L2, 0.8, "原子扣减；异常契约待明确", List.of(quote),
        List.of(new ProbeGap(quote, "异常类型约束未提供")),
        List.of(new GapResolution(7, quote, "提交已改为原子扣减")), review);
    var decision = agent(proposal).assess(request(CODE), "model");
    assertThat(decision.codeReview()).isEqualTo(review);
    assertThat(decision.evidenceQuotes().getFirst().locator())
        .isEqualTo(new SourceQuote.Locator(Source.SUBMITTED_CODE, 5, 14));
    assertThat(decision.probeGaps().getFirst().anchor()).isEqualTo(decision.resolvedGaps().getFirst().evidenceQuote());
  }

  @Test
  void rejectsMissingDuplicateUnknownOrIncompleteChecks() {
    var invalid = List.of(
        new CodeRepairReview(List.of(check("C1", Result.SATISFIED))),
        new CodeRepairReview(List.of(check("C1", Result.SATISFIED), check("C1", Result.NOT_SATISFIED))),
        new CodeRepairReview(List.of(check("C1", Result.SATISFIED), check("C3", Result.UNDETERMINED))),
        new CodeRepairReview(List.of(check("C1", Result.SATISFIED), new CheckReview("C2", null, "原因"))),
        new CodeRepairReview(List.of(check("C1", Result.SATISFIED), new CheckReview("C2", Result.SATISFIED, ""))));
    for (var review : invalid) {
      assertThatThrownBy(() -> agent(proposal(review)).assess(request(CODE), "model"))
          .isInstanceOf(BusinessException.class);
    }
    assertThatThrownBy(() -> agent(proposal(null)).assess(request(CODE), "model"))
        .isInstanceOf(BusinessException.class).hasMessageContaining("必须包含 codeReview");
  }

  @Test
  void textFollowUpCannotReviewAssociatedCodeAgain() {
    assertThatThrownBy(() -> agent(proposal(review(Result.SATISFIED, Result.SATISFIED)))
        .assess(request(null), "model"))
        .isInstanceOf(BusinessException.class).hasMessageContaining("文字回答不能");
    var proposal = new AssessmentProposal(DepthLevel.L0, 0.9, "缺少解释", List.of());
    assertThat(agent(proposal).assess(request(null), "model").codeReview()).isNull();
  }

  @Test
  void cannotUseInitialCodeOrPreviousAnswerAsCurrentEvidence() {
    var proposal = new AssessmentProposal(DepthLevel.L2, 0.8, "修复说明", 
        List.of(new SourceQuote(Source.SUBMITTED_CODE, "stock--", null)), List.of(), List.of(),
        review(Result.SATISFIED, Result.NOT_SATISFIED));
    assertThatThrownBy(() -> agent(proposal).assess(request(CODE), "model"))
        .isInstanceOf(BusinessException.class).hasMessageContaining("未命中");
  }

  private AssessmentProposal proposal(CodeRepairReview review) {
    return new AssessmentProposal(DepthLevel.L0, 0.8, "代码审阅", List.of(), List.of(), List.of(), review);
  }

  private DepthAssessmentAgent agent(AssessmentProposal proposal) {
    return new DepthAssessmentAgent((request, provider) -> proposal);
  }

  private CodeRepairReview review(Result first, Result second) {
    return new CodeRepairReview(List.of(check("C1", first), check("C2", second)));
  }

  private CheckReview check(String id, Result result) {
    return new CheckReview(id, result, "依据已给出的业务约束静态判断");
  }

  private AssessmentRequest request(String code) {
    var task = new CodeRepairTask("stock--;", List.of("库存不能超卖"), List.of("多实例共享数据库"),
        new CodeRepairTask.ReviewGuide(List.of(
            new CodeRepairTask.Check("C1", "并发扣减", "并发请求", "原子扣减"),
            new CodeRepairTask.Check("C2", "错误反馈", "库存不足", "明确业务异常"))));
    var gap = new CoverageView.OpenProbeGap(7, 1, "target-1", 1,
        new SourceQuote(Source.ANSWER_TEXT, "以前的文字", 0), "缺少原子性");
    return new AssessmentRequest("session", 2, new AssessmentContext("Java", "库存", "修复超卖", null,
        List.of("L0-L4"), List.of(), List.of(gap), List.of(),
        new AssessmentContext.CodeTaskContext(task, code, List.of())));
  }
}
