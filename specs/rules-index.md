# Rules Index

Single entry point to every rule that governs WatchCal. Each rule has exactly one canonical home — this index links to it, never restates it. When a rule and a principle disagree, the principle wins and the rule is rewritten (principles: `AGENTS.md` § Principles).

| Rule | Home | Principle | Enforcement | Linked gate |
|------|------|-----------|-------------|-------------|
| Spec-first change workflow | [AGENTS.md](../AGENTS.md) | Single source of truth, Traceability | prose | — |
| Drift (`TODO NGH:`) | [AGENTS.md](../AGENTS.md) | Traceability | prose | — |
| Code-unit cohesion & clean boundaries | [AGENTS.md](../AGENTS.md) | Minimizing accidental complexity | L1 `test` + L2 `lint` (warn) + L3 advisory | [02-code-unit-cohesion.md](./02-code-unit-cohesion.md), `ArchitectureFencesTest`, `config/detekt/detekt.yml` |
| Battery | [AGENTS.md](../AGENTS.md) | Minimizing accidental complexity, Determinism | prose | — `TODO NGH:` lint banning `startForegroundService`/raw `Timer` under `apps/*/src/main/**` |
| Minimal valid entrypoints | [AGENTS.md](../AGENTS.md) | Determinism, Traceability | prose | — |
| Testing | [AGENTS.md](../AGENTS.md) | Traceability | prose | — `TODO NGH:` lint on story-name pattern |
| Update checklist | [README.md](./README.md) | Single source of truth | prose | — |

## Canonical-statement rule

Every rule has exactly one home. Other files link to it, never restate the body. Restatement is drift.

## Gaps

- `TODO NGH:` every `prose` row is backlog — promote to lint/CI/test as gates get wired.
