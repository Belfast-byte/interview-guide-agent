package interview.guide.modules.interview.agent.adaptive.persistence;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "POSTGRES_SCHEMA_TEST_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "POSTGRES_SCHEMA_TEST_USER", matches = ".+")
@EnabledIfEnvironmentVariable(named = "POSTGRES_SCHEMA_TEST_PASSWORD", matches = ".+")
class PostgresAgentSchemaMigrationTest {

  private static final String PRODUCTION_SCHEMA = "public";

  private static final MigrationVersion PRE_SCHEMA_CLEANUP_VERSION =
      MigrationVersion.fromVersion("20260926");

  @Test
  @DisplayName("PostgreSQL 空库和遗留 Agent 基线均可迁移并通过 JPA validate")
  void shouldMigrateEmptyAndExistingSchemas() {
    DatabaseConfig config = DatabaseConfig.fromEnvironment();
    verifyDatabase(config, migrationDatabase("empty"), null);
    verifyDatabase(config, migrationDatabase("upgrade"), PRE_SCHEMA_CLEANUP_VERSION);
  }

  /** 历史迁移显式引用 public；测试账号需 CREATEDB，以独立数据库隔离两条路径。 */
  private void verifyDatabase(
      DatabaseConfig config,
      String database,
      MigrationVersion initialTarget
  ) {
    JdbcTemplate admin = new JdbcTemplate(dataSource(config));
    // database 仅来自本类固定前缀与 UUID，DDL 标识符不接受外部输入。
    admin.execute("CREATE DATABASE " + database);
    try {
      DataSource dataSource = dataSource(config.withDatabase(database));
      if (initialTarget != null) {
        migrate(dataSource, PRODUCTION_SCHEMA, initialTarget);
      }
      Flyway flyway = migrate(dataSource, PRODUCTION_SCHEMA, null);
      assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
      validateJpa(dataSource, PRODUCTION_SCHEMA);
    } finally {
      admin.execute("DROP DATABASE " + database);
    }
  }

  private Flyway migrate(
      DataSource dataSource,
      String schema,
      MigrationVersion target
  ) {
    var configuration = Flyway.configure()
        .dataSource(dataSource)
        .defaultSchema(schema)
        .schemas(schema);
    if (target != null) {
      configuration.target(target);
    }
    Flyway flyway = configuration.load();
    flyway.migrate();
    return flyway;
  }

  private void validateJpa(DataSource dataSource, String schema) {
    LocalContainerEntityManagerFactoryBean factory =
        new LocalContainerEntityManagerFactoryBean();
    factory.setDataSource(dataSource);
    factory.setPackagesToScan("interview.guide");
    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    factory.setJpaPropertyMap(Map.of(
        "hibernate.hbm2ddl.auto", "validate",
        "hibernate.default_schema", schema,
        "hibernate.physical_naming_strategy",
        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"
    ));
    factory.afterPropertiesSet();
    factory.destroy();
  }

  private DataSource dataSource(DatabaseConfig config) {
    return new DriverManagerDataSource(config.url(), config.user(), config.password());
  }

  private String migrationDatabase(String prefix) {
    return "agent_migration_" + prefix + "_"
        + UUID.randomUUID().toString().replace("-", "");
  }

  private record DatabaseConfig(String url, String user, String password) {

    private DatabaseConfig withDatabase(String database) {
      URI endpoint = URI.create(url.substring("jdbc:".length()));
      String query = endpoint.getRawQuery() == null ? "" : "?" + endpoint.getRawQuery();
      String databaseUrl = "jdbc:" + endpoint.getScheme() + "://"
          + endpoint.getRawAuthority() + "/" + database + query;
      return new DatabaseConfig(databaseUrl, user, password);
    }

    private static DatabaseConfig fromEnvironment() {
      return new DatabaseConfig(
          System.getenv("POSTGRES_SCHEMA_TEST_URL"),
          System.getenv("POSTGRES_SCHEMA_TEST_USER"),
          System.getenv("POSTGRES_SCHEMA_TEST_PASSWORD")
      );
    }
  }
}
