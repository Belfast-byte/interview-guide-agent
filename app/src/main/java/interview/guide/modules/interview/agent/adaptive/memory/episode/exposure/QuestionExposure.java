package interview.guide.modules.interview.agent.adaptive.memory.episode.exposure;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.time.LocalDateTime;
import java.util.Objects;

/** 候选人实际看到的一道题；即使尚未回答也参与后续去重。 */
public record QuestionExposure(
    long exposureId,
    MemoryOwner owner,
    String sessionId,
    long turnId,
    QuestionIdentity identity,
    String questionText,
    Long sourceExposureId,
    Long sourceEpisodeId,
    String embeddingDocumentId,
    LocalDateTime askedAt
) {
  public record QuestionIdentity(
      TopicKey topic,
      String evidenceObjective,
      DepthLevel probeDepth,
      String difficulty
  ) {

    public QuestionIdentity {
      Objects.requireNonNull(topic, "topic 不能为空");
      Objects.requireNonNull(probeDepth, "probeDepth 不能为空");
      requireText(evidenceObjective, "evidenceObjective");
      requireText(difficulty, "difficulty");
    }

    public static QuestionIdentity from(CapabilityTarget target) {
      return new QuestionIdentity(target.identity().topic(), target.identity().focus(),
          target.depth().expected(), target.depth().expected().name());
    }

    private static void requireText(String value, String name) {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException(name + " 不能为空");
      }
    }
  }

  public record QuestionPublication(
      RespondAction action,
      QuestionIdentity identity,
      Long sourceExposureId,
      Long sourceEpisodeId
  ) {}
}
