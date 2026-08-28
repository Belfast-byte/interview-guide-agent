package interview.guide.modules.interview.agent.adaptive.memory.episode;

import java.util.List;
import org.springframework.stereotype.Component;

/** 只基于指纹和相似度裁决是否换场景，不改变 TargetEnvelope。 */
@Component
public class QuestionNoveltyPolicy {

  static final double DUPLICATE_SIMILARITY = 0.86;

  public QuestionNoveltyDecision decide(
      QuestionIdentity draft,
      List<EvaluationRecallView> recalls
  ) {
    EvaluationRecallView duplicate = recalls.stream()
        .filter(recall -> duplicate(draft, recall))
        .findFirst()
        .orElse(null);
    if (duplicate == null) {
      return new QuestionNoveltyDecision(
          QuestionNoveltyDecision.Type.ACCEPT, null, null, recalls);
    }
    return new QuestionNoveltyDecision(
        QuestionNoveltyDecision.Type.REWRITE,
        duplicate.exposureId(),
        duplicate.episodeId(),
        recalls
    );
  }

  public void requireSameEnvelope(
      QuestionIdentity original,
      QuestionIdentity rewritten
  ) {
    if (!original.topic().equals(rewritten.topic())
        || !original.evidenceObjective().equals(rewritten.evidenceObjective())
        || original.probeDepth() != rewritten.probeDepth()
        || !original.difficulty().equals(rewritten.difficulty())) {
      throw new IllegalStateException("换场景问题改变了 TargetEnvelope");
    }
  }

  private boolean duplicate(QuestionIdentity draft, EvaluationRecallView recall) {
    return draft.wordingFingerprint().equals(recall.question() == null
        ? ""
        : QuestionFingerprint.wording(recall.question()))
        || draft.scenarioFingerprint().equals(recall.scenarioFingerprint())
        || recall.similarity() >= DUPLICATE_SIMILARITY;
  }
}
