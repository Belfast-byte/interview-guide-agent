ALTER TABLE agent_turns
  ADD COLUMN question_source_id VARCHAR(128),
  ADD COLUMN question_difficulty VARCHAR(16),
  ADD CONSTRAINT agent_turn_question_provenance_check
    CHECK (
      (question_source_id IS NULL AND question_difficulty IS NULL)
      OR
      (question_source_id IS NOT NULL AND question_difficulty IS NOT NULL)
    );
