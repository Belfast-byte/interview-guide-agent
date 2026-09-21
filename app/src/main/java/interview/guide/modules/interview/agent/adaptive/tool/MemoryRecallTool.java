package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeQueryService;
import interview.guide.modules.interview.agent.adaptive.memory.episode.exposure.QuestionExposureRepository;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMemoryService;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation.AdoptableSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation;

/** 只按当前计划的知识点召回；如何换场景由 Agent 根据原问答决定。 */
@Component
@RequiredArgsConstructor
public class MemoryRecallTool implements ReadOnlyAgentTool {
  private final EpisodeQueryService episodes;
  private final QuestionExposureRepository exposures;

  @Tool(name = "memory_recall", description = "按当前计划目标读取练习历史及原题曝光。评估模式仅返回曝光题目，历史评级不能用于当前评分。")
  public DecisionObservation query(
      @ToolParam(description = "当前计划的目标 ID") String targetId,
      ToolContext toolContext) {
    var scope = InterviewToolContext.from(toolContext);
    var arguments = new java.util.LinkedHashMap<String, Object>();
    arguments.put("targetId", targetId);
    var request = new ReadToolRequest(scope.context(), arguments, scope.deadlineNanos());
    validate(request);
    return scope.observe("memory_recall", execute(request));
  }

  public String name() {
    return "memory_recall";
  }

  public void validate(ReadToolRequest request) {
    if (!request.arguments().keySet().equals(Set.of("targetId"))) {
      throw new ReadToolValidationException("arguments", "只需提供 targetId");
    }
    if (request.context().facts().coverage().targets().stream().noneMatch(target ->
        target.targetId().equals(request.arguments().get("targetId")))) {
      throw new ReadToolValidationException("arguments.targetId", "目标不属于当前计划");
    }
  }

  public ReadToolResult execute(ReadToolRequest request) {
    var owner = request.context().session().identity().owner();
    var target = request.context().facts().coverage().targets().stream()
        .filter(item -> item.targetId().equals(request.arguments().get("targetId")))
        .findFirst().orElseThrow();
    var topic = target.target().identity().topic();
    var page = PageRequest.of(0, PracticeMemoryService.RECALL_SIZE);
    var questions = exposures.findByOwnerAndTopic(owner, topic, page).stream()
        .map(exposure -> exposure.toDomain().questionText()).toList();
    // 评估模式只看曝光题目防止原题重复，不能读取历史回答和能力结论。
    if (request.context().session().mode() == SessionMode.EVALUATION) {
      return new ReadToolResult.Success(Map.of("recentQuestions", questions), List.of());
    }
    var history = episodes.recent(owner, topic, page);
    return new ReadToolResult.Success(Map.of("episodes", history, "recentQuestions", questions,
        "usage", "参考原场景和 gap 换场景练习；历史等级不用于当前评分"),
        history.stream().map(episode -> new AdoptableSource(episode.reference(), "episode",
            Long.toString(episode.episodeId()), null)).toList());
  }
}
