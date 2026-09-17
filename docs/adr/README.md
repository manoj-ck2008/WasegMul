# Architecture Decision Records (ADRs)

This directory stores Architecture Decision Records (ADRs) for WasegMul.

## Format

ADRs use sequential numbering: `0001-<slug>.md`, `0002-<slug>.md`, etc.

```markdown
# [Short title of the decision]

[1-3 sentences: what is the context, what did we decide, and why.]

## Status
Accepted

## Consequences (Optional)
[Non-obvious downstream impacts or trade-offs]
```

## When to Record an ADR
Record an ADR when a decision meets all three criteria:
1. **Hard to reverse**: High switching cost.
2. **Surprising without context**: A future reader will wonder why it was designed this way.
3. **The result of a real trade-off**: Deliberate choice between viable alternatives.
