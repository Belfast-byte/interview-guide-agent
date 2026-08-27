package interview.guide.modules.interview.agent.adaptive.application;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.security.AuthenticatedUser;
import interview.guide.modules.auth.domain.UserRole;
import interview.guide.modules.interview.agent.adaptive.api.CandidateMemoryEnrichmentController;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentRequested;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentService;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactCreation;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeEnrichmentPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeEnrichmentRecoveryPersistence;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeTagRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.interview.adaptive-agent.episode-enrichment-processing-timeout=0s"
})
@Import({
    AdaptiveAgentProperties.class,
    EpisodeEnrichmentDispatcher.class,
    EpisodeEnrichmentRecoveryService.class,
    EpisodeEnrichmentRecoveryPersistence.class,
    EpisodeEnrichmentPersistenceService.class
})
class EpisodeEnrichmentRecoveryIntegrationTest {

  private static final UUID CANDIDATE_ID = UUID.fromString(
      "11111111-1111-1111-1111-111111111111"
  );
  private static final UUID OTHER_CANDIDATE_ID = UUID.fromString(
      "22222222-2222-2222-2222-222222222222"
  );
  private static final String SESSION_ID = "episode-recovery-session";
  private static final String PROVIDER = "provider-authoritative";

  @Autowired private EpisodeEnrichmentRecoveryService recoveryService;
  @Autowired private EpisodeEnrichmentDispatcher dispatcher;
  @Autowired private EpisodeEnrichmentPersistenceService persistenceService;
  @Autowired private EpisodeFactRepository episodeRepository;
  @Autowired private EpisodeTagRepository tagRepository;
  @Autowired private AdaptiveAgentSessionRepository sessionRepository;
  @Autowired private AdaptiveAgentAssessmentRepository assessmentRepository;
  @MockitoBean private AdaptiveInterviewAnswerExecutor executor;
  @MockitoBean private EpisodeEnrichmentService enrichmentService;
  private CandidateMemoryEnrichmentController controller;
  private EpisodeFactEntity episode;

  @BeforeEach
  void setUp() {
    controller = new CandidateMemoryEnrichmentController(recoveryService);
    saveSession();
    episode = saveEpisode();
  }

  @Test
  @DisplayName("AFTER_COMMIT 队列拒绝后 PENDING 由 DB 扫描重新投递")
  void shouldRecoverPendingAfterQueueRejection() {
    RejectedExecutionException rejection = new RejectedExecutionException("queue full");
    doThrow(rejection).when(executor).execute(any(Runnable.class));

    assertThatThrownBy(() -> dispatcher.onRequested(
        new EpisodeEnrichmentRequested(episode.id(), "caller-provider")
    )).isSameAs(rejection);
    assertStatus(EpisodeEnrichmentStatus.PENDING);

    executeSubmittedTask();
    recoveryService.recover();

    verify(enrichmentService).enrich(episode.id(), PROVIDER);
  }

  @Test
  @DisplayName("超时 PROCESSING 原子恢复为 PENDING 后重新投递")
  void shouldRecoverAndDispatchStaleProcessing() {
    persistenceService.claim(episode.id());
    assertStatus(EpisodeEnrichmentStatus.PROCESSING);
    executeSubmittedTask();

    recoveryService.recover();

    assertStatus(EpisodeEnrichmentStatus.PENDING);
    verify(enrichmentService).enrich(episode.id(), PROVIDER);
  }

  @Test
  @DisplayName("FAILED 不自动恢复且显式 retry 后立即投递")
  void shouldDispatchOnlyAfterExplicitRetry() {
    persistenceService.claim(episode.id());
    persistenceService.fail(episode.id(), "model unavailable");
    executeSubmittedTask();

    recoveryService.recover();
    verify(enrichmentService, never()).enrich(any(Long.class), any());

    controller.retry(principal(CANDIDATE_ID), episode.id());

    assertStatus(EpisodeEnrichmentStatus.PENDING);
    verify(enrichmentService).enrich(episode.id(), PROVIDER);
  }

  @Test
  @DisplayName("候选人不能重试其他 owner 的 FAILED Episode")
  void shouldRejectRetryAcrossOwner() {
    persistenceService.claim(episode.id());
    persistenceService.fail(episode.id(), "model unavailable");

    assertThatThrownBy(() -> controller.retry(
        principal(OTHER_CANDIDATE_ID),
        episode.id()
    )).isInstanceOf(BusinessException.class)
        .hasMessageContaining("EpisodeFact 不存在");
    verify(executor, never()).execute(any(Runnable.class));
    assertStatus(EpisodeEnrichmentStatus.FAILED);
  }

  private void executeSubmittedTask() {
    doAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    }).when(executor).execute(any(Runnable.class));
  }

  private void saveSession() {
    sessionRepository.saveAndFlush(new AdaptiveAgentSessionEntity(
        new AdaptiveInterviewSession(
            SESSION_ID,
            AdaptiveInterviewSession.RUNTIME_VERSION,
            AdaptiveSessionStatus.CREATED,
            1,
            2,
            EVALUATION_SETTINGS
        ),
        new AdaptiveSessionCreation(
            null,
            SESSION_ID,
            CANDIDATE_ID.toString(),
            "JD",
            "Resume",
            PROVIDER,
            "Provider",
            "Model",
            EVALUATION_SETTINGS
        )
    ));
  }

  private EpisodeFactEntity saveEpisode() {
    AdaptiveAgentAssessmentEntity assessment = assessmentRepository.saveAndFlush(
        new AdaptiveAgentAssessmentEntity(
            0,
            new AssessmentDecision(
                SESSION_ID,
                1,
                DepthLevel.L2,
                0.8,
                "基础回答",
                false,
                List.of()
            )
        )
    );
    return episodeRepository.saveAndFlush(new EpisodeFactEntity(
        new EpisodeFactCreation(
            new MemoryOwner(null, CANDIDATE_ID.toString()),
            SESSION_ID,
            1,
            new TopicKey("java-backend", "REDIS")
        ),
        assessment
    ));
  }

  private void assertStatus(EpisodeEnrichmentStatus expected) {
    assertThat(episodeRepository.findById(episode.id()).orElseThrow()
        .toDomain().enrichmentStatus()).isEqualTo(expected);
  }

  private AuthenticatedUser principal(UUID candidateId) {
    return new AuthenticatedUser(candidateId, UserRole.CANDIDATE);
  }
}
