package interview.guide.modules.interview.agent.adaptive.role;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.App;
import interview.guide.modules.auth.persistence.UserRepository;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentContext;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentRequest;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthAssessmentAgent;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeRepairReview.Result;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask;
import interview.guide.modules.llmprovider.repository.LlmProviderRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** 显式启用的合成代码语义验收；调用生产评估器，不提交面试事实，不执行候选人代码。 */
@EnabledIfEnvironmentVariable(named = "CODE_REPAIR_LIVE", matches = "true")
class CodeRepairLiveAssessmentTest {
  @Test
  @Timeout(240)
  void distinguishesCorrectEquivalentAndIncorrectRepairs() {
    String database = required("POSTGRES_DB");
    assertThat(database).as("只允许专用隔离数据库").matches("cr08_[a-z0-9_]+");
    assertThat(required("REDIS_PORT")).as("不得使用默认开发 Redis").isNotEqualTo("6379");
    String provider = required("CODE_REPAIR_PROVIDER");
    String email = required("CODE_REPAIR_EMAIL");
    try (var app = new SpringApplicationBuilder(App.class).run("--server.port=0",
        "--spring.datasource.url=jdbc:postgresql://" + required("POSTGRES_HOST") + ":"
            + required("POSTGRES_PORT") + "/" + database)) {
      var owner = app.getBean(UserRepository.class).findByEmail(email).orElseThrow().id();
      app.getBean(LlmProviderRepository.class).findByIdAndCandidateId(provider, owner).orElseThrow();
      var assessor = app.getBean(DepthAssessmentAgent.class);
      var task = task();
      task.validate();
      String session = "cr08-synthetic-" + UUID.randomUUID();
      for (var sample : samples()) {
        long started = System.nanoTime();
        System.out.println("CR08_START sample=" + sample.id());
        var context = new AssessmentContext("Java", "并发库存扣减", "修复库存预留方法", null,
            Arrays.stream(DepthLevel.values()).map(DepthLevel::rubricLine).toList(),
            List.of(), List.of(), List.of(),
            new AssessmentContext.CodeTaskContext(task, source(sample.body()), List.of()));
        var result = assessor.assess(new AssessmentRequest(session, 1, context), provider);
        var actual = result.codeReview().checks().stream().collect(Collectors.toMap(
            check -> check.checkId(), check -> check.result()));
        System.out.println("CR08_RESULT sample=" + sample.id() + " checks=" + actual
            + " elapsedMs=" + (System.nanoTime() - started) / 1_000_000);
        assertThat(actual).as(sample.id()).isEqualTo(sample.expected());
        // assess 已对 Evidence、gap 和关闭引用逐字定位；不将模型的等级当作固定真值。
      }
    }
  }

  private static CodeRepairTask task() {
    return new CodeRepairTask(source("int stock = repo.stock(sku);\n"
        + "    if (stock >= quantity) repo.setStock(sku, stock - quantity);"),
        List.of("多实例并发下，库存充足时每次预留恰好扣减 quantity，不超卖、不丢失扣减；不足时不修改库存。",
            "库存不足必须抛出 SoldOut，不允许正常返回。"),
        List.of("Java 21；sku 保证存在，quantity 是已校验的正整数，库存非负且计算不溢出。",
            "多个 JVM 共享同一数据库；无重试或重复业务请求；无其他副作用。",
            "Repository 的每个方法均执行独立已提交事务，READ_COMMITTED；调用期间无数据库故障。",
            "stock 读取当前库存，setStock 无条件覆盖库存，两个方法的组合不原子。",
            "reserve 在数据库中原子执行条件扣减：库存 >= quantity 时减 quantity 并返回 true，否则不写入并返回 false。",
            "允许使用公开的任意 Repository 方法，不引入未给定设施；只审阅业务语义，不运行代码。"),
        new CodeRepairTask.ReviewGuide(List.of(
            new CodeRepairTask.Check("C1", "读写分离丢失扣减", "多 JVM 并发预留同一 SKU",
                "并发扣减满足要求，接受等价控制流，不要求固定代码写法"),
            new CodeRepairTask.Check("C2", "不足时正常返回", "库存不足",
                "必须抛出 SoldOut，不能忽略失败结果"))));
  }

  private static String source(String body) {
    return """
        class InventoryService {
          interface Repository {
            int stock(String sku);
            void setStock(String sku, int value);
            boolean reserve(String sku, int quantity);
          }
          static class SoldOut extends RuntimeException {}
          private final Repository repo;
          InventoryService(Repository repo) { this.repo = repo; }
          void reserve(String sku, int quantity) {
            %s
          }
        }
        """.formatted(body);
  }

  private static List<Sample> samples() {
    return List.of(
        new Sample("correct", "if (!repo.reserve(sku, quantity)) throw new SoldOut();",
            Map.of("C1", Result.SATISFIED, "C2", Result.SATISFIED)),
        new Sample("equivalent", "if (repo.reserve(sku, quantity)) return;\n    throw new SoldOut();",
            Map.of("C1", Result.SATISFIED, "C2", Result.SATISFIED)),
        new Sample("instance-lock", "synchronized (this) {\n"
            + "      int stock = repo.stock(sku);\n"
            + "      if (stock < quantity) throw new SoldOut();\n"
            + "      repo.setStock(sku, stock - quantity);\n    }",
            Map.of("C1", Result.NOT_SATISFIED, "C2", Result.SATISFIED)),
        new Sample("ignored-failure", "repo.reserve(sku, quantity);",
            Map.of("C1", Result.SATISFIED, "C2", Result.NOT_SATISFIED)));
  }

  private record Sample(String id, String body, Map<String, Result> expected) {}

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) throw new IllegalStateException("缺少 " + name);
    return value;
  }
}
