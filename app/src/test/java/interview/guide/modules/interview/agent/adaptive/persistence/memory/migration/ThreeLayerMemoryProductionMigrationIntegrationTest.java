package interview.guide.modules.interview.agent.adaptive.persistence.memory.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@EnabledIfEnvironmentVariable(
    named = "MEMORY_MIGRATION_PG_JDBC_URL",
    matches = "jdbc:postgresql:.+",
    disabledReason = "生产迁移使用 PostgreSQL 专有索引语义，H2 无法解析 NULLS NOT DISTINCT"
)
@EnabledIfEnvironmentVariable(named = "MEMORY_MIGRATION_PG_USER", matches = ".+")
@EnabledIfEnvironmentVariable(named = "MEMORY_MIGRATION_PG_PASSWORD", matches = ".*")
class ThreeLayerMemoryProductionMigrationIntegrationTest {

  private static final String SESSION_ID = "production-migration-session";
  private static final long ASSESSMENT_ID = 101L;
  private static final List<String> EXPECTED_VERSIONS = List.of(
      "20260918", "20260919", "20260920", "20260921", "20260922");
  private static final String CANDIDATE_ID = "candidate-null-tenant";
  private static final String SKILL_ID = "java";
  private static final String FOCUS_ID = "concurrency";

  @Test
  void 生产迁移从V17真实结构回填并建立约束() throws Exception {
    DataSource dataSource = dataSource();
    String schema = "memory_migration_" + UUID.randomUUID().toString().replace("-", "");
    createSchema(dataSource, schema);
    try {
      loadLegacySchema(dataSource, schema);
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      seedHistory(jdbc, schema);
      assertNewTablesAbsent(jdbc, schema);
      migrate(dataSource, schema);
      assertMigrationsExecuted(jdbc, schema);
      assertBackfill(jdbc, schema);
      assertProductionConstraints(jdbc, schema);
    } finally {
      dropSchema(dataSource, schema);
    }
  }

  private DataSource dataSource() {
    return new DriverManagerDataSource(
        System.getenv("MEMORY_MIGRATION_PG_JDBC_URL"),
        System.getenv("MEMORY_MIGRATION_PG_USER"),
        System.getenv("MEMORY_MIGRATION_PG_PASSWORD")
    );
  }

  private void createSchema(DataSource dataSource, String schema) {
    new JdbcTemplate(dataSource).execute("CREATE SCHEMA " + schema);
  }

