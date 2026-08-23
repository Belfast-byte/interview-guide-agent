package interview.guide.modules.interview.agent.adaptive.persistence.memory.migration;

import interview.guide.modules.interview.agent.adaptive.persistence.memory.migration.HistoryBackfillData.LegacyProfile;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 回填 legacy Profile 的稳定主题，并在 counter-v1 快照生成后关闭旧 current。
 */
final class HistoryLegacyProfileWriter {

  private static final String TOPIC_UPDATE_SQL = """
      UPDATE legacy_candidate_ability_profiles
      SET skill_id = ?, focus_id = ?
      WHERE id = ?
      """;

  private static final String SUPERSEDE_CURRENT_SQL = """
      UPDATE legacy_candidate_ability_profiles
      SET superseded_at = ?
      WHERE superseded_by IS NULL AND superseded_at IS NULL
      """;

  void updateTopics(
      Connection connection,
      List<LegacyProfile> profiles
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(TOPIC_UPDATE_SQL)) {
      for (LegacyProfile profile : profiles) {
        statement.setString(1, profile.ownerTopic().skillId());
        statement.setString(2, profile.ownerTopic().focusId());
        statement.setLong(3, profile.id());
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  void markCurrentSuperseded(
      Connection connection,
      LocalDateTime migratedAt
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        SUPERSEDE_CURRENT_SQL
    )) {
      statement.setTimestamp(1, Timestamp.valueOf(migratedAt));
      statement.executeUpdate();
    }
  }
}
