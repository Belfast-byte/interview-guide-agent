package interview.guide.modules.interview.agent.adaptive.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
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
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
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
class EpisodeEnrichmentTriggerContextReaderTest {

  private static final String SESSION_ID = "session-trigger-context";
  private static final String OTHER_SESSION_ID = "other-trigger-context";

  @Autowired private EpisodeEnrichmentContextReader reader;
  @Autowired private EpisodeFactRepository episodeRepository;
  @Autowired private AdaptiveAgentTurnRepository turnRepository;
  @Autowired private AdaptiveAgentAssessmentRepository assessmentRepository;
  @Autowired private AssessmentProbeGapRepository gapRepository;
  @Autowired private AdaptiveAgentToolResultEventRepository toolResultRepository;

  @Test
  @DisplayName("ASSESSMENT_GAP 追问合并当前 Assessment 与直接来源 Assessment 的 gaps")
  void shouldMergeDirectAssessmentGapSource() {
    AdaptiveAgentAssessmentEntity source = saveAssessment(SESSION_ID, 1);
    AssessmentProbeGapEntity sourceGap = saveGap(source, "来源锚点", "来源缺口");
    AdaptiveAgentAssessmentEntity current = saveAssessment(SESSION_ID, 2);
    AssessmentProbeGapEntity currentGap = saveGap(current, "当前锚点", "当前缺口");
    AdaptiveAgentAssessmentEntity unrelated = saveAssessment(OTHER_SESSION_ID, 1);
    AssessmentProbeGapEntity unrelatedGap = saveGap(unrelated, "越界锚点", "越界缺口");
    saveAnsweredTurn(TurnProvenance.assessmentGap(1, source.id(), sourceGap.id()));
    EpisodeFactEntity episode = saveEpisode(current);

    EpisodeEnrichmentRequest request = reader.load(episode.id());

    assertThat(request.probeGaps())
        .extracting(fact -> fact.id())
        .containsExactly(currentGap.id(), sourceGap.id())
        .doesNotContain(unrelatedGap.id());
  }

  @Test
  @DisplayName("TOOL_RESULT 追问合并当前 turn facts 与直接来源事件")
  void shouldMergeDirectToolResultSource() {
    AdaptiveAgentToolResultEventEntity source = saveToolResult(
        SESSION_ID, 1, "source-result"
    );
    AdaptiveAgentToolResultEventEntity current = saveToolResult(
        SESSION_ID, 2, "current-result"
    );
    AdaptiveAgentToolResultEventEntity unrelated = saveToolResult(
        OTHER_SESSION_ID, 1, "other-result"
    );
    AdaptiveAgentAssessmentEntity assessment = saveAssessment(SESSION_ID, 2);
    saveAnsweredTurn(TurnProvenance.toolResult(1, source.id()));
    EpisodeFactEntity episode = saveEpisode(assessment);

    EpisodeEnrichmentRequest request = reader.load(episode.id());

    assertThat(request.toolResults())
        .extracting(fact -> fact.id())
        .containsExactly(current.id(), source.id())
        .doesNotContain(unrelated.id());
  }

  @Test
  @DisplayName("TOOL_RESULT 直接来源跨 session 时明确拒绝")
  void shouldRejectCrossSessionToolResultSource() {
    AdaptiveAgentToolResultEventEntity crossSession = saveToolResult(
        OTHER_SESSION_ID, 1, "cross-session-result"
    );
    AdaptiveAgentAssessmentEntity assessment = saveAssessment(SESSION_ID, 2);
    saveAnsweredTurn(TurnProvenance.toolResult(1, crossSession.id()));
    EpisodeFactEntity episode = saveEpisode(assessment);

    assertThatThrownBy(() -> reader.load(episode.id()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不属于当前 session");
  }

  @Test
  @DisplayName("直接 TOOL_RESULT 同时出现在当前 turn facts 时按事件 ID 去重")
  void shouldDeduplicateToolResultSources() {
    AdaptiveAgentToolResultEventEntity source = saveToolResult(
        SESSION_ID,
        2,
        "same-result"
    );
    AdaptiveAgentAssessmentEntity assessment = saveAssessment(SESSION_ID, 2);
    saveAnsweredTurn(TurnProvenance.toolResult(1, source.id()));
    EpisodeFactEntity episode = saveEpisode(assessment);

    assertThat(reader.load(episode.id()).toolResults())
        .extracting(fact -> fact.id())
        .containsExactly(source.id());
  }

  @Test
  @DisplayName("ASSESSMENT_GAP 直接来源跨 session 时明确拒绝")
  void shouldRejectCrossSessionAssessmentSource() {
    AdaptiveAgentAssessmentEntity crossSession = saveAssessment(OTHER_SESSION_ID, 1);
    AssessmentProbeGapEntity crossGap = saveGap(
        crossSession,
        "越界锚点",
        "越界缺口"
    );
    AdaptiveAgentAssessmentEntity assessment = saveAssessment(SESSION_ID, 2);
    saveAnsweredTurn(TurnProvenance.assessmentGap(
        1,
        crossSession.id(),
        crossGap.id()
    ));
    EpisodeFactEntity episode = saveEpisode(assessment);

    assertThatThrownBy(() -> reader.load(episode.id()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不属于当前 session");
  }

  private AdaptiveAgentAssessmentEntity saveAssessment(String sessionId, int turnIndex) {
    return assessmentRepository.saveAndFlush(new AdaptiveAgentAssessmentEntity(
        0,
        new AssessmentDecision(
            sessionId,
            turnIndex,
            DepthLevel.L2,
            0.8,
            "基础回答",
            List.of()
        )
    ));
  }

  private AssessmentProbeGapEntity saveGap(
      AdaptiveAgentAssessmentEntity assessment,
      String anchor,
      String missingPoint
  ) {
    return gapRepository.saveAndFlush(new AssessmentProbeGapEntity(
        assessment,
        1,
        new ProbeGap(anchor, missingPoint)
    ));
  }

  private AdaptiveAgentToolResultEventEntity saveToolResult(
      String sessionId,
      int turnIndex,
      String resultId
  ) {
    return toolResultRepository.saveAndFlush(new AdaptiveAgentToolResultEventEntity(
        sessionId,
        new ToolResultEvent(
            turnIndex,
            "sandbox",
            resultId,
            resultId + " summary",
            resultId + " output"
        )
    ));
  }

  private void saveAnsweredTurn(TurnProvenance provenance) {
    AdaptiveAgentTurnEntity turn = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(
        SESSION_ID,
        2,
        0,
        RespondAction.ask("请继续说明", "直接来源追问"),
        provenance
    ));
    turn.complete(
        new CandidateAnswer(2, "候选人回答"),
        RespondAction.ask("下一题", "继续")
    );
    turnRepository.saveAndFlush(turn);
  }

  private EpisodeFactEntity saveEpisode(AdaptiveAgentAssessmentEntity assessment) {
    return episodeRepository.saveAndFlush(new EpisodeFactEntity(
        new EpisodeFactCreation(
            new MemoryOwner(null, "candidate-trigger-context"),
            SESSION_ID,
            SessionMode.EVALUATION,
            2,
            2,
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
}
