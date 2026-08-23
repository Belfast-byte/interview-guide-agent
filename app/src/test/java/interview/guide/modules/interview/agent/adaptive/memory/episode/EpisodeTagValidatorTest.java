package interview.guide.modules.interview.agent.adaptive.memory.episode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EpisodeTagValidatorTest {

  private final EpisodeTagValidator validator = new EpisodeTagValidator();

  @Test
  @DisplayName("三类权威 source 均可支持白名单标签")
  void shouldAcceptOwnedSources() {
    List<ValidatedEpisodeTag> tags = validator.validate(
        List.of(
            new EpisodeTagProposal(
                "ERROR_PATTERN", "MISSING_FAILURE_BOUNDARY", "ASSESSMENT_EVIDENCE", 1L
            ),
            new EpisodeTagProposal(
                "ANSWER_HABIT", "STRUCTURED_REASONING", "PROBE_GAP", 2L
            ),
            new EpisodeTagProposal(
                "ANSWER_HABIT", "SELF_CORRECTS_AFTER_PROBE", "TOOL_RESULT", 3L
            )
        ),
        facts()
    );

    assertThat(tags).hasSize(3);
  }

  @Test
  @DisplayName("跨 Episode source 只丢弃非法标签并保留其他标签")
  void shouldDropOnlyForeignSourceTag() {
    List<ValidatedEpisodeTag> tags = validator.validate(
        List.of(
            new EpisodeTagProposal(
                "ERROR_PATTERN", "UNSUPPORTED_ASSUMPTION", "PROBE_GAP", 99L
            ),
            new EpisodeTagProposal(
                "ANSWER_HABIT", "STRUCTURED_REASONING", "PROBE_GAP", 2L
            )
        ),
        facts()
    );

    assertThat(tags).singleElement()
        .extracting(tag -> tag.value().tag())
        .isEqualTo("STRUCTURED_REASONING");
  }

  @Test
  @DisplayName("未知分类、未知标签和缺失字段分别丢弃")
  void shouldDropMalformedTags() {
    List<ValidatedEpisodeTag> tags = validator.validate(
        List.of(
            new EpisodeTagProposal("UNKNOWN", "STRUCTURED_REASONING", "PROBE_GAP", 2L),
            new EpisodeTagProposal("ANSWER_HABIT", "FREE_TEXT", "PROBE_GAP", 2L),
            new EpisodeTagProposal(null, "STRUCTURED_REASONING", "PROBE_GAP", 2L)
        ),
        facts()
    );

    assertThat(tags).isEmpty();
  }

  @Test
  @DisplayName("完全相同的标签来源建议确定性去重")
  void shouldDeduplicateExactProposal() {
    EpisodeTagProposal proposal = new EpisodeTagProposal(
        "ANSWER_HABIT",
        "STRUCTURED_REASONING",
        "PROBE_GAP",
        2L
    );

    assertThat(validator.validate(List.of(proposal, proposal), facts())).hasSize(1);
  }

  private EpisodeSourceFacts facts() {
    return new EpisodeSourceFacts(Set.of(1L), Set.of(2L), Set.of(3L));
  }
}
