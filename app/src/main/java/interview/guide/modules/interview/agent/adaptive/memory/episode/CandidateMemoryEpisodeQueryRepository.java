package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** 召回与页面共用正式问答投影，归属限定在数据库查询中完成。 */
public interface CandidateMemoryEpisodeQueryRepository extends Repository<EpisodeFactEntity, Long> {

  String FACTS = """
      SELECT episode.id AS episodeId, episode.sessionId AS sessionId,
             episode.turnIndex AS turnIndex, episode.sessionMode AS sessionMode,
             episode.assessmentId AS assessmentId,
             episode.skillId AS skillId, episode.focusId AS focusId,
             turn.question AS question, turn.answer AS answer,
             turn.parentTurnIndex AS parentTurnIndex, turn.triggerType AS triggerType,
             assessment.depthLevel AS depthLevel, assessment.rationaleSummary AS rationaleSummary,
             plan.expectedDepth AS expectedDepth, episode.createdAt AS createdAt
      FROM EpisodeFactEntity episode
      JOIN episode.assessment assessment
      JOIN AdaptiveAgentTurnEntity turn ON turn.id = episode.turnId
      LEFT JOIN AdaptiveAgentPlanEntity plan ON plan.sessionId = episode.sessionId
           AND plan.dimensionOrder = assessment.dimensionOrder
      WHERE episode.candidateId = :#{#owner.candidateId}
        AND ((:#{#owner.tenantId} IS NULL AND episode.tenantId IS NULL)
             OR episode.tenantId = :#{#owner.tenantId})
      """;
  String NEWEST = " ORDER BY episode.createdAt DESC, episode.id DESC";

  @Query(value = FACTS + NEWEST, countQuery = """
      SELECT COUNT(episode) FROM EpisodeFactEntity episode
      WHERE episode.candidateId = :#{#owner.candidateId}
        AND ((:#{#owner.tenantId} IS NULL AND episode.tenantId IS NULL)
             OR episode.tenantId = :#{#owner.tenantId})
      """)
  Page<EpisodeProjection> findByOwner(MemoryOwner owner, Pageable pageable);

  @Query(FACTS + """
      AND episode.skillId = :#{#topic.skillId} AND episode.focusId = :#{#topic.focusId}
      """ + NEWEST)
  List<EpisodeProjection> findByTopic(MemoryOwner owner, TopicKey topic, Pageable pageable);

  // 最近表现按每个知识点选取；不取最高分或计算另一种综合等级。
  @Query(FACTS + """
      AND NOT EXISTS (
        SELECT newer.id FROM EpisodeFactEntity newer
        WHERE newer.candidateId = episode.candidateId
          AND ((newer.tenantId IS NULL AND episode.tenantId IS NULL)
               OR newer.tenantId = episode.tenantId)
          AND newer.skillId = episode.skillId AND newer.focusId = episode.focusId
          AND (newer.createdAt > episode.createdAt
               OR (newer.createdAt = episode.createdAt AND newer.id > episode.id))
      )
      """ + NEWEST)
  List<EpisodeProjection> findLatestByTopic(MemoryOwner owner);

  // sessionIds 来自上面的归属查询；前文即使没有旧 Episode 索引也能正常读取。
  @Query("""
      SELECT turn.sessionId AS sessionId, turn.turnIndex AS turnIndex,
             turn.parentTurnIndex AS parentTurnIndex,
             turn.question AS question, turn.answer AS answer
      FROM AdaptiveAgentTurnEntity turn WHERE turn.sessionId IN :sessionIds
      """)
  List<PriorTurnProjection> findSessionTurns(Collection<String> sessionIds);

  interface PriorTurnProjection {
    String getSessionId();
    int getTurnIndex();
    Integer getParentTurnIndex();
    String getQuestion();
    String getAnswer();
  }

  interface EpisodeProjection {
    long getEpisodeId();
    String getSessionId();
    int getTurnIndex();
    SessionMode getSessionMode();
    long getAssessmentId();
    String getSkillId();
    String getFocusId();
    String getQuestion();
    String getAnswer();
    Integer getParentTurnIndex();
    TurnTriggerType getTriggerType();
    DepthLevel getDepthLevel();
    DepthLevel getExpectedDepth();
    String getRationaleSummary();
    LocalDateTime getCreatedAt();
  }
}
