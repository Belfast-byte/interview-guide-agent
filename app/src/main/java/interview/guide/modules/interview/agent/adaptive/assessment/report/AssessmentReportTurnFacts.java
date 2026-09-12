package interview.guide.modules.interview.agent.adaptive.assessment.report;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import java.util.List;

/**
 * 评估报告单轮事实。
 */
public record AssessmentReportTurnFacts(
    int turnIndex,
    DepthLevel depthLevel,
    double confidence,
    String rationale,
    List<AssessmentReportEvidenceFacts> evidences
) {}
