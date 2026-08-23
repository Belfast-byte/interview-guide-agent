package interview.guide.modules.interview.agent.adaptive.memory.episode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EpisodeEnrichmentStateTest {

  @Test
  @DisplayName("正常处理按 PENDING 到 PROCESSING 再到 COMPLETED")
  void shouldCompleteClaimedEpisode() {
    EpisodeEnrichmentState completed = EpisodeEnrichmentState.pending()
        .claim()
        .complete();

    assertThat(completed.status()).isEqualTo(EpisodeEnrichmentStatus.COMPLETED);
    assertThat(completed.error()).isNull();
  }

  @Test
  @DisplayName("处理失败保存明确错误且只能显式重试")
  void shouldPersistFailureAndRetryExplicitly() {
    EpisodeEnrichmentState failed = EpisodeEnrichmentState.pending()
        .claim()
        .fail("LLM unavailable");

    assertThat(failed.status()).isEqualTo(EpisodeEnrichmentStatus.FAILED);
    assertThat(failed.error()).isEqualTo("LLM unavailable");
    assertThat(failed.retry()).isEqualTo(EpisodeEnrichmentState.pending());
  }

  @Test
  @DisplayName("超时 PROCESSING 可显式恢复为 PENDING")
  void shouldRecoverStaleProcessing() {
    EpisodeEnrichmentState recovered = EpisodeEnrichmentState.pending()
        .claim()
        .recoverStaleProcessing();

    assertThat(recovered).isEqualTo(EpisodeEnrichmentState.pending());
  }

  @Test
  @DisplayName("非法跨状态迁移全部明确失败")
  void shouldRejectIllegalTransitions() {
    EpisodeEnrichmentState pending = EpisodeEnrichmentState.pending();
    EpisodeEnrichmentState completed = pending.claim().complete();

    assertThatThrownBy(pending::complete).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(pending::retry).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(completed::claim).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(completed::retry).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("FAILED 必须带错误且其他状态禁止带错误")
  void shouldEnforceFailureErrorInvariant() {
    assertThatThrownBy(() -> new EpisodeEnrichmentState(
        EpisodeEnrichmentStatus.FAILED,
        " "
    )).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EpisodeEnrichmentState(
        EpisodeEnrichmentStatus.COMPLETED,
        "unexpected"
    )).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("LEGACY_UNENRICHED 不参与在线状态迁移")
  void shouldKeepLegacyStateTerminal() {
    EpisodeEnrichmentState legacy = new EpisodeEnrichmentState(
        EpisodeEnrichmentStatus.LEGACY_UNENRICHED,
        null
    );

    assertThatThrownBy(legacy::claim).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(legacy::retry).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(legacy::resetAfterAssessmentCorrection)
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("Assessment 修订可让任意在线状态重新进入 PENDING")
  void shouldResetOnlineStateAfterAssessmentCorrection() {
    EpisodeEnrichmentState completed = EpisodeEnrichmentState.pending().claim().complete();
    EpisodeEnrichmentState failed = EpisodeEnrichmentState.pending().claim().fail("error");

    assertThat(completed.resetAfterAssessmentCorrection())
        .isEqualTo(EpisodeEnrichmentState.pending());
    assertThat(failed.resetAfterAssessmentCorrection())
        .isEqualTo(EpisodeEnrichmentState.pending());
  }
}
