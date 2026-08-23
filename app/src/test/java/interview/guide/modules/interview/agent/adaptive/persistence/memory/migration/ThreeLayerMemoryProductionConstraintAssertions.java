package interview.guide.modules.interview.agent.adaptive.persistence.memory.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.SQLException;
import java.util.Locale;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.jdbc.core.JdbcTemplate;

final class ThreeLayerMemoryProductionConstraintAssertions {

  private static final long SECOND_ASSESSMENT_ID = 102L;
  private static final long FIRST_PROBE_GAP_ID = 201L;
  private static final long SECOND_PROBE_GAP_ID = 202L;
  private static final long UNKNOWN_ASSESSMENT_ID = 999_999L;
  private static final String UNIQUE_VIOLATION = "23505";
  private static final String FOREIGN_KEY_VIOLATION = "23503";
  private static final String CHECK_VIOLATION = "23514";

  private final JdbcTemplate jdbc;
  private final String schema;
  private final Context context;

  ThreeLayerMemoryProductionConstraintAssertions(
      JdbcTemplate jdbc,
      String schema,
      Context context
  ) {
    this.jdbc = jdbc;
    this.schema = schema;
    this.context = context;
  }

  void verify() {
    assertCounterNullTenantUnique();
    assertProfileCurrentUnique();
    assertEpisodeConstraints();
    assertProbeGapTurnConstraints();
  }

  private void assertCounterNullTenantUnique() {
    assertConstraintViolation(new ConstraintViolation(
        "uk_ability_counter_owner_topic", UNIQUE_VIOLATION
    ), () -> jdbc.update("""
        INSERT INTO %s (
          tenant_id, candidate_id, skill_id, focus_id, l0_count, l1_count,
          l2_count, l3_count, l4_count, version, created_at, updated_at
        ) VALUES (NULL, ?, ?, ?, 0, 0, 1, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """.formatted(table("candidate_ability_counters")),
        context.candidateId(), context.skillId(), context.focusId()));
  }

  private void assertProfileCurrentUnique() {
    assertThat(insertProfile("CURRENT_TIMESTAMP")).isOne();
    assertConstraintViolation(new ConstraintViolation(
        "uk_ability_profile_current_owner_topic", UNIQUE_VIOLATION
    ), () -> insertProfile("NULL"));
  }

  private int insertProfile(String supersededAt) {
    return jdbc.update("""
        INSERT INTO %s (
          tenant_id, candidate_id, skill_id, focus_id, ability, l0_count, l1_count,
          l2_count, l3_count, l4_count, source_session_id, revision_reason,
          superseded_at, created_at
        ) VALUES (NULL, ?, ?, ?, 'COMPETENT', 0, 0, 1, 0, 0, ?,
          'SESSION_COMPLETED', %s, CURRENT_TIMESTAMP)
        """.formatted(table("candidate_ability_profiles"), supersededAt),
        context.candidateId(), context.skillId(), context.focusId(), context.sessionId());
  }

  private void assertEpisodeConstraints() {
    assertConstraintViolation(new ConstraintViolation(
        "uk_memory_episode_session_turn", UNIQUE_VIOLATION
    ), () -> insertEpisode(new EpisodeInsert(
        context.sessionId(), 1, context.assessmentId(), "PENDING"
    )));
    assertConstraintViolation(new ConstraintViolation(
        "fk_memory_episode_turn", FOREIGN_KEY_VIOLATION
    ), () -> insertEpisode(new EpisodeInsert(
        "missing-session", 2, context.assessmentId(), "PENDING"
    )));
    assertConstraintViolation(new ConstraintViolation(
        "fk_memory_episode_assessment", FOREIGN_KEY_VIOLATION
    ), () -> insertEpisode(new EpisodeInsert(
        context.sessionId(), 2, UNKNOWN_ASSESSMENT_ID, "PENDING"
    )));
    assertConstraintViolation(new ConstraintViolation(
        "memory_episode_enrichment_status_check", CHECK_VIOLATION
    ), () -> insertEpisode(new EpisodeInsert(
        context.sessionId(), 2, context.assessmentId(), "INVALID"
    )));
  }

