package interview.guide.modules.interview.agent.adaptive.assessment.depth;

import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GapResolutionTest {
  @Test void acceptsOnlyKnownGapWithCurrentAnswerQuote() {
    var request=request();
    assertThat(agent(new GapResolution(7,new SourceQuote(SourceQuote.Source.ANSWER_TEXT, "CAS 重试", null),"补足更新机制")).assess(request,"model").resolvedGaps()).hasSize(1);
    assertThatThrownBy(() -> agent(new GapResolution(8,new SourceQuote(SourceQuote.Source.ANSWER_TEXT, "CAS 重试", null),"补足更新机制")).assess(request,"model"))
        .hasMessageContaining("缺口关闭");
    assertThatThrownBy(() -> agent(new GapResolution(7,new SourceQuote(SourceQuote.Source.ANSWER_TEXT, "未出现的证据", null),"补足更新机制")).assess(request,"model"))
        .hasMessageContaining("未命中");
  }
  @Test void noNewGapDoesNotResolveOldGap() {
    var agent=new DepthAssessmentAgent((r,p) -> new AssessmentProposal(DepthLevel.L1,0.7,"只复述概念",List.of(new SourceQuote(SourceQuote.Source.ANSWER_TEXT, "CAS", null))));
    assertThat(agent.assess(request(),"model").resolvedGaps()).isEmpty();
  }
  private DepthAssessmentAgent agent(GapResolution resolution) {
    return new DepthAssessmentAgent((r,p) -> new AssessmentProposal(DepthLevel.L2,0.8,"解释更新机制",
        List.of(new SourceQuote(SourceQuote.Source.ANSWER_TEXT, "CAS 重试", null)),List.of(),List.of(resolution), null));
  }
  private AssessmentRequest request() {
    return new AssessmentRequest("session",2,new AssessmentContext("Java","CAS","如何更新？","CAS 重试保证更新",
        List.of("L0-L4"),List.of(),List.of(new CoverageView.OpenProbeGap(7,1,"target-1",1,new SourceQuote(SourceQuote.Source.ANSWER_TEXT, "CAS", null),"缺少更新机制")),List.of(), null));
  }
}
