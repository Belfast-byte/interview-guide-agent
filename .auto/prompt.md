# Autoresearch: Adaptive interview agent ablation

## Objective
Identify adaptive-agent runtime pieces that can be disconnected or removed without changing documented user-visible behavior. Use controlled, one-factor-at-a-time ablations. A passing result means the production footprint is smaller while the unchanged backend test suite still passes.

This is a complexity ablation, not a latency contest. Do not weaken, delete, skip, or rewrite tests to make an ablation pass. Do not change benchmark inputs or count generated/build files. Distinguish “not covered by tests” from “proven unnecessary.”

## Metrics
- **Primary**: `adaptive_removed_loc` (lines, higher is better) — verified baseline (17,048) minus current nonblank adaptive Java lines.
- **Secondary**: `adaptive_prod_loc`, `adaptive_prod_files`, `adaptive_spring_components`, `adaptive_test_files` — footprint and guardrail visibility.

## How to Run
`./.auto/measure.sh` outputs structured `METRIC name=value` lines. After each passing measurement, `.auto/checks.sh` runs the unchanged backend test suite.

## Saved Baseline
- Commit: `43de0d974ddd55e6f65b9b9148b47ab6402de233`
- Tag: `autoresearch-agent-ablation-baseline-20260902-103116`
- Experiment branch: `autoresearch/agent-ablation-20260902-103116`
- Remote push was attempted but GitHub HTTPS credentials are unavailable in this environment. Local refs are intact.

## Files in Scope
- `app/src/main/java/interview/guide/modules/interview/agent/adaptive/**` — production ablations only.
- `app/src/main/resources/prompts/adaptive-agent-*.st` — only when the corresponding production capability is removed.
- `.auto/**` — experiment records and scripts.

## Off Limits
- `app/src/test/**` — tests must remain byte-for-byte unchanged.
- Benchmark/check scripts may not exclude failing tests or special-case an ablation.
- Database migrations are historical facts and must not be rewritten.
- Frontend, unrelated backend modules, dependencies, and public API behavior.
- Security, ownership, evidence provenance, sandbox isolation/idempotency, concurrency owners, and model/Java control boundaries documented in spec 36.

## Constraints
- Work only on the dedicated experiment branch.
- Change one independently explainable component per experiment.
- Run the exact same metric and full backend checks every time.
- A compile/test failure is discarded, not patched by weakening tests.
- Static non-use plus passing tests supports a removal candidate; it does not prove semantic quality where no evaluation corpus exists.
- Prefer disconnecting dead runtime wiring before deleting compatibility types used only by isolated tests.
- Do not overfit to tests or benchmark implementation.

## Initial Candidates
1. Persisted DimensionBrief read path: records are queried into `PlannedInterview` but have no production consumer and no production writer.
2. Unreferenced `AdaptiveMemoryFacts` record.
3. DimensionBrief Spring bean wiring: generation classes appear to have no production caller; retain types initially so unchanged tests still compile.
4. Question-bank indexing is not an interview Agent Tool, but it is consumed by practice recommendations; do not classify it as dead without preserving that product behavior.
5. Episode enrichment/recovery and semantic memory have API consumers; require stronger evidence before ablation.

## What's Been Tried
- Baseline not yet measured.
