package interview.guide.modules.interview.agent.adaptive.persistence.memory.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class ThreeLayerMemoryHistoryBackfillIntegrationTest {

  @Test
  void 完整回填可重复且不根据同名展示文本映射() throws Exception {
    Fixture fixture = fixture();
    seedCompleteHistory(fixture.jdbc());
    migrate(fixture.dataSource());
    rerunBackfill(fixture.dataSource());
    assertThat(fixture.jdbc().queryForObject(
        "SELECT COUNT(*) FROM candidate_memory_episode_facts",
        Integer.class
    )).isEqualTo(3);
    assertThat(fixture.jdbc().queryForList("""
        SELECT session_id, skill_id, focus_id, enrichment_status
        FROM candidate_memory_episode_facts
        ORDER BY session_id, turn_index
        """)).containsExactly(
            Map.of("session_id", "session-a", "skill_id", "java",
                "focus_id", "focus-java", "enrichment_status", "LEGACY_UNENRICHED"),
            Map.of("session_id", "session-a", "skill_id", "java",
                "focus_id", "focus-java", "enrichment_status", "LEGACY_UNENRICHED"),
            Map.of("session_id", "session-b", "skill_id", "go",
                "focus_id", "focus-go", "enrichment_status", "LEGACY_UNENRICHED")
        );
    assertCounters(fixture.jdbc());
    assertProfiles(fixture.jdbc());
    assertLegacyTopics(fixture.jdbc());
  }

  @Test
  void 缺少plan时显式失败() {
    Fixture fixture = fixture();
    insertSession(fixture.jdbc(), "missing-plan");
    insertTurn(fixture.jdbc(), "missing-plan", 1);
    insertAssessment(fixture.jdbc(), new AssessmentSeed(
        1, "missing-plan", 1, 1, "L2"
    ));

    assertMigrationFails(fixture.dataSource(), "缺 agent_plan: missing-plan/1");
  }

  @Test
  void 缺少skill时显式失败() {
    Fixture fixture = fixture();
    seedSingleAssessment(fixture.jdbc(), new PlanSeed(
        "missing-skill", null, "focus-id"
    ));

    assertMigrationFails(fixture.dataSource(), "Plan 缺 suggested_skill");
  }

  @Test
  void 缺少focus时显式失败() {
    Fixture fixture = fixture();
    seedSingleAssessment(fixture.jdbc(), new PlanSeed(
        "missing-focus", "java", null
    ));

    assertMigrationFails(fixture.dataSource(), "Plan 缺 focus_id");
  }

  @Test
  void answeredTurn缺少assessment时显式失败() {
    Fixture fixture = fixture();
    insertSession(fixture.jdbc(), "missing-assessment");
    insertPlan(fixture.jdbc(), new PlanSeed(
        "missing-assessment", "java", "focus-id"
    ));
    insertTurn(fixture.jdbc(), "missing-assessment", 1);

    assertMigrationFails(fixture.dataSource(), "answered turn 缺 Assessment");
  }

  private Fixture fixture() {
    String url = "jdbc:h2:mem:t23_" + UUID.randomUUID()
        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
    new ResourceDatabasePopulator(new ClassPathResource(
        "db/migration/t23-history-backfill-schema.sql"
    ))
        .execute(dataSource);
    return new Fixture(dataSource, new JdbcTemplate(dataSource));
  }

  private void migrate(DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .baselineOnMigrate(true)
        .baselineVersion("20260920")
        .target("20260921")
        .load()
        .migrate();
  }

  private void rerunBackfill(DataSource dataSource) throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      new ThreeLayerMemoryHistoryBackfill().migrate(connection);
    }
  }

  private void assertMigrationFails(DataSource dataSource, String message) {
    Throwable failure = catchThrowable(() -> migrate(dataSource));
    assertThat(rootCause(failure)).hasMessageContaining(message);
  }

  private Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private void seedCompleteHistory(JdbcTemplate jdbc) {
    insertSession(jdbc, "session-a");
    insertPlan(jdbc, new PlanSeed("session-a", "java", "focus-java"));
    insertTurn(jdbc, "session-a", 1);
    insertTurn(jdbc, "session-a", 2);
    insertAssessment(jdbc, new AssessmentSeed(101, "session-a", 1, 1, "L1"));
    insertAssessment(jdbc, new AssessmentSeed(102, "session-a", 2, 1, "L3"));
    insertLegacyProfile(jdbc, new LegacyProfileSeed(201, "session-a", 102));
    insertStaleCurrentProfile(jdbc);

    insertSession(jdbc, "session-b");
    insertPlan(jdbc, new PlanSeed("session-b", "go", "focus-go"));
    insertTurn(jdbc, "session-b", 1);
    insertAssessment(jdbc, new AssessmentSeed(103, "session-b", 1, 1, "L4"));
    insertLegacyProfile(jdbc, new LegacyProfileSeed(202, "session-b", 103));
  }

  private void seedSingleAssessment(
      JdbcTemplate jdbc,
      PlanSeed plan
  ) {
    insertSession(jdbc, plan.sessionId());
    insertPlan(jdbc, plan);
    insertTurn(jdbc, plan.sessionId(), 1);
    insertAssessment(jdbc, new AssessmentSeed(1, plan.sessionId(), 1, 1, "L2"));
  }

  private void insertSession(JdbcTemplate jdbc, String sessionId) {
    jdbc.update(
        "INSERT INTO agent_sessions (id, tenant_id, candidate_id) VALUES (?, ?, ?)",
        sessionId, "tenant-a", "candidate-a"
    );
  }

  private void insertPlan(JdbcTemplate jdbc, PlanSeed plan) {
    jdbc.update("""
        INSERT INTO agent_plans (
          session_id, dimension_order, dimension, focus, suggested_skill, focus_id
        ) VALUES (?, 1, '同名展示维度', '同名展示焦点', ?, ?)
        """, plan.sessionId(), plan.skillId(), plan.focusId());
  }

  private void insertTurn(JdbcTemplate jdbc, String sessionId, int turnIndex) {
    jdbc.update("""
        INSERT INTO agent_turns (
          session_id, turn_index, answer, answered_at, created_at
        ) VALUES (?, ?, 'answer', TIMESTAMP '2026-01-01 10:00:00',
          TIMESTAMP '2026-01-01 09:00:00')
        """, sessionId, turnIndex);
  }

  private void insertAssessment(
      JdbcTemplate jdbc,
      AssessmentSeed assessment
  ) {
    jdbc.update("""
        INSERT INTO agent_assessments (
          id, session_id, turn_index, dimension_order, depth_level, created_at
        ) VALUES (?, ?, ?, ?, ?, TIMESTAMP '2026-01-01 10:01:00')
        """, assessment.id(), assessment.sessionId(), assessment.turnIndex(),
        assessment.dimensionOrder(), assessment.depthLevel());
  }

  private void insertLegacyProfile(
      JdbcTemplate jdbc,
      LegacyProfileSeed profile
  ) {
    jdbc.update("""
        INSERT INTO legacy_candidate_ability_profiles (
          id, tenant_id, candidate_id, dimension, dimension_order, depth_level,
          source_session_id, source_assessment_id, superseded_by, created_at
        ) VALUES (?, 'tenant-a', 'candidate-a', '同名展示维度', 1, 'L4',
          ?, ?, NULL, TIMESTAMP '2026-01-02 10:00:00')
        """, profile.id(), profile.sessionId(), profile.assessmentId());
  }

  private void insertStaleCurrentProfile(JdbcTemplate jdbc) {
    jdbc.update("""
        INSERT INTO candidate_ability_profiles (
          tenant_id, candidate_id, skill_id, focus_id, ability,
          l0_count, l1_count, l2_count, l3_count, l4_count,
          source_session_id, revision_reason, superseded_at, created_at
        ) VALUES ('tenant-a', 'candidate-a', 'java', 'focus-java', 'WEAK',
          1, 0, 0, 0, 0, 'session-a', 'SESSION_COMPLETED', NULL,
          TIMESTAMP '2026-01-01 08:00:00')
        """);
  }

  private void assertCounters(JdbcTemplate jdbc) {
    assertThat(jdbc.queryForList("""
        SELECT skill_id, focus_id, l0_count, l1_count, l2_count, l3_count, l4_count, version
        FROM candidate_ability_counters ORDER BY skill_id
        """)).containsExactly(
            counter(new CounterExpectation("go", "focus-go", 0, 0, 0, 0, 1)),
            counter(new CounterExpectation("java", "focus-java", 0, 1, 0, 1, 0))
        );
  }

  private Map<String, Object> counter(CounterExpectation counter) {
    return Map.of(
        "skill_id", counter.skillId(), "focus_id", counter.focusId(),
        "l0_count", counter.l0(), "l1_count", counter.l1(),
        "l2_count", counter.l2(), "l3_count", counter.l3(),
        "l4_count", counter.l4(), "version", 0L
    );
  }

  private void assertProfiles(JdbcTemplate jdbc) {
    assertThat(jdbc.queryForList("""
        SELECT skill_id, focus_id, ability, revision_reason
        FROM candidate_ability_profiles
        WHERE superseded_at IS NULL ORDER BY skill_id
        """)).containsExactly(
            Map.of("skill_id", "go", "focus_id", "focus-go",
                "ability", "PROFICIENT", "revision_reason", "SESSION_COMPLETED"),
            Map.of("skill_id", "java", "focus_id", "focus-java",
                "ability", "COMPETENT", "revision_reason", "SESSION_COMPLETED")
        );
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM candidate_ability_profiles",
        Integer.class
    )).isEqualTo(3);
    assertThat(jdbc.queryForObject("""
        SELECT COUNT(*) FROM candidate_ability_profiles WHERE superseded_at IS NOT NULL
        """, Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("""
        SELECT l1_count + l3_count FROM candidate_ability_profiles
        WHERE skill_id = 'java' AND superseded_at IS NULL
        """, Long.class)).isEqualTo(2L);
  }

  private void assertLegacyTopics(JdbcTemplate jdbc) {
    assertThat(jdbc.queryForList("""
        SELECT skill_id, focus_id, superseded_at IS NOT NULL AS superseded
        FROM legacy_candidate_ability_profiles ORDER BY id
        """)).containsExactly(
            Map.of("skill_id", "java", "focus_id", "focus-java", "superseded", true),
            Map.of("skill_id", "go", "focus_id", "focus-go", "superseded", true)
        );
  }

  private record Fixture(DataSource dataSource, JdbcTemplate jdbc) {}

  private record PlanSeed(String sessionId, String skillId, String focusId) {}

  private record AssessmentSeed(
      long id,
      String sessionId,
      int turnIndex,
      int dimensionOrder,
      String depthLevel
  ) {}

  private record LegacyProfileSeed(
      long id,
      String sessionId,
      long assessmentId
  ) {}

  private record CounterExpectation(
      String skillId,
      String focusId,
      long l0,
      long l1,
      long l2,
      long l3,
      long l4
  ) {}
}
