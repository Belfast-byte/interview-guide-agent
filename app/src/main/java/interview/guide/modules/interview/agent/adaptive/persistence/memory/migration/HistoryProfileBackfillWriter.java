package interview.guide.modules.interview.agent.adaptive.persistence.memory.migration;

import interview.guide.modules.interview.agent.adaptive.persistence.memory.migration.HistoryBackfillData.Counts;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.migration.HistoryBackfillData.LegacyProfile;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.migration.HistoryBackfillData.OwnerTopic;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.migration.HistoryBackfillData.ProfileBackfillInput;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class HistoryProfileBackfillWriter {

  private static final long PROFICIENT_WEIGHT = 3;
  private static final long COMPETENT_WEIGHT = 2;

  private static final String CURRENT_SELECT_SQL = """
      SELECT id, tenant_id, candidate_id, skill_id, focus_id, ability,
             l0_count, l1_count, l2_count, l3_count, l4_count,
             source_session_id, revision_reason
      FROM candidate_ability_profiles
      WHERE superseded_at IS NULL
      """;

  private static final String SUPERSEDE_SQL = """
      UPDATE candidate_ability_profiles SET superseded_at = ? WHERE id = ?
      """;

  private static final String INSERT_SQL = """
      INSERT INTO candidate_ability_profiles (
        tenant_id, candidate_id, skill_id, focus_id, ability,
        l0_count, l1_count, l2_count, l3_count, l4_count,
        source_session_id, revision_reason, superseded_at, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SESSION_COMPLETED', NULL, ?)
      """;

  void write(
      Connection connection,
      ProfileBackfillInput input
  ) throws SQLException {
    Map<OwnerTopic, LegacyProfile> sources = selectSources(input.legacyProfiles());
    Map<OwnerTopic, CurrentProfile> currents = readCurrents(connection);
    List<ProfileCreation> creations = sources.entrySet().stream()
        .map(item -> creation(item, input.counters()))
        .filter(creation -> !matches(currents.get(creation.ownerTopic()), creation))
        .toList();
    ProfileWritePlan plan = new ProfileWritePlan(
        creations, currents, input.migratedAt()
    );
    supersede(connection, plan);
    insert(connection, creations, input.migratedAt());
  }

  private Map<OwnerTopic, LegacyProfile> selectSources(
      List<LegacyProfile> legacyProfiles
  ) {
    Set<OwnerTopic> topics = new HashSet<>();
    Map<OwnerTopic, LegacyProfile> result = new HashMap<>();
    for (LegacyProfile profile : legacyProfiles) {
      topics.add(profile.ownerTopic());
      if (profile.current()) {
        result.merge(profile.ownerTopic(), profile, this::latest);
      }
    }
    if (!result.keySet().containsAll(topics)) {
      throw new IllegalStateException(
          "三层记忆历史迁移失败：Legacy Profile 主题缺 current 快照"
      );
    }
    return Map.copyOf(result);
  }

  private LegacyProfile latest(LegacyProfile left, LegacyProfile right) {
    int byCreatedAt = left.createdAt().compareTo(right.createdAt());
    if (byCreatedAt != 0) {
      return byCreatedAt > 0 ? left : right;
    }
    return left.id() > right.id() ? left : right;
  }

  private ProfileCreation creation(
      Map.Entry<OwnerTopic, LegacyProfile> source,
      Map<OwnerTopic, Counts> counters
  ) {
    Counts counts = counters.get(source.getKey());
    if (counts == null || counts.total() == 0) {
      throw new IllegalStateException(
          "三层记忆历史迁移失败：Legacy Profile 主题缺 AbilityCounter: "
              + source.getKey()
      );
    }
    return new ProfileCreation(
        source.getKey(),
        counts,
        ability(counts),
        source.getValue().sourceSessionId()
    );
  }

  private String ability(Counts counts) {
    if (counts.weighted() >= PROFICIENT_WEIGHT * counts.total()) {
      return "PROFICIENT";
    }
    if (counts.weighted() >= COMPETENT_WEIGHT * counts.total()) {
      return "COMPETENT";
    }
    return "WEAK";
  }

  private Map<OwnerTopic, CurrentProfile> readCurrents(Connection connection)
      throws SQLException {
    Map<OwnerTopic, CurrentProfile> result = new HashMap<>();
    try (PreparedStatement statement = connection.prepareStatement(CURRENT_SELECT_SQL);
         ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        putCurrent(result, rows);
      }
    }
    return result;
  }

  private void putCurrent(
      Map<OwnerTopic, CurrentProfile> result,
      ResultSet row
  ) throws SQLException {
    OwnerTopic topic = new OwnerTopic(
        row.getString("tenant_id"),
        row.getString("candidate_id"),
        row.getString("skill_id"),
        row.getString("focus_id")
    );
    CurrentProfile current = new CurrentProfile(
        row.getLong("id"),
        topic,
        row.getString("ability"),
        new Counts(
            row.getLong("l0_count"),
            row.getLong("l1_count"),
            row.getLong("l2_count"),
            row.getLong("l3_count"),
            row.getLong("l4_count")
        ),
        row.getString("source_session_id"),
        row.getString("revision_reason")
    );
    if (result.put(topic, current) != null) {
      throw new IllegalStateException(
          "三层记忆历史迁移失败：同一 owner + TopicKey 存在多个 current Profile"
      );
    }
  }

  private boolean matches(
      CurrentProfile current,
      ProfileCreation creation
  ) {
    if (current == null) {
      return false;
    }
    return current.ownerTopic().equals(creation.ownerTopic())
        && current.counts().equals(creation.counts())
        && Objects.equals(current.ability(), creation.ability())
        && Objects.equals(current.sourceSessionId(), creation.sourceSessionId())
        && Objects.equals(current.revisionReason(), "SESSION_COMPLETED");
  }

  private void supersede(
      Connection connection,
      ProfileWritePlan plan
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(SUPERSEDE_SQL)) {
      for (ProfileCreation creation : plan.creations()) {
        CurrentProfile current = plan.currents().get(creation.ownerTopic());
        if (current == null) {
          continue;
        }
        statement.setTimestamp(1, Timestamp.valueOf(plan.migratedAt()));
        statement.setLong(2, current.id());
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  private void insert(
      Connection connection,
      List<ProfileCreation> creations,
      LocalDateTime migratedAt
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
      for (ProfileCreation creation : creations) {
        bindCreation(statement, creation, migratedAt);
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  private void bindCreation(
      PreparedStatement statement,
      ProfileCreation creation,
      LocalDateTime migratedAt
  ) throws SQLException {
    OwnerTopic topic = creation.ownerTopic();
    Counts counts = creation.counts();
    statement.setString(1, topic.tenantId());
    statement.setString(2, topic.candidateId());
    statement.setString(3, topic.skillId());
    statement.setString(4, topic.focusId());
    statement.setString(5, creation.ability());
    statement.setLong(6, counts.l0());
    statement.setLong(7, counts.l1());
    statement.setLong(8, counts.l2());
    statement.setLong(9, counts.l3());
    statement.setLong(10, counts.l4());
    statement.setString(11, creation.sourceSessionId());
    statement.setTimestamp(12, Timestamp.valueOf(migratedAt));
  }

  private record CurrentProfile(
      long id,
      OwnerTopic ownerTopic,
      String ability,
      Counts counts,
      String sourceSessionId,
      String revisionReason
  ) {}

  private record ProfileCreation(
      OwnerTopic ownerTopic,
      Counts counts,
      String ability,
      String sourceSessionId
  ) {}

  private record ProfileWritePlan(
      List<ProfileCreation> creations,
      Map<OwnerTopic, CurrentProfile> currents,
      LocalDateTime migratedAt
  ) {}
}
