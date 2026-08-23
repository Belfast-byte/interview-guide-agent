package interview.guide.modules.interview.agent.adaptive.persistence.memory.migration;

import java.time.LocalDateTime;
import java.util.List;

record HistoryBackfillData(
    List<HistoricalAssessment> assessments,
    List<HistoricalEpisode> episodes,
    List<LegacyProfile> legacyProfiles
) {

  record OwnerTopic(
      String tenantId,
      String candidateId,
      String skillId,
      String focusId
  ) {}

  record HistoricalAssessment(
      long assessmentId,
      String sessionId,
      int turnIndex,
      OwnerTopic ownerTopic,
      String depthLevel
  ) {}

  record HistoricalEpisode(
      HistoricalAssessment assessment,
      LocalDateTime answeredAt
  ) {}

  record LegacyProfile(
      long id,
      OwnerTopic ownerTopic,
      String sourceSessionId,
      boolean current,
      LocalDateTime createdAt
  ) {}

  record Counts(long l0, long l1, long l2, long l3, long l4) {

    private static final long L2_WEIGHT = 2;
    private static final long L3_WEIGHT = 3;
    private static final long L4_WEIGHT = 4;

    static Counts zero() {
      return new Counts(0, 0, 0, 0, 0);
    }

    Counts increment(String depthLevel) {
      return switch (depthLevel) {
        case "L0" -> new Counts(l0 + 1, l1, l2, l3, l4);
        case "L1" -> new Counts(l0, l1 + 1, l2, l3, l4);
        case "L2" -> new Counts(l0, l1, l2 + 1, l3, l4);
        case "L3" -> new Counts(l0, l1, l2, l3 + 1, l4);
        case "L4" -> new Counts(l0, l1, l2, l3, l4 + 1);
        default -> throw new IllegalStateException(
            "历史迁移失败：未知 Assessment depthLevel=" + depthLevel
        );
      };
    }

    long total() {
      return l0 + l1 + l2 + l3 + l4;
    }

    long weighted() {
      return l1 + L2_WEIGHT * l2 + L3_WEIGHT * l3 + L4_WEIGHT * l4;
    }
  }

  record ProfileBackfillInput(
      List<LegacyProfile> legacyProfiles,
      java.util.Map<OwnerTopic, Counts> counters,
      LocalDateTime migratedAt
  ) {}
}
