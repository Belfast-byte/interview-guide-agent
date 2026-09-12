package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.assertj.core.api.Assertions.assertThat;

class MemoryFactReuseMigrationTest {
  @Test
  void newAnswersOmitRetiredStateWhileHistoricalValuesRemain() throws Exception {
    try (var connection = DriverManager.getConnection("jdbc:h2:mem:reuse_memory_facts");
         var statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE candidate_memory_episode_facts (
            id BIGINT PRIMARY KEY, assessment_id BIGINT NOT NULL,
            assistance_level VARCHAR(16) NOT NULL, closure_status VARCHAR(16) NOT NULL,
            enrichment_status VARCHAR(24) NOT NULL, updated_at TIMESTAMP NOT NULL,
            version BIGINT NOT NULL DEFAULT 0, enrichment_attempts INTEGER NOT NULL DEFAULT 0,
            CHECK (enrichment_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')))
          """);
      statement.execute("""
          INSERT INTO candidate_memory_episode_facts
            (id, assessment_id, assistance_level, closure_status, enrichment_status, updated_at)
          VALUES (1, 10, 'HINT', 'UNRESOLVED', 'FAILED', CURRENT_TIMESTAMP)
          """);
      new ResourceDatabasePopulator(new ClassPathResource(
          "db/migration/V20261006__reuse_answer_facts_for_memory.sql")).populate(connection);
      statement.execute("INSERT INTO candidate_memory_episode_facts (id, assessment_id) VALUES (2, 20)");
      try (var rows = statement.executeQuery("""
          SELECT assessment_id, assistance_level, enrichment_status, version
          FROM candidate_memory_episode_facts ORDER BY id
          """)) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getLong(1)).isEqualTo(10);
        assertThat(rows.getString(2)).isEqualTo("HINT");
        assertThat(rows.getString(3)).isEqualTo("FAILED");
        assertThat(rows.next()).isTrue();
        assertThat(rows.getLong(1)).isEqualTo(20);
        assertThat(rows.getString(2)).isNull();
        assertThat(rows.getString(3)).isNull();
        assertThat(rows.getLong(4)).isZero();
      }
    }
  }
}
