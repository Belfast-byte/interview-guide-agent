package interview.guide.modules.interview.agent.evaluation;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.PromptLoader;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.ai.StructuredOutputProperties;
import interview.guide.common.config.LlmProviderProperties;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
import interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor;
import interview.guide.modules.llmprovider.repository.LlmProviderRepository;
import interview.guide.modules.llmprovider.service.ApiKeyEncryptionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/** 显式启用的离线入口；最小上下文不扫描 App，不启动 HTTP、队列、调度、Flyway 或生产 Agent。 */
@EnabledIfEnvironmentVariable(named = "QUESTION_JUDGE_LIVE", matches = "true")
class QuestionJudgeOfflineTest {
  @TestConfiguration(proxyBeanMethods = false)
  @ImportAutoConfiguration({DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
      TransactionAutoConfiguration.class})
  @EnableConfigurationProperties(LlmProviderProperties.class)
  @EntityScan("interview.guide.modules")
  @EnableJpaRepositories(basePackageClasses = {LlmProviderRepository.class, AdaptiveAgentSessionRepository.class,
      AdaptiveAgentPlanRepository.class})
  @Import({LlmProviderRegistry.class, ApiKeyEncryptionService.class})
  static class OfflineConfiguration {}

  @Test
  @Timeout(600)
  void evaluateExplicitSamples() throws Exception {
    String email = required("QUESTION_JUDGE_EMAIL");
    String providerId = required("QUESTION_JUDGE_PROVIDER");
    int limit = Integer.parseInt(required("QUESTION_JUDGE_MAX_SAMPLES"));
    var timeout = Duration.ofSeconds(Long.parseLong(required("QUESTION_JUDGE_TIMEOUT_SECONDS")));
    if (limit < 1 || limit > 30 || timeout.toSeconds() * limit > 480) {
      throw new IllegalArgumentException("Offline run budget must fit 480 seconds and 30 samples");
    }
    // Credentials are read through existing configuration; never printed or copied to artifacts.
    try (var app = new SpringApplicationBuilder(OfflineConfiguration.class).web(WebApplicationType.NONE)
        .run("--spring.jpa.hibernate.ddl-auto=validate", "--spring.datasource.hikari.read-only=true")) {
      var jdbc = new JdbcTemplate(app.getBean(DataSource.class));
      String owner = jdbc.queryForObject("select id from users where email = ?", String.class, email);
      var provider = app.getBean(LlmProviderRepository.class)
          .findByIdAndCandidateId(providerId, UUID.fromString(owner)).orElseThrow();
      if (!provider.isEnabled()) throw new IllegalArgumentException("Provider disabled");
      var json = new ObjectMapper();
      var meter = new SimpleMeterRegistry();
      var telemetry = new AdaptiveAgentTelemetry(meter);
      var judge = new QuestionReviewService(app.getBean(LlmProviderRegistry.class),
          new StructuredOutputInvoker(new StructuredOutputProperties(), meter),
          new AdaptiveInputTokenBudget(new AdaptiveAgentProperties(), telemetry, new JTokkitTokenCountEstimator()),
          telemetry, new DeadlineExecutor(), json, new PromptLoader(new DefaultResourceLoader()));
      var batch = new QuestionEvaluationBatch(judge, json);
      var options = new QuestionReviewService.Options(providerId, provider.getModel(), required("QUESTION_JUDGE_REVISION"),
          timeout, Integer.parseInt(required("QUESTION_JUDGE_MAX_INPUT_TOKENS")),
          Integer.parseInt(required("QUESTION_JUDGE_MAX_OUTPUT_TOKENS")), true);
      List<QuestionSnapshot> samples = new ArrayList<>();
      String fixture = System.getenv("QUESTION_JUDGE_FIXTURES");
      String session = System.getenv("QUESTION_JUDGE_SESSION");
      if ((fixture == null) == (session == null)) throw new IllegalArgumentException("Choose fixtures OR session");
      if (fixture != null) samples.addAll(batch.readFixtures(Path.of(fixture)));
      else {
        var reader = new PublishedQuestionSnapshotReader(app.getBean(AdaptiveAgentSessionRepository.class),
            app.getBean(AdaptiveAgentTurnRepository.class), app.getBean(AdaptiveAgentPlanRepository.class),
            app.getBean(PlatformTransactionManager.class), json);
        for (String turn : required("QUESTION_JUDGE_TURNS").split(",")) {
          samples.add(reader.read(owner, null, session, Integer.parseInt(turn.trim())));
        }
      }
      if (!app.getBeansOfType(interview.guide.modules.interview.agent.adaptive.runtime.InterviewAgentLoop.class).isEmpty()) {
        throw new IllegalStateException("Production Agent must not be started");
      }
      var before = session == null ? Map.of() : fingerprint(jdbc, session);
      var output = batch.run(samples, options, limit, Path.of(required("QUESTION_JUDGE_OUTPUT")));
      if (session != null && !before.equals(fingerprint(jdbc, session))) {
        throw new IllegalStateException("Published interview facts changed during isolated evaluation");
      }
      if (session != null) System.out.println("QUESTION_JUDGE_BUSINESS_FACTS_UNCHANGED");
      // Only path/status is printed; reports are private local artifacts requiring explicit review.
      System.out.println("QUESTION_JUDGE_REPORT " + output);
      System.out.println("QUESTION_JUDGE_SUMMARY " + json.readTree(output.resolve("summary.json").toFile()));
    }
  }

  private static Map<String, Object> fingerprint(JdbcTemplate jdbc, String session) {
    // Hash full rows rather than only counts; includes answers, memory, leases and assessment contents.
    return jdbc.queryForMap("select "
        + "(select md5(coalesce(string_agg(to_jsonb(s)::text, '' order by s.id), '')) from agent_sessions s where s.id=?) as session, "
        + "(select md5(coalesce(string_agg(to_jsonb(t)::text, '' order by t.id), '')) from agent_turns t where t.session_id=?) as turns, "
        + "(select md5(coalesce(string_agg(to_jsonb(a)::text, '' order by a.id), '')) from agent_assessments a where a.session_id=?) as assessments, "
        + "(select md5(coalesce(string_agg(to_jsonb(p)::text, '' order by p.id), '')) from agent_plans p where p.session_id=?) as plans",
        session, session, session, session);
  }

  private static String required(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
    return value;
  }
}
