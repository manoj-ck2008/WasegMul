# Issue Tracker: Local Markdown & GitHub

Issues, specs, and tracer tickets for WasegMul live as local markdown files under `.scratch/`, with optional synchronization to GitHub Issues (`https://github.com/manoj-ck2008/WasegMul`).

## Conventions (Local Markdown)

- **One feature per directory**: `.scratch/<feature-slug>/`
- **Specification**: `.scratch/<feature-slug>/spec.md`
- **Implementation tickets**: One file per ticket under `.scratch/<feature-slug>/issues/<NN>-<slug>.md`, numbered starting from `01` (never combine multiple tickets into one file).
- **Triage status**: Recorded in the `Status:` field at the top of the file (see `docs/agents/triage-labels.md`).
- **Comments & discussion**: Appended under `## Comments`.

### Example Ticket Format

```markdown
# 01-seam-arbitrator-test

Status: ready-for-agent
Blocked by: none
Type: task

## Goal
Implement public unit tests for MLArbitrator hierarchical fallback.

## Seam
`MLArbitrator.arbitrate(prediction: PredictionResult): ClassificationResult`

## Verification

`./gradlew :app:testDebugUnitTest --tests "com.agrelius.wasegmul.*"`

```

## GitHub Issues Alternative

When the GitHub CLI (`gh`) is available in the environment:
- **Repo**: `manoj-ck2008/WasegMul`
- **Create**: `gh issue create --title "..." --body "..."`
- **View**: `gh issue view <number> --comments`
- **List**: `gh issue list --state open`

## When a Skill Says "Publish to Issue Tracker"
Create a new file under `.scratch/<feature-slug>/` (or create a GitHub issue if configured).

## When a Skill Says "Fetch the Relevant Ticket"
Read the file at `.scratch/<feature-slug>/issues/<NN>-<slug>.md` or view the GitHub issue.

## Wayfinding Operations (`/wayfinder`)
- **Map**: `.scratch/<effort>/map.md` (holding Notes, Decisions-so-far, and Fog body).
- **Child ticket**: `.scratch/<effort>/issues/<NN>-<slug>.md`.
- **Blocking**: A `Blocked by: <NN>, <NN>` line. Unblocked when all listed tickets have `Status: resolved`.
