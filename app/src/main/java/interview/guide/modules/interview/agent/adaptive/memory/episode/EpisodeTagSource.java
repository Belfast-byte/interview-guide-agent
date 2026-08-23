package interview.guide.modules.interview.agent.adaptive.memory.episode;

import java.util.Objects;

public record EpisodeTagSource(EpisodeTagSourceType type, long sourceId) {

  public EpisodeTagSource {
    Objects.requireNonNull(type, "type 不能为空");
    if (sourceId < 1) {
      throw new IllegalArgumentException("sourceId 必须为正数");
    }
  }
}
