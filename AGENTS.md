# WatchCal Agent Instructions

`AGENTS.md` is the source of truth for agent instructions. `CLAUDE.md` is a symlink to it. Edit `AGENTS.md` only.

## Spec-as-source-driven development

WatchCal is developed **spec-as-source**: the specs are the artifact of record, the codebase is one materialization of them. The specs must be complete enough that a competent agent could discard the code and reimplement WatchCal from them alone.

```text
specs/00-product.md       = functional spec   (what the app does, reminder semantics, non-goals)
specs/01-architecture.md  = technical spec    (how the implementation realizes it — decisions,
                                               contracts, pipeline, toolchain)
specs/02-code-unit-cohesion.md = code-unit sizing & boundary contract
codebase (app/)           = executable materialization of the specs
tests                     = executable proof that the materialization matches the specs
```

Properties this hierarchy must hold:

- **Reimplementability.** If a decision lives only in code, the spec is incomplete — record it.
- **Functional changes start in `specs/00-product.md`.** Implementation-only changes start in `specs/01-architecture.md`.
- **Drift is visible.** When implementation intentionally lags or diverges from spec, record it as a `TODO NGH:` note in the relevant spec — never hide drift in comments or test names.

## Principles

- **Separation of concerns** — dev affordances do not leak into release paths.
- **Single source of truth** — each behavior is controlled from one predictable place.
- **Determinism** — behavior follows from persisted state + config, not hidden toggles.
- **Traceability** — every taken code path is explainable.
- **Fail-fast** — invalid state is rejected early, not discovered at runtime.
- **Minimizing accidental complexity** — the battery and cohesion rules are both forms of this.

## Spec-first change workflow rule

Spec edits precede code edits within the same change.

```text
1. Spec edit        → contract written in the right home (00 functional / 01 technical)
2. Implementation   → spec is the brief; deviations bounce back to step 1
3. Tests            → behavior-named, prove the spec
4. Reconcile        → spec adjusted only for genuine ambiguity — never to match accidental code
```

If you cannot write the spec section, you cannot write the code. Trivial changes (typo, mechanical rename, single-file edit with self-evident intent) are exempt.

## Drift rule

A `TODO NGH:` note must name: the canonical expectation, the current implementation, the impact/risk, and what closes the gap. Searchable, in the relevant spec doc.

## Code-unit cohesion & clean boundaries rule

Code units are kept small by making them **cohesive and cleanly bounded**, not by capping size. One principle at four radii: *cohesion* (inside: one responsibility), *composed method / SLAP* (shape: a body narrates named steps at one abstraction level — `ReminderCoordinator.refresh` is the exemplar), *non-leaky abstraction* (boundary: depend on the interface, not the impl), *modular design* (between: loose coupling).

Three enforcement layers:

- **L1 — structural gates (hard).** Konsist architecture tests fence the layers (`domain` is a leaf; `ui` never reaches into `reminders`/`calendar` internals). A violation fails the test task.
- **L2 — smell sensors (soft, warn-only).** detekt complexity/size thresholds report, never block. Size is a signal, boundaries are the gate.
- **L3 — cohesion review (advisory).** LLM judges semantic cohesion on changed units: KEEP / PROMOTE / SPLIT / MERGE. Never a hard gate.

Full contract — thresholds, fence map, rubric — lives in [`specs/02-code-unit-cohesion.md`](specs/02-code-unit-cohesion.md).

## Battery rule

Battery is a first-class constraint, enforced at design time:

- **No polling loops, no idle foreground services.** WorkManager periodic sync (≥1h) + exact alarms only.
- **One alarm at a time.** Schedule only the *next* wake; reschedule after it fires. Never one alarm per event.
- **The system syncs the calendar, not us.** Read the Wear OS calendar mirror (`WearableCalendarContract`); never run our own network sync.

Any change that adds a wakeup source, shortens an interval, or adds a service must record the justification in `specs/01-architecture.md`.

## Minimal valid entrypoints rule

Gradle tasks are the entrypoints: `assembleDebug`, `test`, `detekt`, `installDebug`. No ad-hoc invocation paths. New entrypoints are registered in `specs/01-architecture.md` § Entrypoints in the same change.

## Testing rule

Tests read like executable documentation. Behavior-story names:

```text
snoozed reminder keeps coming back until marked done
done event never notifies again even after resync
planner schedules exactly one next wake at the earliest pending trigger
```

Avoid: `creates object`, `returns list`, `works`. Pure logic (`ReminderPlanner`) is tested without Android; Android glue stays thin.

## Repository workflow

All meaningful changes update `CHANGELOG.md` in the same commit.

Commit message format:

```text
yymmdd-hhmm: Your commit message
```
