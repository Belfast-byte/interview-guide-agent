ALTER TABLE algorithm_problems
  ADD COLUMN variant_group VARCHAR(64);

UPDATE algorithm_problems
SET variant_group = id;

ALTER TABLE algorithm_problems
  ALTER COLUMN variant_group SET NOT NULL;

CREATE INDEX idx_algorithm_problems_variant
  ON algorithm_problems (variant_group, difficulty);
