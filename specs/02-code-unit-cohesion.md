# 02 — Code-unit cohesion & clean boundaries

Technical contract for the *Code-unit cohesion & clean boundaries rule* (home: [`AGENTS.md`](../AGENTS.md)). The rule states *why*; this doc states *what* enforces it and *which* thresholds.

Principle: *Minimizing accidental complexity*, one principle at four radii — **cohesion** (inside: one responsibility), **composed method / SLAP** (shape: the body narrates named steps at one abstraction level — `ReminderCoordinator.refresh()` reads like the pipeline in `01-architecture.md`), **non-leaky abstraction** (boundary: depend on `CalendarSource`, not `WearCalendarSource`), **modular design** (between: loose coupling). Size is a *signal*; boundaries are the *gate*.

## The three layers

| Layer | Catches | Tool | Enforcement | Blocks? |
|---|---|---|---|---|
| **L1 — structural gates** | cross-layer coupling | Konsist (`ArchitectureFencesTest`) | test failure | **yes** |
| **L2 — smell sensors** | within-unit responsibility creep | detekt | report only (`maxIssues` unbounded) | no |
| **L3 — cohesion review** | semantic cohesion lint can't measure | LLM, on changed units | advisory | no |

## L1 — fence map (Konsist, runs in `./gradlew test`)

```text
domain     → (nothing)        pure Kotlin leaf; no Android imports either
calendar   → domain
reminders  → domain, calendar
ui         → domain, calendar, reminders
App.kt     → anything         (composition root, outside the layer set)
```

A unit reaching past another's surface (e.g. `ui` importing `WearCalendarSource` instead of `CalendarSource`) is the leak the gate makes unrepresentable. Lives in `apps/watchcal/src/test/java/.../ArchitectureFencesTest.kt`.

## L2 — thresholds (detekt, `config/detekt/detekt.yml`, warn-only)

Start loose (max-band — flag only genuine god-units), ratchet down once the codebase has history:

| Rule | Threshold | Ratchet target |
|---|---|---|
| `CyclomaticComplexMethod` | 15 | 10 |
| `NestedBlockDepth` | 4 | 3 |
| `LongParameterList` | 6 | 5 |
| `LongMethod` | 100 | 60 |
| `LargeClass` | 600 | 300 |

`complexity`/`depth` are the real shape sensors; line counts are weak canaries. A long-but-flat narrating orchestrator is good shape — never split it to satisfy a line count.

## L3 — rubric (advisory)

For each touched unit, recommend one of: **KEEP** (one coherent responsibility), **PROMOTE** (complete unit living inline → own file/layer), **SPLIT** (responsibilities tangled), **MERGE** (fragment existing only to satisfy a size limit → fold back). A step-comment (`// step 3: …`) is a step wanting a name. MERGE is the counterweight to over-extraction.

`TODO NGH:` canonical expectation: a `/cohesion-review` command grounding verdicts in the import graph; current implementation: rubric applied by agent discipline during review; impact: verdicts may miss real fan-in/out; closes: wire a review command once the codebase is big enough to need it.

## Decision records

- **Size as signal, boundary as gate.** Rejected hard size caps — lagging proxies that force bad splits.
- **Konsist over manual review for fences.** The fence is a test, so it blocks deterministically; LLM/L3 stays advisory (non-deterministic gates erode trust).
- **detekt never fails the build.** `maxIssues` unbounded; promotion of individual rules to blocking happens here, with a decision record, not silently in config.
