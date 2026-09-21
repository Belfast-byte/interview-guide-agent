package interview.guide.modules.interview.agent.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** 单并发、有限样本的离线运行；独立目录、不可覆盖文件，无持久任务状态。 */
public final class QuestionEvaluationBatch {
  private static final long MAX_FIXTURE_BYTES = 2 * 1024 * 1024;
  private final QuestionReviewService judge;
  private final ObjectMapper json;

  public QuestionEvaluationBatch(QuestionReviewService judge, ObjectMapper json) {
    this.judge = judge;
    this.json = json;
  }

  public record Summary(String run, int total, int planned, int completed, int failed, int skipped,
      int requestsAttempted, long durationMillis, long knownTotalTokens, int unknownUsageCount,
      Map<String, Integer> cohorts, Map<String, Integer> findings, String humanReview) {}

  public List<QuestionSnapshot> readFixtures(Path path) throws IOException {
    // Bound the bytes actually read, not only a racy pre-read file size.
    byte[] bytes;
    try (var input = Files.newInputStream(path)) { bytes = input.readNBytes((int) MAX_FIXTURE_BYTES + 1); }
    if (bytes.length > MAX_FIXTURE_BYTES) throw new IllegalArgumentException("Fixture too large");
    var strict = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    var snapshots = strict.readValue(bytes, QuestionSnapshot[].class);
    if (snapshots == null || snapshots.length == 0) throw new IllegalArgumentException("No fixtures");
    for (var snapshot : snapshots) {
      if (snapshot == null || snapshot.source() != QuestionSnapshot.Source.FIXTURE) {
        throw new IllegalArgumentException("File input must be labeled FIXTURE");
      }
    }
    return List.of(snapshots);
  }

  public Path run(List<QuestionSnapshot> snapshots, QuestionReviewService.Options options,
      int maxSamples, Path outputParent) throws IOException {
    if (maxSamples < 1 || snapshots.isEmpty()) throw new IllegalArgumentException("Explicit sample budget required");
    String run = UUID.randomUUID().toString();
    Files.createDirectories(outputParent);
    Path directory = Files.createDirectory(outputParent.resolve("question-judge-" + run),
        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    List<QuestionReviewService.Report> reports = new ArrayList<>();
    int planned = 0;
    boolean previousTimeout = false;
    for (int i = 0; i < snapshots.size(); i++) {
      var snapshot = snapshots.get(i);
      boolean interrupted = Thread.currentThread().isInterrupted();
      var selected = !interrupted && !previousTimeout && planned < maxSamples;
      if (selected && "TEXT".equals(snapshot.questionType())) planned++;
      var config = selected ? options : new QuestionReviewService.Options(options.providerId(), options.model(),
          options.codeRevision(), options.timeout(), options.maxInputTokens(), options.maxOutputTokens(), false);
      var report = judge.evaluate(snapshot, config);
      if (!selected) report = new QuestionReviewService.Report(report.attempt(), report.startedAt(), report.snapshot(),
          report.snapshotHash(), report.publicHash(), report.rubricVersion(), report.promptHash(), report.options(),
          report.status(), interrupted ? "INTERRUPTED" : previousTimeout ? "PREVIOUS_CALL_TIMEOUT" : "SAMPLE_LIMIT", report.durationMillis(), report.requestsAttempted(), report.tokens(),
          report.observations(), report.locatedIssues());
      previousTimeout |= "TIMEOUT".equals(report.reason());
      reports.add(report);
      // Save immediately: interruption of a later sample does not discard completed observations.
      writeNew(directory.resolve(String.format("%04d-%s.json", i + 1, report.attempt())), report);
      writeNew(directory.resolve(String.format("progress-%04d.json", i + 1)), summarize(run, reports, planned));
    }
    writeNew(directory.resolve("summary.json"), summarize(run, reports, planned));
    return directory;
  }

  private Summary summarize(String run, List<QuestionReviewService.Report> reports, int planned) {
    int completed = 0, failed = 0, skipped = 0, calls = 0, unknown = 0;
    long tokens = 0, duration = 0;
    var cohorts = new LinkedHashMap<String, Integer>();
    var findings = new LinkedHashMap<String, Integer>();
    for (var report : reports) {
      switch (report.status()) { case COMPLETED -> completed++; case FAILED -> failed++; case SKIPPED -> skipped++; }
      calls += report.requestsAttempted();
      duration += report.durationMillis();
      if (report.tokens() == null || report.tokens().total() == null) {
        if (report.requestsAttempted() > 0) unknown++;
      } else tokens += report.tokens().total();
      String cohort = report.snapshot().source() + ":" + (report.snapshot().turnIndex() == 1 ? "FIRST" : "LATER");
      cohorts.merge(cohort + ":" + report.status(), 1, Integer::sum);
      for (var observation : report.observations()) {
        findings.merge(observation.dimension() + ":" + observation.finding(), 1, Integer::sum);
      }
    }
    return new Summary(run, reports.size(), planned, completed, failed, skipped, calls, duration, tokens, unknown,
        Map.copyOf(cohorts), Map.copyOf(findings), "PENDING: model observations are not human-confirmed labels");
  }

  private void writeNew(Path file, Object content) throws IOException {
    Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    Files.writeString(file, json.writerWithDefaultPrettyPrinter().writeValueAsString(content), StandardOpenOption.WRITE);
  }
}
