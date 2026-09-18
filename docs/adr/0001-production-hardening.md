# 0001 Production Hardening from Deep-Dive Audit

Four parallel fix batches (UI/nav, shared KMP, data/ML/infra, scripts/build/iOS) plus lead
close-out fixes landed for every finding in `docs/DEEP_DIVE_AUDIT_REPORT.md` that re-verified
as still-open. This ADR records the hard-to-reverse choices so future readers know why the
code looks the way it does.

## Status

Accepted

## Decisions

1. **Trash is kept at rest, Residual at the edges.** Room rows, label assets
   (`category_classes.txt`), analytics history, and existing tests keep emitting `Trash`.
   New code normalizes via `normalizeCategoryLabel()` (Trash → Residual) at arbitration /
   persistence boundaries, and shared logic (`WasteMapping.RESIDUAL`, KB, DecayTime,
   EcoImpact) treats both identically. Rationale: renaming stored values would orphan
   installed-user databases and silently remap model outputs; a compat alias is safer
   than a flag-day migration.
2. **Eco factors stay coarse but are labeled estimates.** Per-category CO₂/water/energy
   constants match `docs/architecture.md` and now carry provenance KDoc plus
   `divertedItems`/`divertedWeightKg` (credited-only) alongside the legacy totals.
   Per-material LCA tables are a tracked follow-up, not this release.
3. **Tier-4 visual guesses and pseudo-codes never enter the barcode cache.**
   `BarcodeScanViewModel` shows visual-ML fallbacks in-memory only, and
   `quickClassifyAndSave(..., persistCache = false)` skips `code_*` stand-ins.
   Rationale: persisting guesses as ground truth poisoned all future scans of that code.
4. **Destructive migration removed.** `WasteDatabase` keeps only
   `fallbackToDestructiveMigrationOnDowngrade()`; a missing upgrade migration now throws
   loudly instead of wiping `waste_history` + barcode cache.
5. **Image normalization values are frozen until training truth is confirmed.**
   `ImagePreprocessor` documents the raw-[0,255] contract with a range-assert test hook;
   whichever side (code vs README/training) is wrong gets fixed with a regression test,
   not a guess.
6. **INT8 TFLite export requires a calibration dataset** (`data=`) plus an AP-regression
   check, else the script fails loudly. `taxonomy.yaml` is the single source for label
   sets (the `kaggle_train.py` fallback dict was deleted). `wasegmul://barcode` is a real
   deep link (nav route + manifest intent-filter).
7. **XP economy: confidence gate + 60 s same-subclass dedup + per-scan caps.**
   Both camera and barcode VMs track the last awarded scan and pass it to
   `GamificationManager.processNewScan()`; barcode scans earn XP at parity with camera.

## Consequences

- New `Residual` rows appear in history/DB going forward; filters, chips, and dashboards
  must treat `Trash` and `Residual` as the same bucket (single `CategoryColors` map does).
- `minSdk 29` + `java.time` depends on `isCoreLibraryDesugaringEnabled`; removing the
  desugar flag crashes pre-API-26 devices (see `SettingsManager` KDoc).
- `ModelBenchmarkTest` delegates to production helpers; YOLO parse/NMS/letterbox and Room
  migration tests still need Robolectric/`room-testing` (documented in-test as follow-ups).
