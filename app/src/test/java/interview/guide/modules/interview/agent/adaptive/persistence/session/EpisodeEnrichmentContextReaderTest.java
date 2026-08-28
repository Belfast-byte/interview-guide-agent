package interview.guide.modules.interview.agent.adaptive.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentRequest;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactCreation;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeClosureStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSourceType;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeEnrichmentContextReader;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeEnrichmentRepositories;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({EpisodeEnrichmentRepositories.class, EpisodeEnrichmentContextReader.class})
class EpisodeEnrichmentContextReaderTest {

  private static final String SESSION_ID = "session-enrichment-context";

  @Autowired
  private EpisodeEnrichmentContextReader reader;

  @Autowired
  private EpisodeFactRepository episodeRepository;

  @Autowired
  private AdaptiveAgentTurnRepository turnRepository;

  @Autowired
  private AdaptiveAgentAssessmentRepository assessmentRepository;

  @Autowired
  private AdaptiveAgentEvidenceRepository evidenceRepository;

  @Autowired
  private AssessmentProbeGapRepository gapRepository;

  @Autowired
  private AdaptiveAgentToolResultEventRepository toolResultRepository;

  @Test
  @DisplayName("从 Episode 权威关系链组装三类可引用 source")
  void shouldLoadAuthoritativeSourceFacts() {
    AdaptiveAgentTurnEntity turn = createAnsweredTurn();
    AdaptiveAgentAssessmentEntity assessment = createAssessment();
    SourceFixture sources = createSources(assessment);
    EpisodeFactEntity episode = createEpisode(assessment);

    EpisodeEnrichmentRequest request = reader.load(episode.id());

    assertThat(request.question()).isEqualTo(turn.question());
    assertThat(request.answer()).isEqualTo(turn.answer());
    assertSources(request, sources);
  }

  private SourceFixture createSources(AdaptiveAgentAssessmentEntity assessment) {
    AdaptiveAgentEvidenceEntity evidence = evidenceRepository.saveAndFlush(
        new AdaptiveAgentEvidenceEntity(
            assessment,
            SESSION_ID,
            1,
            new ValidatedAssessmentEvidence(EvidenceType.QUOTE, "版本号", null)
        )
    );
    AssessmentProbeGapEntity gap = gapRepository.saveAndFlush(
        new AssessmentProbeGapEntity(
            assessment,
            1,
            new ProbeGap("版本号", "未说明并发冲突")
        )
    );
    AdaptiveAgentToolResultEventEntity toolResult = toolResultRepository.saveAndFlush(
        new AdaptiveAgentToolResultEventEntity(
            SESSION_ID,
            new ToolResultEvent(1, "sandbox", "result-1", "执行完成", "passed")
        )
    );
    return new SourceFixture(evidence.id(), gap.id(), toolResult.id());
  }

  private EpisodeFactEntity createEpisode(AdaptiveAgentAssessmentEntity assessment) {
    return episodeRepository.saveAndFlush(new EpisodeFactEntity(
        new EpisodeFactCreation(
            new MemoryOwner(null, "candidate-context"),
            SESSION_ID,
            SessionMode.EVALUATION,
            1,
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
  }

  private void assertSources(EpisodeEnrichmentRequest request, SourceFixture sources) {
    assertThat(request.sourceFacts().contains(source(
        EpisodeTagSourceType.ASSESSMENT_EVIDENCE,
        sources.evidenceId()
    ))).isTrue();
    assertThat(request.sourceFacts().contains(source(
        EpisodeTagSourceType.PROBE_GAP,
        sources.gapId()
    ))).isTrue();
    assertThat(request.sourceFacts().contains(source(
        EpisodeTagSourceType.TOOL_RESULT,
        sources.toolResultId()
    ))).isTrue();
  }

  private AdaptiveAgentTurnEntity createAnsweredTurn() {
    AdaptiveAgentTurnEntity turn = new AdaptiveAgentTurnEntity(
        new AdaptiveTurnCreation(
            SESSION_ID,
            1,
            0,
            RespondAction.ask("如何保证缓存一致性？", "首题"),
            TurnProvenance.initial()
        )
    );
    turn.complete(
        new CandidateAnswer(1, "通过版本号保证一致性"),
        RespondAction.ask("失败时怎么办？", "继续追问")
    );
    return turnRepository.saveAndFlush(turn);
  }

  private AdaptiveAgentAssessmentEntity createAssessment() {
    return assessmentRepository.saveAndFlush(new AdaptiveAgentAssessmentEntity(
        0,
        new AssessmentDecision(
            SESSION_ID,
            1,
            DepthLevel.L2,
            0.8,
            "基础回答",
            List.of()
        )
    ));
  }

  private EpisodeTagSource source(EpisodeTagSourceType type, long id) {
    return new EpisodeTagSource(type, id);
  }

  private record SourceFixture(
      long evidenceId,
      long gapId,
      long toolResultId
  ) {}
}
