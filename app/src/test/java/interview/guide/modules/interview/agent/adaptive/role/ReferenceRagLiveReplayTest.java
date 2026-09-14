package interview.guide.modules.interview.agent.adaptive.role;

import static org.assertj.core.api.Assertions.*;

import interview.guide.App;
import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.modules.llmprovider.repository.LlmProviderRepository;
import interview.guide.modules.auth.persistence.UserRepository;
import interview.guide.modules.interview.agent.adaptive.application.*;
import interview.guide.modules.interview.agent.adaptive.core.context.*;
import interview.guide.modules.interview.agent.adaptive.core.session.*;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.runtime.*;
import interview.guide.modules.interview.agent.adaptive.tool.SkillReferenceIndex;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;

/** 显式启用才读取指定测试账号并调用真实模型；只有索引写入，无评分/下一题提交。 */
@EnabledIfEnvironmentVariable(named = "REFERENCE_RAG_LIVE", matches = "true")
class ReferenceRagLiveReplayTest {
  @Test
  void replaySavedAnswerAndReferenceAugmentedDecisionWithoutCommit() {
    String sessionId = required("REFERENCE_RAG_SESSION");
    int turn = Integer.parseInt(required("REFERENCE_RAG_TURN"));
    try (var app = new SpringApplicationBuilder(App.class).run("--server.port=0")) {
      var owner = app.getBean(UserRepository.class).findByEmail(required("REFERENCE_RAG_EMAIL"))
          .orElseThrow().id().toString();
      var persistence = app.getBean(AdaptiveInterviewPersistenceService.class);
      persistence.requireCandidateSession(owner, sessionId);
      String credentialProvider = System.getenv("REFERENCE_RAG_EMBEDDING_CREDENTIAL_PROVIDER");
      if (credentialProvider != null && !credentialProvider.isBlank()) {
        // 仅在显式回归配置中借用同站点账号客户端；模型、维度与向量空间沿用全局配置。
        var providers = app.getBean(LlmProviderRepository.class);
        var candidate = providers.findByIdAndCandidateId(credentialProvider, UUID.fromString(owner)).orElseThrow();
        var registry = app.getBean(LlmProviderRegistry.class);
        String defaultId = ReflectionTestUtils.invokeMethod(registry, "resolveDefaultEmbeddingProviderId");
        var global = providers.findById(defaultId).orElseThrow();
        assertThat(candidate.getBaseUrl()).isEqualTo(global.getBaseUrl());
        var original = (OpenAiEmbeddingModel) registry.getDefaultEmbeddingModel();
        var credentialModel = (OpenAiEmbeddingModel) registry.getEmbeddingModel(credentialProvider);
        var model = OpenAiEmbeddingModel.builder()
            .openAiClient((OpenAIClient) ReflectionTestUtils.getField(credentialModel, "openAiClient"))
            .metadataMode(MetadataMode.EMBED).options(original.getOptions()).build();
        @SuppressWarnings("unchecked")
        var cache = (Map<String, EmbeddingModel>) ReflectionTestUtils.getField(registry, "embeddingModelCache");
        cache.put(defaultId, model);
        app.getBean(SkillReferenceIndex.class).sync();
        System.out.println("LIVE_EMBEDDING_SAME_MODEL_WITH_ACCOUNT_CREDENTIAL model=" + original.getOptions().getModel());
      }
      var interview = persistence.get(sessionId);
      var answer = persistence.answerForCandidate(owner, sessionId, turn);
      assertThat(answer.codeSubmission()).isNull();
      var jdbc = app.getBean(JdbcTemplate.class);
      var before = counts(jdbc, sessionId);
      var request = new AdaptiveAnswerDecisionService.AnswerDecisionRequest(new MemoryOwner(null, owner),
          interview, answer, Duration.ofSeconds(60), stage -> System.out.println("LIVE_STAGE " + stage));
      var decisions = app.getBean(AdaptiveAnswerDecisionService.class);
      var result = decisions.decide(request);
      System.out.println("LIVE_ORIGINAL_SUCCESS action=" + result.agentDecision().action().getClass().getSimpleName());
      var policy = app.getBean(TargetBudgetPolicy.class).evaluate(interview.coverage(), result.assessment());
      AgentContext context = ReflectionTestUtils.invokeMethod(decisions, "context", request, result.assessment(), policy);
      assertThat(app.getBean(SkillReferenceIndex.class).ready()).isTrue();
      var referenceTarget = context.facts().coverage().targets().stream()
          .filter(t -> t.target().identity().topic().focusId().equals("RAG")).findFirst().orElseThrow();
      var observations = app.getBean(ReadToolExecutor.class).execute(new ReadToolBatch(context,
          List.of(new ReadToolCall("reference_search", Map.of("targetId", referenceTarget.targetId(),
              "query", "RAG 检索质量 召回率 延迟 向量检索与重排"), "验证参考进入出题上下文")), System.nanoTime() + Duration.ofSeconds(60).toNanos(), 0));
      assertThat(observations).singleElement().satisfies(observation -> {
        assertThat(observation.kind()).isEqualTo(DecisionObservation.Kind.TOOL_SUCCESS);
        assertThat((List<?>) observation.data().get("hits")).isNotEmpty();
        assertThat(observation.adoptableSources()).isEmpty();
      });
      var loop = app.getBean(InterviewAgentLoop.class);
      var augmented = loop.run(context, observations, Duration.ofSeconds(60));
      System.out.println("LIVE_REFERENCE_SUCCESS action=" + augmented.action().getClass().getSimpleName());
      // 不制造数据库事实：追加明确的合成问答，仅验证后续轮次体积与真实决策可行性。
      var turns = new ArrayList<>(context.facts().recentTurns());
      for (int index = turn + 1; index <= turn + 3; index++) {
        turns.add(new AdaptiveInterviewTurn(index, referenceTarget.target().identity().order(),
            "合成回归题：如何衡量检索效果与排查召回失败？", "合成上下文增长验证",
            "合成回答：以标注问题集评估召回率和相关性，分开观察检索与重排延迟。先检查文档切分和查询分布，"
                + "再比较候选规模与最终答案质量，并用失败样本确认问题来自召回还是生成。", null, null, null));
        var coverage = context.facts().coverage();
        var targets = coverage.targets().stream().map(target -> target.targetId().equals(referenceTarget.targetId())
            ? new CoverageView.TargetCoverage(target.targetId(), target.target(), target.askedTurns() + 1,
                target.latestDepth(), target.openGapIds(), target.evidenceIds()) : target).toList();
        context = new AgentContext(context.session(), new AgentContext.Facts(
            new CoverageView(turns.size(), Math.max(0, context.session().maxTurns() - turns.size()),
                targets, coverage.openProbeGaps(), coverage.evidenceIds()), turns,
            context.facts().skillGuidance(), context.facts().allowedReadTools()), context.workingMemory());
        var later = loop.run(context, observations, Duration.ofSeconds(60));
        System.out.println("LIVE_SYNTHETIC_LATER turn=" + index + " action=" + later.action().getClass().getSimpleName());
      }
      assertThat(counts(jdbc, sessionId)).isEqualTo(before);
      assertThat(persistence.get(sessionId).history().session()).isEqualTo(interview.history().session());
      System.out.println("LIVE_NO_COMMIT_VERIFIED " + before);
    }
  }

  private Map<String, Object> counts(JdbcTemplate jdbc, String session) {
    return jdbc.queryForMap("SELECT (SELECT count(*) FROM agent_turns WHERE session_id=?) AS turns, "
        + "(SELECT count(*) FROM agent_assessments WHERE session_id=?) AS assessments", session, session);
  }

  private String required(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
    return value;
  }
}
