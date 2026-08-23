package interview.guide.modules.interview.agent.adaptive.memory.episode;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 将不可信标签建议收敛为白名单值，并校验 source 属于当前 Episode。
 */
@Slf4j
@Component
public class EpisodeTagValidator {

  public List<ValidatedEpisodeTag> validate(
      List<EpisodeTagProposal> proposals,
      EpisodeSourceFacts sourceFacts
  ) {
    return proposals.stream()
        .map(proposal -> validateOne(proposal, sourceFacts))
        .flatMap(Optional::stream)
        .distinct()
        .toList();
  }

  private Optional<ValidatedEpisodeTag> validateOne(
      EpisodeTagProposal proposal,
      EpisodeSourceFacts sourceFacts
  ) {
    if (!isComplete(proposal)) {
      log.warn("丢弃字段不完整的 Episode 标签建议");
      return Optional.empty();
    }
    try {
      EpisodeTagValue value = new EpisodeTagValue(
          EpisodeTagCategory.valueOf(proposal.category()),
          proposal.tag()
      );
      EpisodeTagSource source = new EpisodeTagSource(
          EpisodeTagSourceType.valueOf(proposal.sourceType()),
          proposal.sourceId()
      );
      if (!sourceFacts.contains(source)) {
        log.warn(
            "丢弃跨 Episode 标签来源: sourceType={}, sourceId={}",
            source.type(),
            source.sourceId()
        );
        return Optional.empty();
      }
      return Optional.of(new ValidatedEpisodeTag(value, source));
    } catch (IllegalArgumentException e) {
      log.warn("丢弃非白名单 Episode 标签: tag={}", proposal.tag());
      return Optional.empty();
    }
  }

  private boolean isComplete(EpisodeTagProposal proposal) {
    return proposal != null
        && proposal.category() != null
        && proposal.tag() != null
        && proposal.sourceType() != null
        && proposal.sourceId() != null;
  }
}
