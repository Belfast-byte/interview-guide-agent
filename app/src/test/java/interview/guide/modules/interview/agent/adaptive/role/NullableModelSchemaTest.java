package interview.guide.modules.interview.agent.adaptive.role;

import static org.assertj.core.api.Assertions.assertThat;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class NullableModelSchemaTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void initialAndNextQuestionsAllowNullTaskAndRootWithoutRelaxingQuestionType() {
    for (Class<?> type : List.of(PlanProposal.class, InterviewDecisionOutput.class, AgentDecision.QuestionDraft.class)) {
      var schema = schema(type);
      optionalNullable(schema, "codeTask");
      optionalNullable(schema, "codeTaskTurnIndex");
      assertThat(schema.findValues("questionType")).isNotEmpty().allSatisfy(field ->
          assertThat(nullable(field)).isFalse());
    }
  }

  @Test
  void decisionBranchesAndWorkingMemoryOptionalScalarsAdmitNullOrOmission() {
    var schema = schema(InterviewDecisionOutput.class);
    for (String name : List.of("ask", "callReadTools", "finish", "sourceGapId", "basedOnTurnIndex",
        "activeTargetId", "activeGapId", "nextProbeIntent")) {
      optionalNullable(schema, name);
    }
    assertThat(schema.findValues("adoptedObservationRefs")).isNotEmpty().allSatisfy(field -> {
      assertThat(nullable(field)).isFalse();
      assertThat(nullable(field.path("items"))).isFalse();
    });
  }

  @Test
  void textualAssessmentsAllowNoCodeReviewAndUniqueQuotesNeedNoOffset() {
    var schema = schema(AssessmentProposal.class);
    optionalNullable(schema, "codeReview");
    optionalNullable(schema, "startOffset");
    assertThat(schema.findValues("quote")).isNotEmpty().allSatisfy(field ->
        assertThat(nullable(field)).isFalse());
  }

  private JsonNode schema(Class<?> type) {
    return mapper.readTree(StructuredOutputInvoker.strictConverter(type).getJsonSchema());
  }

  private void optionalNullable(JsonNode schema, String name) {
    assertThat(schema.findValues(name)).as(name).isNotEmpty().allSatisfy(field ->
        assertThat(nullable(field)).as(name + " accepts explicit null").isTrue());
    for (var required : schema.findValues("required")) {
      for (var field : required) assertThat(field.asText()).isNotEqualTo(name);
    }
  }

  private boolean nullable(JsonNode schema) {
    var type = schema.path("type");
    if (type.isTextual() && type.asText().equals("null")) return true;
    for (var member : type) if (member.asText().equals("null")) return true;
    for (var branch : schema.path("anyOf")) if (nullable(branch)) return true;
    return false;
  }
}
