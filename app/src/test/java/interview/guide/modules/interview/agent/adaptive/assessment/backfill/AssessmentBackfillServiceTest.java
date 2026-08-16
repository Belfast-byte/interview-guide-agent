package interview.guide.modules.interview.agent.adaptive.assessment.backfill;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmAssessmentEvidenceService;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentProposal;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentRequest;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthAssessmentAgent;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceValidator;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.memory.profile.CandidateAbilityProfileWriter;
class AssessmentBackfillServiceTest {

  @Test
  @DisplayName("历史轮次逐轮使用原始问答评估并写入已校验证据")
  void shouldBackfillMissingTurnsFromOriginalFacts() {
    RecordingStore store = new RecordingStore(List.of(
        new AssessmentBackfillTurn(
            "session-old",
            1,
            0,
            "架构设计",
            "缓存权衡",
            "原始问题？",
            "原始回答包含一致性取舍",
            "provider-a",
            null,
            null,
            null
        )
    ));
    List<AssessmentRequest> requests = new ArrayList<>();
    DepthAssessmentAgent agent = new DepthAssessmentAgent((request, provider) -> {
      requests.add(request);
      return new AssessmentProposal(
          DepthLevel.L3,
          0.9,
          "展示了权衡",
          true,
          List.of("一致性取舍")
      );
    });
    AssessmentEvidenceValidator validator = new AssessmentEvidenceValidator(
        (sessionId, turnIndex, ids) -> {
          throw new AssertionError("quote 回填不应加载工具结果");
        }
    );
    AlgorithmAssessmentEvidenceService algorithmEvidenceService =
        mock(AlgorithmAssessmentEvidenceService.class);
    CandidateAbilityProfileWriter abilityProfileWriter =
        mock(CandidateAbilityProfileWriter.class);
    AssessmentBackfillService service = new AssessmentBackfillService(
        store,
        agent,
        validator,
        algorithmEvidenceService,
        abilityProfileWriter
    );

    assertThat(service.backfill("session-old")).isEqualTo(1);

    assertThat(requests).singleElement().satisfies(request -> {
      assertThat(request.context().question()).isEqualTo("原始问题？");
      assertThat(request.context().answer())
          .isEqualTo("原始回答包含一致性取舍");
    });
    assertThat(store.saved).singleElement().satisfies(saved -> {
      assertThat(saved.assessment().depthLevel()).isEqualTo(DepthLevel.L3);
      assertThat(saved.evidences()).containsExactly(
          new ValidatedAssessmentEvidence(
              EvidenceType.QUOTE,
              "一致性取舍",
              null
          )
      );
    });
    verify(algorithmEvidenceService).attachAvailable("session-old");
    verify(abilityProfileWriter).refresh("session-old");
  }

  private static final class RecordingStore implements AssessmentBackfillStore {

    private final List<AssessmentBackfillTurn> missing;
    private final List<SavedAssessment> saved = new ArrayList<>();

    private RecordingStore(List<AssessmentBackfillTurn> missing) {
      this.missing = missing;
    }

    @Override
    public List<AssessmentBackfillTurn> findMissing(String sessionId) {
      return missing;
    }

    @Override
    public void save(
        AssessmentBackfillTurn turn,
        AssessmentDecision assessment,
        List<ValidatedAssessmentEvidence> evidences
    ) {
      saved.add(new SavedAssessment(turn, assessment, evidences));
    }
  }

  private record SavedAssessment(
      AssessmentBackfillTurn turn,
      AssessmentDecision assessment,
      List<ValidatedAssessmentEvidence> evidences
  ) {}
}
