package interview.guide.modules.interview.agent.adaptive.tool;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** rubric 专用向量索引元数据仓储。 */
@Repository
@RequiredArgsConstructor
class RubricVectorIndexRepository {

  private final JdbcTemplate jdbcTemplate;

  List<RubricSnapshot> findPending(int limit) {
    return jdbcTemplate.query(
        """
        SELECT q.id, q.question, q.category, q.topic_summary,
               q.difficulty, q.scoring_rubric, q.updated_at
        FROM knowledge_base_questions q
        LEFT JOIN agent_rubric_index i ON i.question_id = q.id
        WHERE q.status = 'ACTIVE'
          AND q.scoring_rubric IS NOT NULL
          AND q.scoring_rubric <> ''
          AND (i.question_id IS NULL OR i.source_updated_at <> q.updated_at)
        ORDER BY q.updated_at ASC, q.id ASC
        LIMIT ?
        """,
        (resultSet, rowNumber) -> new RubricSnapshot(
            resultSet.getLong("id"),
            resultSet.getString("question"),
            resultSet.getString("category"),
            resultSet.getString("topic_summary"),
            resultSet.getString("difficulty"),
            resultSet.getString("scoring_rubric"),
            resultSet.getTimestamp("updated_at").toLocalDateTime()
        ),
        limit
    );
  }

  List<IndexedRubric> findStale(int limit) {
    return jdbcTemplate.query(
        """
        SELECT i.question_id, i.document_id
        FROM agent_rubric_index i
        LEFT JOIN knowledge_base_questions q ON q.id = i.question_id
        WHERE q.id IS NULL
           OR q.status <> 'ACTIVE'
           OR q.scoring_rubric IS NULL
           OR q.scoring_rubric = ''
        ORDER BY i.question_id ASC
        LIMIT ?
        """,
        (resultSet, rowNumber) -> new IndexedRubric(
            resultSet.getLong("question_id"),
            resultSet.getObject("document_id", UUID.class)
        ),
        limit
    );
  }

  void markIndexed(List<IndexedSnapshot> snapshots) {
    jdbcTemplate.batchUpdate(
        """
        INSERT INTO agent_rubric_index (
          question_id, document_id, source_updated_at, indexed_at
        ) VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT (question_id) DO UPDATE SET
          document_id = EXCLUDED.document_id,
          source_updated_at = EXCLUDED.source_updated_at,
          indexed_at = CURRENT_TIMESTAMP
        """,
        snapshots.stream().map(snapshot -> new Object[] {
            snapshot.questionId(),
            snapshot.documentId(),
            Timestamp.valueOf(snapshot.sourceUpdatedAt())
        }).toList()
    );
  }

  void deleteIndexEntries(List<Long> questionIds) {
    jdbcTemplate.batchUpdate(
        "DELETE FROM agent_rubric_index WHERE question_id = ?",
        questionIds.stream().map(questionId -> new Object[] {questionId}).toList()
    );
  }

  record RubricSnapshot(
      long id,
      String question,
      String category,
      String topicSummary,
      String difficulty,
      String scoringRubric,
      LocalDateTime updatedAt
  ) {}

  record IndexedRubric(long questionId, UUID documentId) {}

  record IndexedSnapshot(long questionId, UUID documentId, LocalDateTime sourceUpdatedAt) {}
}
