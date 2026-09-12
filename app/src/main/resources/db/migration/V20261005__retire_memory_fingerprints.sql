-- 指纹机制已移除。保留历史列的数据，新代码不再映射、读取或写入这些列。
ALTER TABLE agent_question_exposures ALTER COLUMN scenario_fingerprint DROP NOT NULL;
ALTER TABLE agent_question_exposures ALTER COLUMN wording_fingerprint DROP NOT NULL;
ALTER TABLE candidate_memory_observation_revisions ALTER COLUMN input_fingerprint DROP NOT NULL;
ALTER TABLE candidate_memory_observation_revisions ALTER COLUMN opportunity_key DROP NOT NULL;
