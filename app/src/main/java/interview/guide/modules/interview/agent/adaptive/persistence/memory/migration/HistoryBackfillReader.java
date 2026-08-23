package interview.guide.modules.interview.agent.adaptive.persistence.memory.migration;

import interview.guide.modules.interview.agent.adaptive.persistence.memory.migration.HistoryBackfillData.HistoricalAssessment;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.migration.HistoryBackfillData.HistoricalEpisode;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.migration.HistoryBackfillData.LegacyProfile;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.migration.HistoryBackfillData.OwnerTopic;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class HistoryBackfillReader {

  private static final String ASSESSMENT_SQL = """
      SELECT a.id, a.session_id, a.turn_index, a.dimension_order, a.depth_level,
             s.tenant_id, s.candidate_id,
             p.session_id AS mapped_session_id, p.suggested_skill, p.focus_id
      FROM agent_assessments a
      LEFT JOIN agent_sessions s ON s.id = a.session_id
      LEFT JOIN agent_plans p
        ON p.session_id = a.session_id AND p.dimension_order = a.dimension_order
      ORDER BY a.id
      """;

  private static final String ANSWERED_TURN_SQL = """
      SELECT session_id, turn_index, COALESCE(answered_at, created_at) AS answered_at
      FROM agent_turns
      WHERE answer IS NOT NULL
      ORDER BY session_id, turn_index
      """;

  private static final String LEGACY_PROFILE_SQL = """
      SELECT lp.id, lp.tenant_id, lp.candidate_id, lp.source_session_id,
             lp.dimension_order, lp.source_assessment_id, lp.superseded_by,
             lp.created_at, s.tenant_id AS session_tenant_id,
             s.candidate_id AS session_candidate_id,
             p.session_id AS mapped_session_id, p.suggested_skill, p.focus_id,
             a.id AS mapped_assessment_id, a.session_id AS assessment_session_id,
             a.dimension_order AS assessment_dimension_order
      FROM legacy_candidate_ability_profiles lp
      LEFT JOIN agent_sessions s ON s.id = lp.source_session_id
      LEFT JOIN agent_plans p
        ON p.session_id = lp.source_session_id
       AND p.dimension_order = lp.dimension_order
      LEFT JOIN agent_assessments a ON a.id = lp.source_assessment_id
      ORDER BY lp.id
      """;

  HistoryBackfillData read(Connection connection) throws SQLException {
    List<HistoricalAssessment> assessments = readAssessments(connection);
    return new HistoryBackfillData(
        assessments,
        readEpisodes(connection, assessments),
        readLegacyProfiles(connection)
    );
  }

  private List<HistoricalAssessment> readAssessments(Connection connection)
      throws SQLException {
    List<HistoricalAssessment> result = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(ASSESSMENT_SQL);
         ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        result.add(toAssessment(rows));
      }
    }
    return List.copyOf(result);
  }

  private HistoricalAssessment toAssessment(ResultSet row) throws SQLException {
    long id = row.getLong("id");
    String sessionId = required(row.getString("session_id"), "Assessment " + id + " 缺 session");
    required(row.getString("mapped_session_id"), mappingError(sessionId, row));
    OwnerTopic ownerTopic = new OwnerTopic(
        row.getString("tenant_id"),
        required(row.getString("candidate_id"), "Session " + sessionId + " 缺 candidate"),
        required(row.getString("suggested_skill"), "Plan 缺 suggested_skill: " + mappingKey(sessionId, row)),
        required(row.getString("focus_id"), "Plan 缺 focus_id: " + mappingKey(sessionId, row))
    );
    return new HistoricalAssessment(
        id,
        sessionId,
        row.getInt("turn_index"),
        ownerTopic,
        required(row.getString("depth_level"), "Assessment " + id + " 缺 depthLevel")
    );
  }

  private List<HistoricalEpisode> readEpisodes(
      Connection connection,
      List<HistoricalAssessment> assessments
  ) throws SQLException {
    Map<String, HistoricalAssessment> byTurn = new HashMap<>();
    assessments.forEach(item -> byTurn.put(turnKey(item.sessionId(), item.turnIndex()), item));
    List<HistoricalEpisode> result = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(ANSWERED_TURN_SQL);
         ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        result.add(toEpisode(rows, byTurn));
      }
    }
    return List.copyOf(result);
  }

  private HistoricalEpisode toEpisode(
      ResultSet row,
      Map<String, HistoricalAssessment> byTurn
  ) throws SQLException {
    String sessionId = row.getString("session_id");
    int turnIndex = row.getInt("turn_index");
    HistoricalAssessment assessment = byTurn.get(turnKey(sessionId, turnIndex));
    if (assessment == null) {
      throw failure("answered turn 缺 Assessment: " + turnKey(sessionId, turnIndex));
    }
    return new HistoricalEpisode(
        assessment,
        row.getObject("answered_at", LocalDateTime.class)
    );
  }

  private List<LegacyProfile> readLegacyProfiles(Connection connection)
      throws SQLException {
    List<LegacyProfile> result = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(LEGACY_PROFILE_SQL);
         ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        result.add(toLegacyProfile(rows));
      }
    }
    return List.copyOf(result);
  }

  private LegacyProfile toLegacyProfile(ResultSet row) throws SQLException {
    long id = row.getLong("id");
    String sessionId = required(
        row.getString("source_session_id"),
        "Legacy Profile " + id + " 缺 source session"
    );
    required(row.getString("mapped_session_id"), mappingError(sessionId, row));
    validateLegacyAssessment(row, id, sessionId);
    validateLegacyOwner(row, id);
    return new LegacyProfile(
        id,
        new OwnerTopic(
            row.getString("session_tenant_id"),
            row.getString("session_candidate_id"),
            required(row.getString("suggested_skill"), "Plan 缺 suggested_skill: " + mappingKey(sessionId, row)),
            required(row.getString("focus_id"), "Plan 缺 focus_id: " + mappingKey(sessionId, row))
        ),
        sessionId,
        row.getObject("superseded_by") == null,
        row.getObject("created_at", LocalDateTime.class)
    );
  }

  private void validateLegacyAssessment(ResultSet row, long id, String sessionId)
      throws SQLException {
    if (row.getObject("mapped_assessment_id") == null) {
      throw failure("Legacy Profile " + id + " 缺 Assessment");
    }
    boolean sameSession = Objects.equals(sessionId, row.getString("assessment_session_id"));
    boolean sameDimension = row.getInt("dimension_order")
        == row.getInt("assessment_dimension_order");
    if (!sameSession || !sameDimension) {
      throw failure("Legacy Profile " + id + " 的 Assessment 不属于来源 plan");
    }
  }

  private void validateLegacyOwner(ResultSet row, long id) throws SQLException {
    boolean sameTenant = Objects.equals(
        row.getString("tenant_id"),
        row.getString("session_tenant_id")
    );
    boolean sameCandidate = Objects.equals(
        row.getString("candidate_id"),
        row.getString("session_candidate_id")
    );
    if (!sameTenant || !sameCandidate) {
      throw failure("Legacy Profile " + id + " 的 owner 与来源 session 不一致");
    }
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) {
      throw failure(message);
    }
    return value;
  }

  private String mappingError(String sessionId, ResultSet row) throws SQLException {
    return "缺 agent_plan: " + mappingKey(sessionId, row);
  }

  private String mappingKey(String sessionId, ResultSet row) throws SQLException {
    return sessionId + "/" + row.getInt("dimension_order");
  }

  private String turnKey(String sessionId, int turnIndex) {
    return sessionId + "/" + turnIndex;
  }

  private IllegalStateException failure(String detail) {
    return new IllegalStateException("三层记忆历史迁移失败：" + detail);
  }
}
