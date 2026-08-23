package interview.guide.modules.interview.agent.adaptive.persistence.memory.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 将三层记忆表上线前的自适应面试历史确定性回填到新模型。
 */
public final class ThreeLayerMemoryHistoryBackfill {

  private static final String ADD_LEGACY_SKILL_SQL = """
      ALTER TABLE legacy_candidate_ability_profiles
      ADD COLUMN IF NOT EXISTS skill_id VARCHAR(64)
      """;

  private static final String ADD_LEGACY_FOCUS_SQL = """
      ALTER TABLE legacy_candidate_ability_profiles
      ADD COLUMN IF NOT EXISTS focus_id VARCHAR(64)
      """;

  private static final String REQUIRE_LEGACY_SKILL_SQL = """
      ALTER TABLE legacy_candidate_ability_profiles
      ALTER COLUMN skill_id SET NOT NULL
      """;

  private static final String REQUIRE_LEGACY_FOCUS_SQL = """
      ALTER TABLE legacy_candidate_ability_profiles
      ALTER COLUMN focus_id SET NOT NULL
      """;

  private final Clock clock;
  private final HistoryBackfillReader reader = new HistoryBackfillReader();
  private final HistoryBackfillWriter writer = new HistoryBackfillWriter();

  public ThreeLayerMemoryHistoryBackfill() {
    this(Clock.systemDefaultZone());
  }

  ThreeLayerMemoryHistoryBackfill(Clock clock) {
    this.clock = clock;
  }

  public void migrate(Connection connection) throws SQLException {
    prepareLegacyTopicColumns(connection);
    HistoryBackfillData data = reader.read(connection);
    writer.write(connection, data, LocalDateTime.now(clock));
    requireLegacyTopicColumns(connection);
  }

  private void prepareLegacyTopicColumns(Connection connection) throws SQLException {
    execute(connection, ADD_LEGACY_SKILL_SQL);
    execute(connection, ADD_LEGACY_FOCUS_SQL);
  }

  private void requireLegacyTopicColumns(Connection connection) throws SQLException {
    execute(connection, REQUIRE_LEGACY_SKILL_SQL);
    execute(connection, REQUIRE_LEGACY_FOCUS_SQL);
  }

  private void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }
}
