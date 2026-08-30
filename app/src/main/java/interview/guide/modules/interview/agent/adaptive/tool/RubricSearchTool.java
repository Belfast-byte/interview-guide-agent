package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.common.util.Sha256;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation.AdoptableSource;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseQuestionRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/** 模型按需检索全局 ACTIVE 审核 rubric 目录。 */
@Component
public class RubricSearchTool implements ReadOnlyAgentTool {

  public static final String NAME = "rubric_search";
  private static final Set<String> ARGUMENTS = Set.of("query", "intent", "levelHints");
  private static final int CANDIDATE_MULTIPLIER = 3;

  private final VectorStore vectorStore;
  private final KnowledgeBaseQuestionRepository questionRepository;
  private final ToolProperties properties;

  public RubricSearchTool(
      VectorStore vectorStore,
      KnowledgeBaseQuestionRepository questionRepository,
      ToolProperties properties
  ) {
    this.vectorStore = vectorStore;
    this.questionRepository = questionRepository;
    this.properties = properties;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void validate(ReadToolRequest request) {
    Set<String> unknown = new HashSet<>(request.arguments().keySet());
    unknown.removeAll(ARGUMENTS);
    if (!unknown.isEmpty()) {
      throw new ReadToolValidationException(
          "arguments." + unknown.iterator().next(), "不支持的参数");
    }
    requireText(request.arguments().get("query"), "arguments.query");
    requireText(request.arguments().get("intent"), "arguments.intent");
    levelHints(request.arguments().get("levelHints"));
  }

  @Override
  public ReadToolResult execute(ReadToolRequest request) {
    String queryText = semanticQuery(request.arguments());
    List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
        .query(queryText)
        .topK(properties.getRubricSearchLimit() * CANDIDATE_MULTIPLIER)
        .similarityThreshold(properties.getRubricMinScore())
        .filterExpression("document_type == '" + RubricVectorIndexer.DOCUMENT_TYPE + "'")
        .build());
    List<RubricHit> hits = authoritativeHits(documents);
    if (hits.isEmpty()) {
      return new ReadToolResult.Empty("没有命中可用的 ACTIVE rubric");
    }
    return new ReadToolResult.Success(
        Map.of("hits", hits.stream().map(RubricHit::data).toList()),
        hits.stream().map(RubricHit::source).toList()
    );
  }

  private List<RubricHit> authoritativeHits(List<Document> documents) {
    if (documents.isEmpty()) {
      return List.of();
    }
    List<Long> rankedIds = documents.stream().map(document -> Long.parseLong(
        (String) document.getMetadata().get("question_id"))).toList();
    Map<Long, KnowledgeBaseQuestionEntity> questions = questionRepository.findAllById(rankedIds)
        .stream().collect(Collectors.toMap(KnowledgeBaseQuestionEntity::getId, Function.identity()));
    List<RubricHit> hits = new ArrayList<>();
    for (Long questionId : rankedIds) {
      KnowledgeBaseQuestionEntity question = questions.get(questionId);
      if (usable(question)) {
        hits.add(toHit(question));
      }
      if (hits.size() == properties.getRubricSearchLimit()) {
        break;
      }
    }
    return List.copyOf(hits);
  }

  private boolean usable(KnowledgeBaseQuestionEntity question) {
    return question != null
        && question.getStatus() == KnowledgeBaseQuestionStatus.ACTIVE
        && question.getScoringRubric() != null
        && !question.getScoringRubric().isBlank();
  }

  private RubricHit toHit(KnowledgeBaseQuestionEntity question) {
    String rubric = normalizeRubric(question.getScoringRubric());
    String entryId = "question:" + question.getId() + ":rubric";
    String version = Sha256.hex(rubric);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("entryId", entryId);
    data.put("version", version);
    data.put("category", question.getCategory());
    data.put("difficulty", question.getDifficulty());
    data.put("rubric", rubric);
    return new RubricHit(
        Map.copyOf(data),
        new AdoptableSource(
            "rubric:" + entryId + "@" + version,
            "rubric",
            entryId,
            version
        )
    );
  }

  private String semanticQuery(Map<String, Object> arguments) {
    List<String> parts = new ArrayList<>();
    parts.add((String) arguments.get("query"));
    parts.add((String) arguments.get("intent"));
    parts.addAll(levelHints(arguments.get("levelHints")));
    return String.join("\n", parts);
  }

  private List<String> levelHints(Object value) {
    if (!(value instanceof List<?> values)) {
      throw new ReadToolValidationException("arguments.levelHints", "必须为字符串数组");
    }
    List<String> hints = new ArrayList<>();
    for (int index = 0; index < values.size(); index++) {
      Object item = values.get(index);
      if (!(item instanceof String text)) {
        throw new ReadToolValidationException(
            "arguments.levelHints[" + index + "]", "必须为字符串");
      }
      hints.add(text);
    }
    return List.copyOf(hints);
  }

  private void requireText(Object value, String field) {
    if (!(value instanceof String text) || text.isBlank()) {
      throw new ReadToolValidationException(field, "必须为非空字符串");
    }
  }

  private String normalizeRubric(String rubric) {
    return rubric.replace("\r\n", "\n").replace('\r', '\n').strip();
  }

  private record RubricHit(Map<String, Object> data, AdoptableSource source) {}
}
