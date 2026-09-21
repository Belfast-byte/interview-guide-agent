package interview.guide.modules.interview.agent.evaluation;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QuestionEvaluationIsolationTest {
  @Test
  @Timeout(45)
  void productionCodeCannotConsumeEvaluationResults() {
    var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("interview.guide");
    noClasses().that().resideOutsideOfPackage("..agent.evaluation..")
        .should().dependOnClassesThat().resideInAPackage("..agent.evaluation..")
        .because("Judge observations must not affect publishing, working memory or candidate assessments")
        .check(classes);
    noClasses().that().resideInAPackage("..agent.evaluation..")
        .should().beMetaAnnotatedWith(org.springframework.stereotype.Component.class)
        .because("offline evaluation must be explicitly constructed, not started by production component scan")
        .check(classes);
  }
}
