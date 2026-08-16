package interview.guide.modules.interview.agent.adaptive.tool;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 题库向量索引仓储。
 */
@Repository
@RequiredArgsConstructor
class QuestionBankVectorIndexRepository {

  private final JdbcTemplate jdbcTemplate;

  List<QuestionSnapshot> findPending(int limit) {
    return jdbcTemplate.query(
        """
        SELECT q.id, q.question, q.category, q.topic_summary, q.difficulty, q.updated_at
        FROM knowledge_base_questions q
        LEFT JOIN agent_question_index i ON i.question_id = q.id
        WHERE q.status = 'ACTIVE'
          AND (i.question_id IS NULL OR i.source_updated_at <> q.updated_at)
        ORDER BY q.updated_at ASC, q.id ASC
        LIMIT ?
        """,
        (resultSet, rowNumber) -> new QuestionSnapshot(
            resultSet.getLong("id"),
            resultSet.getString("question"),
            resultSet.getString("category"),
            resultSet.getString("topic_summary"),
            resultSet.getString("difficulty"),
            resultSet.getTimestamp("updated_at").toLocalDateTime()
        ),
        limit
    );
  }

  List<IndexedQuestion> findStale(int limit) {
    return jdbcTemplate.query(
        """
        SELECT i.question_id, i.document_id
        FROM agent_question_index i
        LEFT JOIN knowledge_base_questions q ON q.id = i.question_id
        WHERE q.id IS NULL OR q.status <> 'ACTIVE'
        ORDER BY i.question_id ASC
        LIMIT ?
        """,
        (resultSet, rowNumber) -> new IndexedQuestion(
            resultSet.getLong("question_id"),
            resultSet.getObject("document_id", UUID.class)
        ),
        limit
    );
  }

  void markIndexed(List<IndexedSnapshot> snapshots) {
    jdbcTemplate.batchUpdate(
        """
        INSERT INTO agent_question_index (
          question_id, document_id, source_updated_at, indexed_at
        ) VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT (question_id) DO UPDATE SET
          document_id = EXCLUDED.document_id,
          source_updated_at = EXCLUDED.source_updated_at,
          indexed_at = CURRENT_TIMESTAMP
        """,
        snapshots.stream()
            .map(snapshot -> new Object[] {
                snapshot.questionId(),
                snapshot.documentId(),
                Timestamp.valueOf(snapshot.sourceUpdatedAt())
            })
            .toList()
    );
  }

  void deleteIndexEntries(List<Long> questionIds) {
    jdbcTemplate.batchUpdate(
        "DELETE FROM agent_question_index WHERE question_id = ?",
        questionIds.stream().map(questionId -> new Object[] {questionId}).toList()
    );
  }

  record QuestionSnapshot(
      long id,
      String question,
      String category,
      String topicSummary,
      String difficulty,
      LocalDateTime updatedAt
  ) {}

  record IndexedQuestion(long questionId, UUID documentId) {}

  record IndexedSnapshot(
      long questionId,
      UUID documentId,
      LocalDateTime sourceUpdatedAt
  ) {}
}
