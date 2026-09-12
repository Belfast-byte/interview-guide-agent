package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class MemoryFingerprintMigrationTest {

  @Test
  void newRowsCanOmitRetiredFieldsWithoutDeletingHistoricalValues() throws Exception {
    try (var connection = DriverManager.getConnection("jdbc:h2:mem:retire_fingerprints");
         var statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE agent_question_exposures (
            id BIGINT PRIMARY KEY, scenario_fingerprint VARCHAR(64) NOT NULL,
            wording_fingerprint VARCHAR(64) NOT NULL)
          """);
      statement.execute("""
          CREATE TABLE candidate_memory_observation_revisions (
            id BIGINT PRIMARY KEY, input_fingerprint VARCHAR(64) NOT NULL,
            opportunity_key VARCHAR(100) NOT NULL)
          """);
      statement.execute("INSERT INTO agent_question_exposures VALUES (1, 'old-scenario', 'old-wording')");
      statement.execute("INSERT INTO candidate_memory_observation_revisions VALUES (1, 'old-input', 'old-opportunity')");

      new ResourceDatabasePopulator(new ClassPathResource(
          "db/migration/V20261005__retire_memory_fingerprints.sql")).populate(connection);

      statement.execute("INSERT INTO agent_question_exposures (id) VALUES (2)");
      statement.execute("INSERT INTO candidate_memory_observation_revisions (id) VALUES (2)");
      try (var rows = statement.executeQuery(
          "SELECT input_fingerprint, opportunity_key FROM candidate_memory_observation_revisions ORDER BY id")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo("old-input");
        assertThat(rows.getString(2)).isEqualTo("old-opportunity");
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isNull();
        assertThat(rows.getString(2)).isNull();
      }
    }
  }
}
