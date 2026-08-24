package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.profile.AbilityCounterIncrementStore;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityCounter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 使用数据库原生 upsert 保证 Counter 首建与增量是单条原子写入。
 */
@Repository
public class JdbcAbilityCounterIncrementStore implements AbilityCounterIncrementStore {

  private static final int EXPECTED_AFFECTED_ROWS = 1;
  private static final String POSTGRESQL = "PostgreSQL";
  private static final String H2 = "H2";
  private static final String POSTGRESQL_UPSERT = """
      INSERT INTO candidate_ability_counters (
        tenant_id, candidate_id, skill_id, focus_id,
        l0_count, l1_count, l2_count, l3_count, l4_count,
        version, created_at, updated_at
      ) VALUES (
        :tenantId, :candidateId, :skillId, :focusId,
        :l0, :l1, :l2, :l3, :l4, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
      )
      ON CONFLICT (tenant_id, candidate_id, skill_id, focus_id)
      DO UPDATE SET
        l0_count = candidate_ability_counters.l0_count + EXCLUDED.l0_count,
        l1_count = candidate_ability_counters.l1_count + EXCLUDED.l1_count,
        l2_count = candidate_ability_counters.l2_count + EXCLUDED.l2_count,
        l3_count = candidate_ability_counters.l3_count + EXCLUDED.l3_count,
        l4_count = candidate_ability_counters.l4_count + EXCLUDED.l4_count,
        version = candidate_ability_counters.version + 1,
        updated_at = CURRENT_TIMESTAMP
      """;
  private static final String H2_UPSERT = """
      MERGE INTO candidate_ability_counters AS counter
      USING (VALUES (
        CAST(:tenantId AS VARCHAR(64)), CAST(:candidateId AS VARCHAR(64)),
        CAST(:skillId AS VARCHAR(64)), CAST(:focusId AS VARCHAR(64)),
        CAST(:l0 AS BIGINT), CAST(:l1 AS BIGINT), CAST(:l2 AS BIGINT),
        CAST(:l3 AS BIGINT), CAST(:l4 AS BIGINT)
      )) AS delta(tenant_id, candidate_id, skill_id, focus_id, l0, l1, l2, l3, l4)
      ON counter.tenant_id IS NOT DISTINCT FROM delta.tenant_id
        AND counter.candidate_id = delta.candidate_id
        AND counter.skill_id = delta.skill_id
        AND counter.focus_id = delta.focus_id
      WHEN MATCHED THEN UPDATE SET
        l0_count = counter.l0_count + delta.l0,
        l1_count = counter.l1_count + delta.l1,
        l2_count = counter.l2_count + delta.l2,
        l3_count = counter.l3_count + delta.l3,
        l4_count = counter.l4_count + delta.l4,
        version = counter.version + 1,
        updated_at = CURRENT_TIMESTAMP
      WHEN NOT MATCHED THEN INSERT (
        tenant_id, candidate_id, skill_id, focus_id,
        l0_count, l1_count, l2_count, l3_count, l4_count,
        version, created_at, updated_at
      ) VALUES (
        delta.tenant_id, delta.candidate_id, delta.skill_id, delta.focus_id,
        delta.l0, delta.l1, delta.l2, delta.l3, delta.l4,
        0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
      )
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final String upsertStatement;

  public JdbcAbilityCounterIncrementStore(DataSource dataSource) {
    jdbc = new NamedParameterJdbcTemplate(dataSource);
    upsertStatement = resolveUpsertStatement(dataSource);
  }

  @Override
  public void increment(MemoryOwner owner, TopicKey topic, DepthLevel level) {
    AbilityCounter delta = AbilityCounter.empty().increment(level);
    int affected = jdbc.update(upsertStatement, parameters(owner, topic, delta));
    if (affected != EXPECTED_AFFECTED_ROWS) {
      throw new IllegalStateException("AbilityCounter 原子增量未更新唯一记录");
    }
  }

  private MapSqlParameterSource parameters(
      MemoryOwner owner,
      TopicKey topic,
      AbilityCounter delta
  ) {
    return new MapSqlParameterSource()
        .addValue("tenantId", owner.tenantId(), Types.VARCHAR)
        .addValue("candidateId", owner.candidateId())
        .addValue("skillId", topic.skillId())
        .addValue("focusId", topic.focusId())
        .addValue("l0", delta.l0Count())
        .addValue("l1", delta.l1Count())
        .addValue("l2", delta.l2Count())
        .addValue("l3", delta.l3Count())
        .addValue("l4", delta.l4Count());
  }

  private String resolveUpsertStatement(DataSource dataSource) {
    try (Connection connection = dataSource.getConnection()) {
      String databaseProduct = connection.getMetaData().getDatabaseProductName();
      return switch (databaseProduct) {
        case POSTGRESQL -> POSTGRESQL_UPSERT;
        case H2 -> H2_UPSERT;
        default -> throw new IllegalStateException(
            "AbilityCounter 不支持数据库 " + databaseProduct
        );
      };
    } catch (SQLException exception) {
      throw new IllegalStateException("无法识别 AbilityCounter 数据库类型", exception);
    }
  }
}
