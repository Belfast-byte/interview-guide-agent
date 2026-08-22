package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.common.util.Sha256;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseQuestionRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * 深度量规查询工具，供 Agent 获取当前维度的评级标准。
 */
@Component
public class RubricLookupTool implements AdaptiveAgentTool {

  public static final String NAME = "rubric_lookup";

  private final KnowledgeBaseQuestionRepository questionRepository;
  private final int limit;
  private final ToolCallback callback;

  public RubricLookupTool(
      KnowledgeBaseQuestionRepository questionRepository,
      ToolProperties properties
  ) {
    this.questionRepository = questionRepository;
    this.limit = properties.getQuestionBankLimit();
    this.callback = ToolCallbacks.gatewayOnly(
        NAME,
        "Load only the reviewed scoring rubric fragments relevant to one dimension",
        RubricLookupInput.class
    );
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public ToolCallback callback() {
    return callback;
  }

  @Override
  public ToolResult execute(Map<String, Object> arguments) {
    String dimension = ToolArguments.requiredString(arguments, "dimension", 100);
    List<RubricPayload> rubrics = questionRepository.findRubricsByDimension(
            KnowledgeBaseQuestionStatus.ACTIVE,
            dimension,
            PageRequest.of(0, limit)
        ).stream()
        .map(this::toPayload)
        .toList();
    String resultId = "rubric-search:" + Sha256.hex(rubrics.stream()
        .map(rubric -> String.valueOf(rubric.questionId()))
        .sorted()
        .collect(Collectors.joining(",")));
    return new CompletedToolResult(
        resultId,
        rubrics,
        "rubricQuestionIds=" + rubrics.stream().map(RubricPayload::questionId).toList()
    );
  }

  private RubricPayload toPayload(KnowledgeBaseQuestionEntity question) {
    return new RubricPayload(
        "question:" + question.getId() + ":rubric",
        question.getId(),
        question.getCategory(),
        question.getScoringRubric()
    );
  }

  record RubricLookupInput(String dimension) {}

  record RubricPayload(
      String stableId,
      Long questionId,
      String dimension,
      String rubric
  ) {}
}
