package interview.guide.modules.interview.agent.adaptive;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
  @DisplayName("领域内核只依赖 JDK、通用业务异常和自身")
  void coreShouldRemainFrameworkFree() {
    classes()
        .that().resideInAPackage(ROOT + ".core..")
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage(
            "java..",
            "interview.guide.common.exception..",
            ROOT + ".core.."
        )
        .check(classes);
  }

  @Test
  @DisplayName("业务能力与运行时不能反向依赖持久化实现")
  void businessCapabilitiesShouldUseOwnedPorts() {
    noClasses()
        .that().resideInAnyPackage(
            ROOT + ".memory..",
            ROOT + ".assessment..",
            ROOT + ".planning..",
            ROOT + ".runtime.."
        )
        .should().dependOnClassesThat()
        .resideInAPackage(ROOT + ".persistence..")
        .check(classes);
  }
}
