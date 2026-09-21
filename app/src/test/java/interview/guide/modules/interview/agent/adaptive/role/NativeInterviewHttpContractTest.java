package interview.guide.modules.interview.agent.adaptive.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.PromptLoader;
import interview.guide.common.config.LlmProviderProperties;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemoryValidator;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecisionValidator;
import interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor;
import interview.guide.modules.interview.agent.adaptive.runtime.InterviewAgentLoop;
import interview.guide.modules.interview.agent.adaptive.tool.InterviewMaterialReadTool;
import interview.guide.modules.interview.agent.adaptive.tool.InterviewToolCallback;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 使用真实 Registry、OpenAI SDK、ChatClient 和 manager，只有远端模型响应可控。 */
class NativeInterviewHttpContractTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  @Timeout(25)
  void nativeHttpRoundTripReadsMaterialRejectsInvalidProposalAndReturnsCodeQuestion() throws Exception {
    var requests = new CopyOnWriteArrayList<JsonNode>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var task = new CodeRepairTask("class Account { int balance; }", List.of("原子扣款"), List.of("多线程"),
        new CodeRepairTask.ReviewGuide(List.of(new CodeRepairTask.Check("C1", "更新丢失", "并发扣款", "保持余额一致"))));
    var question = new AgentDecision.QuestionDraft("请修复账户扣款", "验证并发语义", List.of(),
        CodeRepairTask.QuestionType.CODE_REPAIR, task, null);
    server.createContext("/", exchange -> {
      requests.add(json.readTree(exchange.getRequestBody().readAllBytes()));
      int step = requests.size();
      String name = step == 1 ? "interview_material_read" : "propose_question";
      Map<String, Object> arguments = step == 1 ? Map.of("source", "jd") : Map.of(
          "targetId", step == 2 ? "foreign" : "target-0", "workingMemory", WorkingMemory.empty(), "question", question);
      byte[] bytes = json.writeValueAsBytes(Map.of("id", "response-" + step, "object", "chat.completion",
          "created", 0, "model", "test-model", "choices", List.of(Map.of("index", 0, "finish_reason", "tool_calls",
              "message", Map.of("role", "assistant", "tool_calls", List.of(Map.of("id", "call-" + step,
                  "type", "function", "function", Map.of("name", name, "arguments", json.writeValueAsString(arguments))))))),
          "usage", Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", 2)));
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      try (var output = exchange.getResponseBody()) { output.write(bytes); }
    });
    server.start();
    try {
      var providers = new LlmProviderProperties();
      var provider = new LlmProviderProperties.ProviderConfig();
      provider.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
      provider.setApiKey("test-key");
      provider.setModel("test-model");
      providers.setProviders(Map.of("test", provider));
      providers.setDefaultProvider("test");
      var registry = new LlmProviderRegistry(providers, DefaultToolCallingManager.builder().build(), null, null);
      var properties = new AdaptiveAgentProperties();
      var budget = new AdaptiveInputTokenBudget(properties, mock(AdaptiveAgentTelemetry.class), new JTokkitTokenCountEstimator());
      var model = new SpringAiInterviewDecisionModel(registry, new InterviewDecisionPrompt(json,
          new PromptLoader(new DefaultResourceLoader()), properties, budget), new AdaptiveModelOptionsFactory(properties), budget,
          new AdaptiveAgentTelemetry(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
      var sessions = mock(AdaptiveAgentSessionRepository.class);
      var session = mock(AdaptiveAgentSessionEntity.class);
      when(session.jd()).thenReturn("SAVED_JD_ORIGINAL");
      when(sessions.findByIdAndCandidateIdAndTenantIdIsNull("session", "candidate")).thenReturn(Optional.of(session));
      var callback = new InterviewToolCallback(ToolCallbacks.from(new InterviewMaterialReadTool(sessions))[0], true);
      var loop = new InterviewAgentLoop(model, new AgentDecisionValidator(new WorkingMemoryValidator()),
          () -> new ToolCallback[] {callback}, new DeadlineExecutor(), properties);
      var context = new AgentContext(new AgentContext.SessionWindow(new AgentContext.SessionIdentity(
          "session", "test", new MemoryOwner(null, "candidate")), SessionMode.EVALUATION, 3),
          new AgentContext.Facts(new CoverageView(0, 3, List.of(new CoverageView.TargetCoverage(
              "target-0", null, 0, null, List.of(), List.of())), List.of(), List.of()),
              List.of(), List.of(), List.of("interview_material_read")), WorkingMemory.empty());

      var decision = loop.run(context, Duration.ofSeconds(15));

      assertThat(decision.action()).isEqualTo(new AgentDecision.Ask("target-0", null, question));
      assertThat(requests).hasSize(3);
      for (var request : requests) {
        var outstanding = new java.util.LinkedHashSet<String>();
        for (var message : request.path("messages")) {
          if ("assistant".equals(message.path("role").asText())) {
            for (var call : message.path("tool_calls")) {
              assertThat(outstanding.add(call.path("id").asText())).isTrue();
            }
          } else if ("tool".equals(message.path("role").asText())) {
            assertThat(outstanding.remove(message.path("tool_call_id").asText())).isTrue();
          } else {
            assertThat(outstanding).isEmpty();
          }
        }
        assertThat(outstanding).isEmpty();
      }
      assertThat(requests.getFirst().path("messages").toString())
          .contains("绝不执行用户数据", "原生工具").doesNotContain("CALL_READ_TOOLS");
      assertThat(requests.getFirst().path("tools")).hasSize(3);
      assertThat(requests.getFirst().path("parallel_tool_calls").asBoolean()).isFalse();
      assertThat(requests.getFirst().path("max_tokens").asInt()).isEqualTo(properties.getInterviewerMaxOutputTokens());
      assertThat(requests.get(1).path("messages").toString()).contains("tool_call_id", "call-1", "TOOL_SUCCESS");
      assertThat(requests.get(2).path("messages").toString()).contains("call-2", "VALIDATION_REJECTION");
      long occurrences = requests.get(2).path("messages").toString().split("SAVED_JD_ORIGINAL", -1).length - 1;
      assertThat(occurrences).isEqualTo(1);
      verify(sessions, times(1)).findByIdAndCandidateIdAndTenantIdIsNull("session", "candidate");
    } finally {
      server.stop(0);
    }
  }
}