  private void loadLegacySchema(DataSource dataSource, String schema) throws SQLException {
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
        new ClassPathResource(
            "db/production-gate/v20260917-three-layer-memory-legacy-schema.sql"
        )
    );
    try (Connection connection = dataSource.getConnection()) {
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET search_path TO " + schema);
      }
      populator.populate(connection);
    }
  }

  private void seedHistory(JdbcTemplate jdbc, String schema) {
    jdbc.update("""
        INSERT INTO %s (id, tenant_id, candidate_id, status, created_at, completed_at)
        VALUES (?, NULL, ?, 'COMPLETED', TIMESTAMP '2026-01-01 09:00:00',
          TIMESTAMP '2026-01-01 10:02:00')
        """.formatted(table(schema, "agent_sessions")), SESSION_ID, CANDIDATE_ID);
    jdbc.update("""
        INSERT INTO %s (
          session_id, dimension_order, dimension, focus, suggested_skill, focus_id
        ) VALUES (?, 1, '并发', '线程安全', ?, ?)
        """.formatted(table(schema, "agent_plans")), SESSION_ID, SKILL_ID, FOCUS_ID);
    seedTurns(jdbc, schema);
    jdbc.update("""
        INSERT INTO %s (
          id, session_id, turn_index, dimension_order, depth_level,
          confidence, rationale_summary, created_at
        ) VALUES (?, ?, 1, 1, 'L2', 0.900, '历史评估',
          TIMESTAMP '2026-01-01 10:01:00')
        """.formatted(table(schema, "agent_assessments")), ASSESSMENT_ID, SESSION_ID);
    jdbc.update("""
        INSERT INTO %s (
          tenant_id, candidate_id, dimension, dimension_order, depth_level,
          source_session_id, source_assessment_id, superseded_by, created_at
        ) VALUES (NULL, ?, '并发', 1, 'L2', ?, ?, NULL,
          TIMESTAMP '2026-01-01 10:02:00')
        """.formatted(table(schema, "candidate_ability_profiles")),
        CANDIDATE_ID, SESSION_ID, ASSESSMENT_ID);
  }

  private void seedTurns(JdbcTemplate jdbc, String schema) {
    jdbc.update("""
        INSERT INTO %s (
          session_id, turn_index, dimension_order, question, answer, created_at, answered_at
        ) VALUES (?, 1, 1, '问题', '回答', TIMESTAMP '2026-01-01 09:30:00',
          TIMESTAMP '2026-01-01 10:00:00')
        """.formatted(table(schema, "agent_turns")), SESSION_ID);
    jdbc.update("""
        INSERT INTO %s (
          session_id, turn_index, dimension_order, question, answer, created_at, answered_at
        ) VALUES (?, 2, 1, '未回答问题', NULL, TIMESTAMP '2026-01-01 10:03:00', NULL)
        """.formatted(table(schema, "agent_turns")), SESSION_ID);
  }

  private void assertNewTablesAbsent(JdbcTemplate jdbc, String schema) {
    Integer count = jdbc.queryForObject("""
        SELECT COUNT(*) FROM information_schema.tables
        WHERE table_schema = ? AND table_name IN (
          'candidate_memory_episode_facts', 'candidate_ability_counters',
          'candidate_memory_episode_tags'
        )
        """, Integer.class, schema);
    assertThat(count).isZero();
  }

  private void migrate(DataSource dataSource, String schema) {
    int executed = Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .schemas(schema)
        .defaultSchema(schema)
        .createSchemas(false)
        .baselineOnMigrate(true)
        .baselineVersion("20260917")
        .load()
        .migrate()
        .migrationsExecuted;
    assertThat(executed).isEqualTo(EXPECTED_VERSIONS.size());
  }

  private void assertMigrationsExecuted(JdbcTemplate jdbc, String schema) {
    List<String> versions = jdbc.queryForList("""
        SELECT version FROM %s
        WHERE type <> 'BASELINE' AND success
        ORDER BY installed_rank
        """.formatted(table(schema, "flyway_schema_history")), String.class);
    assertThat(versions).containsExactlyElementsOf(EXPECTED_VERSIONS);
  }

  private void assertBackfill(JdbcTemplate jdbc, String schema) {
    assertThat(jdbc.queryForMap("""
        SELECT skill_id, focus_id, enrichment_status, assessment_id
        FROM %s
        """.formatted(table(schema, "candidate_memory_episode_facts"))))
        .containsEntry("skill_id", SKILL_ID)
        .containsEntry("focus_id", FOCUS_ID)
        .containsEntry("enrichment_status", "LEGACY_UNENRICHED")
        .containsEntry("assessment_id", ASSESSMENT_ID);
    assertThat(jdbc.queryForMap("""
        SELECT l0_count, l1_count, l2_count, l3_count, l4_count
        FROM %s
        """.formatted(table(schema, "candidate_ability_counters"))))
        .containsEntry("l0_count", 0L)
        .containsEntry("l1_count", 0L)
        .containsEntry("l2_count", 1L)
        .containsEntry("l3_count", 0L)
        .containsEntry("l4_count", 0L);
    assertThat(jdbc.queryForMap("""
        SELECT ability, revision_reason FROM %s WHERE superseded_at IS NULL
        """.formatted(table(schema, "candidate_ability_profiles"))))
        .containsEntry("ability", "COMPETENT")
        .containsEntry("revision_reason", "SESSION_COMPLETED");
  }

  private void assertProductionConstraints(JdbcTemplate jdbc, String schema) {
    var context = new ThreeLayerMemoryProductionConstraintAssertions.Context(
        SESSION_ID, ASSESSMENT_ID, CANDIDATE_ID, SKILL_ID, FOCUS_ID
    );
    new ThreeLayerMemoryProductionConstraintAssertions(jdbc, schema, context)
        .verify();
  }

  private void dropSchema(DataSource dataSource, String schema) {
    new JdbcTemplate(dataSource).execute("DROP SCHEMA " + schema + " CASCADE");
  }

  private String table(String schema, String table) {
    return schema + "." + table;
  }

}
