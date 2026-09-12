import java.nio.file.Files;
import java.nio.file.Path;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.ai.PromptSecurityConstants;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentProposal;

/** Export actual production schema and formatting instructions without booting the app. */
public class ExportCodeRepairSchema {
  public static void main(String[] args) throws Exception {
    var directory = Path.of(args[0]);
    export(directory, "planner", PlanProposal.class);
    export(directory, "assessment", AssessmentProposal.class);
    Files.writeString(directory.resolve("security-instruction.txt"), PromptSecurityConstants.ANTI_INJECTION_INSTRUCTION);
  }

  private static void export(Path directory, String name, Class<?> type) throws Exception {
    var converter = StructuredOutputInvoker.strictConverter(type);
    Files.writeString(directory.resolve(name + "-schema.json"), converter.getJsonSchema());
    Files.writeString(directory.resolve(name + "-format.txt"), converter.getFormat());
  }
}
