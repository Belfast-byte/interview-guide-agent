package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.interview.skill.InterviewSkillService.ReferenceResource;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/** classpath 资料是原文依据；现有 vector_store 只保存可重建片段，没有另一张业务事实表。 */
@Slf4j
@Component
public class SkillReferenceIndex {
  static final String DOCUMENT_TYPE = "skill_reference";
  private final InterviewSkillService skills;
  private final VectorStore vectors;
  private final JdbcTemplate jdbc;
  private volatile Catalog catalog;
  private volatile boolean ready;

  public SkillReferenceIndex(InterviewSkillService skills, VectorStore vectors, JdbcTemplate jdbc) {
    this.skills = skills;
    this.vectors = vectors;
    this.jdbc = jdbc;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initialize() {
    try {
      sync();
    } catch (Exception e) {
      // 初始化失败不阻断文字答题，工具明确报告不可用；运维可修复后重启重建。
      log.error("skill_reference_index_unavailable", e);
    }
  }

  /** 启动时对比原文，仅重新向量化新增/变化片段；资源更新、删除后重启即可同步。 */
  public synchronized void sync() {
    ready = false;
    Catalog current = catalog(skills.referenceResources());
    catalog = current;
    Map<String, String> stored = new LinkedHashMap<>();
    jdbc.query("SELECT id, content FROM vector_store WHERE metadata->>'document_type' = ?",
        rs -> { stored.put(rs.getString("id"), rs.getString("content")); }, DOCUMENT_TYPE);
    List<String> stale = stored.keySet().stream().filter(id -> !current.chunks().containsKey(id)).toList();
    if (!stale.isEmpty()) vectors.delete(stale);
    var changed = current.chunks().values().stream()
        .filter(chunk -> !chunk.text().equals(stored.get(chunk.chunkId()))).toList();
    // 沿用 Embedding 服务的小批量约束，不持有数据库事务调用模型。
    for (int offset = 0; offset < changed.size(); offset += 10) {
      vectors.add(changed.subList(offset, Math.min(offset + 10, changed.size())).stream()
          .map(chunk -> Document.builder().id(chunk.chunkId()).text(chunk.text())
              .metadata(Map.of("document_type", DOCUMENT_TYPE, "source_id", chunk.sourceId()))
              .build()).toList());
    }
    ready = true;
    log.info("skill_reference_index_ready chunks={} updated={} removed={}",
        current.chunks().size(), changed.size(), stale.size());
  }

  public boolean ready() { return ready; }

  Set<String> sources(TopicKey topic) {
    requireReady();
    return catalog.sources().getOrDefault(topic, Set.of());
  }

  Chunk authoritative(Document document, Set<String> allowedSources) {
    requireReady();
    Chunk chunk = catalog.chunks().get(document.getId());
    if (chunk == null || !allowedSources.contains(chunk.sourceId())
        || !DOCUMENT_TYPE.equals(document.getMetadata().get("document_type"))
        || !chunk.sourceId().equals(document.getMetadata().get("source_id"))
        || !chunk.text().equals(document.getText())) return null;
    return chunk;
  }

  private void requireReady() {
    if (!ready) throw new IllegalStateException("专业参考索引尚不可用");
  }

  static Catalog catalog(List<ReferenceResource> resources) {
    Map<TopicKey, Set<String>> sources = new LinkedHashMap<>();
    Map<String, String> originals = new LinkedHashMap<>();
    for (var resource : resources) {
      sources.computeIfAbsent(new TopicKey(resource.skillId(), resource.focusId()), key -> new LinkedHashSet<>())
          .add(resource.sourceId());
      originals.putIfAbsent(resource.sourceId(), resource.text());
    }
    var splitter = TokenTextSplitter.builder().withChunkSize(450).withMinChunkSizeChars(0)
        .withMinChunkLengthToEmbed(1).withMaxNumChunks(10000).withKeepSeparator(true).build();
    Map<String, Chunk> chunks = new LinkedHashMap<>();
    originals.forEach((sourceId, text) -> {
      int ordinal = 0;
      for (String section : text.split("(?m)(?=^#{1,6} )")) {
        if (section.isBlank()) continue;
        String title = section.lines().findFirst().orElse(sourceId).replaceFirst("^#+\\s*", "");
        for (Document part : splitter.apply(List.of(new Document(section)))) {
          // 身份由资源与位置决定，内容变化由原文比较识别；不产生记忆指纹。
          String id = UUID.nameUUIDFromBytes(("skill-reference:" + sourceId + ":" + ordinal++)
              .getBytes(StandardCharsets.UTF_8)).toString();
          chunks.put(id, new Chunk(id, sourceId, title, part.getText()));
        }
      }
    });
    Map<TopicKey, Set<String>> immutableSources = new LinkedHashMap<>();
    sources.forEach((topic, ids) -> immutableSources.put(topic, Set.copyOf(ids)));
    return new Catalog(Map.copyOf(chunks), Map.copyOf(immutableSources));
  }

  record Chunk(String chunkId, String sourceId, String title, String text) {}
  record Catalog(Map<String, Chunk> chunks, Map<TopicKey, Set<String>> sources) {}
}
