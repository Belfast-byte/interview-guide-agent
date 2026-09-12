package interview.guide.modules.interview.agent.adaptive;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Adaptive Agent 包依赖隔离")
class AdaptivePackageIsolationTest {

  private static final String ROOT =
      "interview.guide.modules.interview.agent.adaptive";
  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages(ROOT);
  }

  @Test
  @DisplayName("领域内核不依赖框架业务实现，仅允许模型契约的 Schema 元数据")
  void coreShouldExcludeFrameworkBehaviorDependencies() {
    classes()
        .that().resideInAPackage(ROOT + ".core..")
        // SourceQuote/WorkingMemory 直接用于模型契约；nullable 注解明确可空值，不引入运行时业务依赖。
        .should().onlyDependOnClassesThat(JavaClass.Predicates.resideInAnyPackage(
            "java..", "interview.guide.common.exception..", ROOT + ".core..")
            .or(DescribedPredicate.<JavaClass>describe("Schema 注解及其参数类型", dependency ->
                dependency.getName().equals("io.swagger.v3.oas.annotations.media.Schema")
                    || dependency.getName().equals("io.swagger.v3.oas.annotations.ExternalDocumentation")
                    || dependency.getName().startsWith("io.swagger.v3.oas.annotations.media.Schema$"))))
        .check(classes);
  }

  @Test
  @DisplayName("领域业务能力与运行时不依赖持久化，外部工具适配按事实直接查询")
  void businessCapabilitiesShouldUseOwnedPorts() {
    noClasses()
        .that().resideInAnyPackage(
            ROOT + ".memory..",
            ROOT + ".assessment..",
            ROOT + ".planning..",
            ROOT + ".runtime.."
        )
        // Episode 按业务聚合事实与查询；tool 是外部读取适配，允许注入 Repository 并在本边界校验归属。
        // 不为满足技术分层再增加只转发 Repository 的接口或 DTO；其余业务能力保留持久化隔离。
        .and().resideOutsideOfPackage(ROOT + ".memory.episode..")
        .should().dependOnClassesThat()
        .resideInAPackage(ROOT + ".persistence..")
        .check(classes);
  }
}