  private int insertEpisode(EpisodeInsert episode) {
    return jdbc.update("""
        INSERT INTO %s (
          tenant_id, candidate_id, session_id, turn_index, assessment_id,
          skill_id, focus_id, enrichment_status, version, created_at, updated_at
        ) VALUES (NULL, ?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """.formatted(table("candidate_memory_episode_facts")),
        context.candidateId(), episode.sessionId(), episode.turnIndex(),
        episode.assessmentId(), context.skillId(), context.focusId(), episode.status());
  }

  private void assertProbeGapTurnConstraints() {
    insertSecondAssessment();
    insertProbeGap(FIRST_PROBE_GAP_ID, 1);
    insertProbeGap(SECOND_PROBE_GAP_ID, 2);
    assertThat(insertTriggerTurn(new TriggerTurn(
        3, 1, context.assessmentId(), FIRST_PROBE_GAP_ID
    ))).isOne();
    assertConstraintViolation(new ConstraintViolation(
        "uk_agent_turn_source_probe_gap", UNIQUE_VIOLATION
    ), () -> insertTriggerTurn(new TriggerTurn(
        4, 1, context.assessmentId(), FIRST_PROBE_GAP_ID
    )));
    assertConstraintViolation(new ConstraintViolation(
        "fk_agent_turn_source_probe_gap", FOREIGN_KEY_VIOLATION
    ), () -> insertTriggerTurn(new TriggerTurn(
        4, 2, SECOND_ASSESSMENT_ID, SECOND_PROBE_GAP_ID
    )));
    assertConstraintViolation(new ConstraintViolation(
        "agent_turn_trigger_check", CHECK_VIOLATION
    ), () -> insertTriggerTurn(new TriggerTurn(
        4, 2, SECOND_ASSESSMENT_ID, null
    )));
  }

  private void insertSecondAssessment() {
    jdbc.update("""
        INSERT INTO %s (
          id, session_id, turn_index, dimension_order, depth_level,
          confidence, rationale_summary, created_at
        ) VALUES (?, ?, 2, 1, 'L1', 0.800, '约束测试评估', CURRENT_TIMESTAMP)
        """.formatted(table("agent_assessments")),
        SECOND_ASSESSMENT_ID, context.sessionId());
  }

  private void insertProbeGap(long gapId, int gapOrder) {
    jdbc.update("""
        INSERT INTO %s (
          id, assessment_id, gap_order, gap_code, anchor, description, created_at
        ) VALUES (?, ?, ?, 'MISSING_DEPTH', '锚点', '描述', CURRENT_TIMESTAMP)
        """.formatted(table("agent_assessment_probe_gaps")),
        gapId, context.assessmentId(), gapOrder);
  }

  private int insertTriggerTurn(TriggerTurn turn) {
    return jdbc.update("""
        INSERT INTO %s (
          session_id, turn_index, dimension_order, question, created_at,
          parent_turn_index, trigger_type, source_assessment_id, source_probe_gap_id
        ) VALUES (?, ?, 1, '追问', CURRENT_TIMESTAMP, ?, 'ASSESSMENT_GAP', ?, ?)
        """.formatted(table("agent_turns")), context.sessionId(), turn.turnIndex(),
        turn.parentTurnIndex(), turn.assessmentId(), turn.probeGapId());
  }

  private void assertConstraintViolation(
      ConstraintViolation expected,
      ThrowingCallable operation
  ) {
    Throwable failure = catchThrowable(operation);
    assertThat(failure).isNotNull();
    Throwable root = rootCause(failure);
    assertThat(root).isInstanceOf(SQLException.class);
    assertThat(((SQLException) root).getSQLState()).isEqualTo(expected.sqlState());
    assertThat(root.getMessage().toLowerCase(Locale.ROOT)).contains(expected.name());
  }

  private Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private String table(String name) {
    return schema + "." + name;
  }

  record Context(
      String sessionId,
      long assessmentId,
      String candidateId,
      String skillId,
      String focusId
  ) {}

  private record EpisodeInsert(
      String sessionId,
      int turnIndex,
      long assessmentId,
      String status
  ) {}

  private record TriggerTurn(
      int turnIndex,
      int parentTurnIndex,
      long assessmentId,
      Long probeGapId
  ) {}

  private record ConstraintViolation(String name, String sqlState) {}
}
