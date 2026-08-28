package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.core.context.QuestionRecallHint;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeRecallSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EvaluationRecallView;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionIdentity;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionIdentityFactory;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionNoveltyDecision;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionNoveltyPolicy;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionPublication;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionReview;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 在出题后召回历史并产出 ACCEPT/REWRITE，不参与 Planner。 */
@Service
@RequiredArgsConstructor
public class QuestionNoveltyService {

  private final EpisodeRecallSource recallSource;
  private final QuestionIdentityFactory identityFactory;
  private final QuestionNoveltyPolicy policy;

  public QuestionReview review(ReActRequest request, RespondAction action) {
    QuestionIdentity identity = identityFactory.create(request.interviewerContext(), action);
    List<EvaluationRecallView> recalls = recallSource.evaluation(
        request.sessionId(), identity.topic(), action.content());
    QuestionNoveltyDecision decision = policy.decide(identity, recalls);
    QuestionPublication publication = new QuestionPublication(
        action, identity, decision.sourceExposureId(), decision.sourceEpisodeId());
    return new QuestionReview(decision.type(), publication, hints(decision.recalls()));
  }

  public void requireValidRewrite(QuestionReview original, QuestionReview rewritten) {
    policy.requireSameEnvelope(
        original.publication().identity(), rewritten.publication().identity());
    if (rewritten.type() == QuestionNoveltyDecision.Type.REWRITE) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "换场景后的问题仍与历史题目重复"
      );
    }
  }

  public ReActRequest rewriteRequest(ReActRequest request, QuestionReview review) {
    InterviewerContext context = request.interviewerContext();
    InterviewerContext rewritten = new InterviewerContext(
        context.jd(),
        context.resume(),
        context.currentTurn(),
        context.maxTurns(),
        context.targetDimensionOrder(),
        context.targetDimension(),
        context.targetFocus(),
        context.suggestedTools(),
        context.suggestedSkill(),
        context.currentDimensionTurns(),
        context.currentDimensionAnswer(),
        context.working(),
        review.hints(),
        context.currentToolResult(),
        context.currentCodeSubmission(),
        context.project()
    );
    return new ReActRequest(
        request.sessionId(), request.role(), request.llmProvider(), rewritten);
  }

  private List<QuestionRecallHint> hints(List<EvaluationRecallView> recalls) {
    return recalls.stream().map(recall -> new QuestionRecallHint(
        recall.question(), recall.evidenceObjective(), recall.revalidationNeed()
    )).toList();
  }
}
