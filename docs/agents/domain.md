# Domain Docs

How engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root: Contains the project's ubiquitous domain language and terminology glossary.
- **`docs/adr/`**: Read ADRs that touch the area you are about to work in.

If any of these files do not exist, proceed silently. The `/domain-modeling` skill creates or updates them lazily when terms or decisions get resolved.

## File structure

Single-context repo layout:

```
/
├── CONTEXT.md
├── docs/adr/
│   └── 0001-two-stage-yolo-classification-architecture.md
├── app/
├── shared/
└── scripts/
```

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a test name, or variable/function naming), use the term as defined in `CONTEXT.md`. Avoid invented synonyms.

## Flag ADR conflicts

If an implementation or proposal contradicts an existing ADR in `docs/adr/`, flag it explicitly with rationale.
