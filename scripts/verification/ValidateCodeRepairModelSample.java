import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentProposal;
import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;
import interview.guide.modules.interview.agent.adaptive.core.session.*;
import interview.guide.modules.interview.agent.adaptive.planning.*;
import tools.jackson.databind.json.JsonMapper;

/** Replay synthetic model output through the real strict converter and domain checks. */
public class ValidateCodeRepairModelSample {
  public static void main(String[] args) throws Exception {
    var mapper = JsonMapper.builder().build();
    var artifact = mapper.readTree(Files.readString(Path.of(args[0])));
    String name = artifact.path("case").asString();
    String raw = artifact.path("rawText").asString();
    if (name.equals("planner")) {
      validatePlanner(raw);
    } else {
      var input = mapper.readTree(Files.readString(Path.of(args[1]))).path(name);
      var proposal = StructuredOutputInvoker.strictConverter(AssessmentProposal.class).convert(raw);
      var task = mapper.treeToValue(input.path("codeTaskContext").path("task"), CodeRepairTask.class);
      proposal.codeReview().validate(task);
      var sources = new SourceQuote.AnswerSources(null,
          input.path("codeTaskContext").path("submittedCode").asString());
      proposal.evidenceQuotes().forEach(quote -> quote.resolve(sources));
      proposal.probeGaps().forEach(gap -> gap.anchor().resolve(sources));
      proposal.resolvedGaps().forEach(gap -> gap.evidenceQuote().resolve(sources));
    }
    System.out.println("PASS real strict converter and domain checks: " + name);
  }

  private static void validatePlanner(String raw) {
    var proposal = StructuredOutputInvoker.strictConverter(PlanProposal.class).convert(raw);
    var plan = InterviewPlan.decide("synthetic-model-smoke", proposal,
        new InterviewSessionSettings(SessionMode.EVALUATION, CandidateLevel.EXPERIENCED, PracticeScope.none()));
    proposal.initialQuestion().toDecision(plan, List.of());
  }
}
