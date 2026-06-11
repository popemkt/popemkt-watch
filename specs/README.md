# WatchCal Specs

WatchCal is developed **spec-as-source**. This folder is the spec of record. Together with `AGENTS.md` it must be **reimplementation-complete**: a competent agent should be able to discard `app/` and rebuild WatchCal from these documents alone.

## Documents

| File | Role |
|------|------|
| [00-product.md](./00-product.md) | **Functional spec** — what the app does, reminder semantics, agenda behavior, non-goals |
| [01-architecture.md](./01-architecture.md) | **Technical spec** — package layout, data flow, alarm pipeline, persistence, permissions, toolchain, entrypoints, decision records |
| [02-code-unit-cohesion.md](./02-code-unit-cohesion.md) | Code-unit sizing & boundary contract — L1/L2/L3 layers, thresholds, fence map, rubric |
| [rules-index.md](./rules-index.md) | Single entry point to every rule; one canonical home per rule |

## What belongs here

| Belongs | Does not belong |
|---|---|
| Reminder lifecycle semantics, contract shapes | Copies of code or API signatures (they rot) |
| Decision records — why, alternatives considered | Generated discovery output |
| Battery/wakeup budget decisions | Exploratory notes (use `_playground/` if needed) |
| `TODO NGH:` drift notes | |

## Update checklist

Before committing a change, answer:

```text
Does this alter user-visible reminder/agenda behavior?   → update 00-product.md first
Does this alter the pipeline, persistence, permissions,
wakeup sources, or toolchain?                            → update 01-architecture.md
Does this introduce or reverse a technical decision?     → add a decision record (alternatives included)
Does this create intentional drift from the spec?        → record a `TODO NGH:` note
Does this add/remove a Gradle entrypoint?                → update 01-architecture.md § Entrypoints
```

If none apply, the change probably needs no spec update. If a meaningful decision lives only in code, the spec is incomplete — record it.
