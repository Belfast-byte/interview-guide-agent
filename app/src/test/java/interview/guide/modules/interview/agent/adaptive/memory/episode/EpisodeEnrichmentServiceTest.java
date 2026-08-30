package interview.guide.modules.interview.agent.adaptive.memory.episode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeEnrichmentContextReader;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeEnrichmentPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeTagRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.SemanticMemoryPersistenceService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DataJpaTest(showSql = false, properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
    EpisodeEnrichmentPersistenceService.class,
    EpisodeEnrichmentServiceDependencies.class,
    EpisodeEnrichmentService.class,
    EpisodeTagValidator.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EpisodeEnrichmentServiceTest {

  @Autowired
  private EpisodeEnrichmentService service;

  @Autowired
  private EpisodeFactRepository episodeRepository;

  @Autowired
  private EpisodeTagRepository tagRepository;

  @Autowired
  private AdaptiveAgentAssessmentRepository assessmentRepository;

  @MockitoBean
  private EpisodeEnrichmentContextReader contextReader;

  @MockitoBean
  private EpisodeEnrichmentGenerator generator;

  @MockitoBean
  private SemanticMemoryPersistenceService semanticMemory;

  private EpisodeFactEntity episode;
  private EpisodeEnrichmentRequest request;

  @BeforeEach
  void createEpisode() {
    String sessionId = "episode-" + UUID.randomUUID().toString().substring(0, 8);
    AdaptiveAgentAssessmentEntity assessment = assessmentRepository.saveAndFlush(
        new AdaptiveAgentAssessmentEntity(0, assessment(sessionId))
    );
    episode = episodeRepository.saveAndFlush(new EpisodeFactEntity(
        new EpisodeFactCreation(
            new MemoryOwner(null, "candidate-worker"),
            sessionId,
            SessionMode.EVALUATION,
            assessment.id(),
            1,
            new TopicKey("java-backend", "REDIS"),
            "target-0",
            1,
            2,
            EpisodeAssistanceLevel.NONE,
            EpisodeClosureStatus.UNRESOLVED,
            null
        ),
        assessment
    ));
    request = request(sessionId);
    when(contextReader.load(episode.id())).thenReturn(request);
  }

  @Test
  @DisplayName("LLM 在事务外执行且只保存来源合法的标签")
  void shouldGenerateOutsideTransactionAndFilterInvalidTag() {
    doAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return proposal();
    }).when(generator).generate(any(EpisodeEnrichmentRequest.class), anyString());

    assertThat(service.enrich(episode.id(), "provider-a")).isTrue();
    assertThat(service.enrich(episode.id(), "provider-a")).isFalse();

    EpisodeFact fact = episodeRepository.findById(episode.id()).orElseThrow().toDomain();
    assertThat(fact.enrichmentStatus()).isEqualTo(EpisodeEnrichmentStatus.COMPLETED);
    assertThat(fact.answerSummary()).isEqualTo("候选人说明了版本号方案。");
    assertThat(tagRepository.findByEpisodeIdOrderById(episode.id())).hasSize(1);
    verify(generator, times(1)).generate(request, "provider-a");
  }

  @Test
  @DisplayName("LLM 异常显式保存 FAILED 后原样抛出")
  void shouldPersistGeneratorFailure() {
    BusinessException failure = new BusinessException(
        ErrorCode.AI_SERVICE_ERROR,
        "model unavailable"
    );
    when(generator.generate(request, "provider-a")).thenThrow(failure);

    assertThatThrownBy(() -> service.enrich(episode.id(), "provider-a"))
        .isSameAs(failure);

    EpisodeFact fact = episodeRepository.findById(episode.id()).orElseThrow().toDomain();
    assertThat(fact.enrichmentStatus()).isEqualTo(EpisodeEnrichmentStatus.FAILED);
    assertThat(fact.enrichmentError()).contains("model unavailable");
  }

  @Test
  @DisplayName("模型返回空摘要时不制造成功记录")
  void shouldFailBlankSummary() {
    when(generator.generate(request, "provider-a"))
        .thenReturn(new EpisodeEnrichmentProposal(" ", List.of()));

    assertThatThrownBy(() -> service.enrich(episode.id(), "provider-a"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(episodeRepository.findById(episode.id()).orElseThrow().toDomain()
        .enrichmentStatus()).isEqualTo(EpisodeEnrichmentStatus.FAILED);
  }

  private AssessmentDecision assessment(String sessionId) {
    return new AssessmentDecision(
        sessionId,
        1,
        DepthLevel.L2,
        0.8,
        "基础回答",
        List.of()
    );
  }

  private EpisodeEnrichmentRequest request(String sessionId) {
    return new EpisodeEnrichmentRequest(
        episode.id(),
        sessionId,
        1,
        new TopicKey("java-backend", "REDIS"),
        "如何保证缓存一致性？",
        "通过版本号保证一致性。",
        DepthLevel.L2,
        "基础回答",
        List.of(new EpisodeEvidenceFact(7, EvidenceType.QUOTE, "版本号", null)),
        List.of()
    );
  }

  private EpisodeEnrichmentProposal proposal() {
    return new EpisodeEnrichmentProposal(
        "候选人说明了版本号方案。",
        List.of(
            new EpisodeTagProposal(
                "ANSWER_HABIT", "STRUCTURED_REASONING", "ASSESSMENT_EVIDENCE", 7L
            ),
            new EpisodeTagProposal(
                "ERROR_PATTERN", "UNSUPPORTED_ASSUMPTION", "ASSESSMENT_EVIDENCE", 99L
            )
        )
    );
  }
}
