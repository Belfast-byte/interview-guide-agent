package interview.guide.modules.interview.agent.adaptive.persistence.memory.migration;

import interview.guide.modules.interview.agent.adaptive.persistence.memory.migration.HistoryBackfillData.Counts;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.migration.HistoryBackfillData.HistoricalAssessment;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.migration.HistoryBackfillData.HistoricalEpisode;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class HistoryBackfillWriter {

  private static final String EPISODE_SELECT_SQL = """
      SELECT session_id, turn_index, assessment_id, tenant_id, candidate_id,
             skill_id, focus_id
      FROM candidate_memory_episode_facts
      """;

  private static final String EPISODE_INSERT_SQL = """
      INSERT INTO candidate_memory_episode_facts (
        tenant_id, candidate_id, session_id, turn_index, assessment_id,
        skill_id, focus_id, enrichment_status, enrichment_error, version,
        created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, 'LEGACY_UNENRICHED', NULL, 0, ?, ?)
      """;

  private static final String COUNTER_SELECT_SQL = """
      SELECT id, tenant_id, candidate_id, skill_id, focus_id,
             l0_count, l1_count, l2_count, l3_count, l4_count
      FROM candidate_ability_counters
      """;

  private static final String COUNTER_INSERT_SQL = """
      INSERT INTO candidate_ability_counters (
        tenant_id, candidate_id, skill_id, focus_id,
        l0_count, l1_count, l2_count, l3_count, l4_count,
        version, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
      """;

  private static final String COUNTER_UPDATE_SQL = """
      UPDATE candidate_ability_counters
      SET l0_count = ?, l1_count = ?, l2_count = ?, l3_count = ?, l4_count = ?,
          version = version + 1, updated_at = ?
      WHERE id = ?
      """;

  private static final String LEGACY_TOPIC_UPDATE_SQL = """
      UPDATE legacy_candidate_ability_profiles
      SET skill_id = ?, focus_id = ?
      WHERE id = ?
      """;

  private final HistoryProfileBackfillWriter profileWriter =
      new HistoryProfileBackfillWriter();

  void write(
      Connection connection,
      HistoryBackfillData data,
      LocalDateTime migratedAt
  ) throws SQLException {
    updateLegacyTopics(connection, data.legacyProfiles());
    writeEpisodes(connection, data.episodes());
    Map<OwnerTopic, Counts> counters = aggregate(data.assessments());
    writeCounters(connection, counters, migratedAt);
    profileWriter.write(connection, new ProfileBackfillInput(
        data.legacyProfiles(), counters, migratedAt
    ));
  }

  private void updateLegacyTopics(
      Connection connection,
      List<LegacyProfile> profiles
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        LEGACY_TOPIC_UPDATE_SQL
    )) {
      for (LegacyProfile profile : profiles) {
        statement.setString(1, profile.ownerTopic().skillId());
        statement.setString(2, profile.ownerTopic().focusId());
        statement.setLong(3, profile.id());
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  private void writeEpisodes(
      Connection connection,
      List<HistoricalEpisode> episodes
  ) throws SQLException {
    Map<String, ExistingEpisode> existing = readEpisodes(connection);
    try (PreparedStatement statement = connection.prepareStatement(
        EPISODE_INSERT_SQL
    )) {
      for (HistoricalEpisode episode : episodes) {
        ExistingEpisode current = existing.get(turnKey(episode.assessment()));
        if (current != null) {
          validateEpisode(current, episode.assessment());
          continue;
        }
        bindEpisode(statement, episode);
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  private Map<String, ExistingEpisode> readEpisodes(Connection connection)
      throws SQLException {
    Map<String, ExistingEpisode> result = new HashMap<>();
    try (PreparedStatement statement = connection.prepareStatement(EPISODE_SELECT_SQL);
         ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        ExistingEpisode episode = new ExistingEpisode(
            rows.getLong("assessment_id"),
            new OwnerTopic(
                rows.getString("tenant_id"),
                rows.getString("candidate_id"),
                rows.getString("skill_id"),
                rows.getString("focus_id")
            )
        );
        String key = rows.getString("session_id") + "/" + rows.getInt("turn_index");
        result.put(key, episode);
      }
    }
    return result;
  }

  private void bindEpisode(
      PreparedStatement statement,
      HistoricalEpisode episode
  ) throws SQLException {
    HistoricalAssessment assessment = episode.assessment();
    OwnerTopic topic = assessment.ownerTopic();
    statement.setString(1, topic.tenantId());
    statement.setString(2, topic.candidateId());
    statement.setString(3, assessment.sessionId());
    statement.setInt(4, assessment.turnIndex());
    statement.setLong(5, assessment.assessmentId());
    statement.setString(6, topic.skillId());
    statement.setString(7, topic.focusId());
    Timestamp createdAt = Timestamp.valueOf(episode.answeredAt());
    statement.setTimestamp(8, createdAt);
    statement.setTimestamp(9, createdAt);
  }

  private void validateEpisode(
      ExistingEpisode existing,
      HistoricalAssessment assessment
  ) {
    boolean matches = existing.assessmentId() == assessment.assessmentId()
        && Objects.equals(existing.ownerTopic(), assessment.ownerTopic());
    if (!matches) {
      throw new IllegalStateException(
          "三层记忆历史迁移失败：现有 Episode 与权威 Assessment/plan 不一致: "
              + turnKey(assessment)
      );
    }
  }

  private Map<OwnerTopic, Counts> aggregate(
      List<HistoricalAssessment> assessments
  ) {
    Map<OwnerTopic, Counts> counters = new HashMap<>();
    for (HistoricalAssessment assessment : assessments) {
      counters.compute(
          assessment.ownerTopic(),
          (ignored, counts) -> (counts == null ? Counts.zero() : counts)
              .increment(assessment.depthLevel())
      );
    }
    return Map.copyOf(counters);
  }

  private void writeCounters(
      Connection connection,
      Map<OwnerTopic, Counts> counters,
      LocalDateTime migratedAt
  ) throws SQLException {
    Map<OwnerTopic, ExistingCounter> existing = readCounters(connection);
    try (PreparedStatement inserts = connection.prepareStatement(COUNTER_INSERT_SQL);
         PreparedStatement updates = connection.prepareStatement(COUNTER_UPDATE_SQL)) {
      for (Map.Entry<OwnerTopic, Counts> item : counters.entrySet()) {
        ExistingCounter current = existing.get(item.getKey());
        if (current == null) {
          bindCounterInsert(inserts, item, migratedAt);
          inserts.addBatch();
        } else if (!current.counts().equals(item.getValue())) {
          bindCounterUpdate(updates, new CounterUpdate(
              item.getValue(), migratedAt, current.id()
          ));
          updates.addBatch();
        }
      }
      updates.executeBatch();
      inserts.executeBatch();
    }
  }

  private Map<OwnerTopic, ExistingCounter> readCounters(Connection connection)
      throws SQLException {
    Map<OwnerTopic, ExistingCounter> result = new HashMap<>();
    try (PreparedStatement statement = connection.prepareStatement(COUNTER_SELECT_SQL);
         ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        OwnerTopic topic = new OwnerTopic(
            rows.getString("tenant_id"),
            rows.getString("candidate_id"),
            rows.getString("skill_id"),
            rows.getString("focus_id")
        );
        result.put(topic, new ExistingCounter(
            rows.getLong("id"),
            new Counts(
                rows.getLong("l0_count"),
                rows.getLong("l1_count"),
                rows.getLong("l2_count"),
                rows.getLong("l3_count"),
                rows.getLong("l4_count")
            )
        ));
      }
    }
    return result;
  }

  private void bindCounterInsert(
      PreparedStatement statement,
      Map.Entry<OwnerTopic, Counts> item,
      LocalDateTime migratedAt
  ) throws SQLException {
    OwnerTopic topic = item.getKey();
    statement.setString(1, topic.tenantId());
    statement.setString(2, topic.candidateId());
    statement.setString(3, topic.skillId());
    statement.setString(4, topic.focusId());
    bindCounts(statement, item.getValue(), 5);
    statement.setTimestamp(10, Timestamp.valueOf(migratedAt));
    statement.setTimestamp(11, Timestamp.valueOf(migratedAt));
  }

  private void bindCounterUpdate(
      PreparedStatement statement,
      CounterUpdate update
  ) throws SQLException {
    bindCounts(statement, update.counts(), 1);
    statement.setTimestamp(6, Timestamp.valueOf(update.migratedAt()));
    statement.setLong(7, update.id());
  }

  private void bindCounts(
      PreparedStatement statement,
      Counts counts,
      int startIndex
  ) throws SQLException {
    statement.setLong(startIndex, counts.l0());
    statement.setLong(startIndex + 1, counts.l1());
    statement.setLong(startIndex + 2, counts.l2());
    statement.setLong(startIndex + 3, counts.l3());
    statement.setLong(startIndex + 4, counts.l4());
  }

  private String turnKey(HistoricalAssessment assessment) {
    return assessment.sessionId() + "/" + assessment.turnIndex();
  }

  private record ExistingEpisode(long assessmentId, OwnerTopic ownerTopic) {}

  private record ExistingCounter(long id, Counts counts) {}

  private record CounterUpdate(
      Counts counts,
      LocalDateTime migratedAt,
      long id
  ) {}
}
