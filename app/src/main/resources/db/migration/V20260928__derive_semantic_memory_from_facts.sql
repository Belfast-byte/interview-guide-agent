ALTER TABLE candidate_memory_episode_facts
  DROP CONSTRAINT memory_episode_revision_check,
  DROP COLUMN work_revision_before,
  DROP COLUMN work_revision_after;

-- Semantic state is derived on read from immutable contributions and enrichment tags.
DROP TABLE candidate_semantic_states;
