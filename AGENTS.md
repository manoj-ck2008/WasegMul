# WasegMul Agent Guide

Instructions and conventions for AI coding agents working in the WasegMul repository.

## Architecture & Codebase Layout

- **`app/`**: Android application built with Jetpack Compose, CameraX, Room SQLite DB, and Android-specific TensorFlow Lite bindings.
- **`shared/`**: Kotlin Multiplatform (KMP) shared library containing domain models, `MLArbitrator`, `EcoImpactCalculator`, `MessageGenerator`, `PredictionCodec`, and iOS bridge.
- **`iosApp/`**: iOS application shell consuming the shared KMP framework.
- **`scripts/`**: Python tooling for YOLO dataset preparation, Kaggle training, and TFLite model export.

## Engineering Standards

- **Domain Language**: Strictly adhere to the terminology in `CONTEXT.md`. Do not invent alternative names for established domain concepts.
- **Testing**: Follow TDD practices (`tdd` skill). Add tests at clean public seams (e.g., `MLArbitratorTest`, `EcoImpactTest`).
- **Decisions**: Record significant architectural changes in `docs/adr/`.

## Agent skills

26 Matt Pocock engineering/productivity skills are installed at `.agents/skills/` (auto-loaded by OpenCode, Claude Code, and Codex, no config needed). Skipped as not applicable here: TypeScript-only (`migrate-to-shoehorn`, `scaffold-exercises`), Claude-hooks-only (`git-guardrails-claude-code`), and `in-progress/*` (unstable). Update with `git clone --depth 1 https://github.com/mattpocock/skills` and re-copy (keep local edits minimal so updates stay clean).

### Issue tracker

Local markdown files under `.scratch/` with optional GitHub issue sync. See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical 5-role triage label vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context domain model and ADRs (`CONTEXT.md` + `docs/adr/`). See `docs/agents/domain.md`.
