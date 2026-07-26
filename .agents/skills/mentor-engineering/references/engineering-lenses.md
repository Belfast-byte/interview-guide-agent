# Engineering Lenses

Use this reference for non-trivial feature design, architecture changes, refactors, integrations, AI workflows, and hard bugs. Select the few lenses that can change the decision; do not dump the entire checklist.

## Business lenses

### Actor and value

- Who initiates the workflow?
- Who receives the outcome?
- What user or business cost exists if it is slow, duplicated, missing, or wrong?

### Workflow and state

- What are the meaningful states?
- Which transitions are legal?
- Who is allowed to trigger each transition?
- What happens when the process stops halfway?

### Invariants

- What must never be duplicated, lost, exposed, or silently changed?
- Which conclusion must be traceable to source evidence?
- Which input is an unverified claim rather than trusted fact?

### Success and feedback

- What observable behavior proves the feature works?
- What metrics reveal degradation?
- How can a user understand and recover from failure?

## Architecture lenses

### Boundaries and ownership

- Which module owns the rule and the state?
- Is a controller, service, repository, or infrastructure adapter taking responsibility that belongs elsewhere?
- Does one source of truth exist?

### Transactions and external calls

- Is an LLM, S3, HTTP, or other slow call inside a database transaction?
- Can the workflow persist partial state safely?
- What happens if the external call succeeds but persistence fails, or vice versa?

### Idempotency and retries

- Can the same command arrive twice?
- What stable key identifies the logical operation?
- Does retry repeat an external side effect?
- Can a completed request return its previous result?

### Concurrency

- Can two actors update the same state?
- Which update wins, and how is a lost update detected?
- Is a unique constraint, version, conditional update, or lock the correct guard?

### Consistency and caching

- Which store is authoritative?
- Can cached state become newer or older than durable state?
- Is cache failure allowed to change correctness?
- How is stale data detected or repaired?

### Async and backpressure

- Does asynchronous work improve the user outcome or only hide latency?
- How are retry, poison messages, deletion, ordering, and duplicate delivery handled?
- What limits prevent unbounded queues or model calls?

### Evolution

- Is the database migration additive and backward compatible?
- Can old and new application versions coexist during rollout?
- Is serialized state versioned?
- Can a new enum value break old readers?

### Observability

- Can operators identify the workflow, state, action, latency, and failure stage without logging sensitive content?
- Is there a metric for the key business invariant?
- Does the error tell the user whether retry is safe?

## Common project pitfalls

### AI workflow

- Treat JD, resume, documents, and answers as untrusted data, not instructions.
- Validate structured output after model retries.
- Keep deterministic limits and state transitions in code.
- Never let generated evidence quote text that is absent from the source.
- Keep LLM calls outside transactions.
- Make retries and duplicate submissions safe.

### Backend

- Keep business orchestration in Service, not Controller.
- Avoid self-invoked transactional methods.
- Avoid per-item database calls when a batch query is possible.
- Preserve the exception and logging conventions of the repository.

### Frontend

- Handle loading, disabled, error, retry, empty, and stale-response states.
- Prevent double submission.
- Preserve user input when a retry is safe.
- Test refresh and navigation, not only the uninterrupted happy path.

### Data

- Provide defaults for existing rows.
- Validate indexes against query shapes.
- Treat schema migration and application deployment as separate failure points.
- Confirm test databases exercise the intended constraints.

## Learning exercises

Use at most one when it helps. Make it optional and non-blocking: invite the user to think, then continue the task with best judgment. Pause only when they explicitly request an interactive exercise or their decision is genuinely required.

- **Predict**: ask what happens during a duplicate, timeout, or concurrent update.
- **Compare**: contrast two viable designs against the same constraints.
- **Break it**: construct the smallest scenario that violates an invariant.
- **Trace it**: follow one request through UI, API, service, persistence, and recovery.
- **Challenge the assumption**: find one counterexample that would invalidate the current design.
- **Teach back**: ask the user to summarize the invariant or trade-off after the task is complete.
