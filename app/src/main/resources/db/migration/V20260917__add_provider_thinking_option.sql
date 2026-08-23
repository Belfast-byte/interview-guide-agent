ALTER TABLE llm_provider_config
    ADD COLUMN thinking_disabled BOOLEAN NOT NULL DEFAULT FALSE;
