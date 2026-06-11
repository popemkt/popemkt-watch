# popemkt-watch

Personal Wear OS app monorepo, developed **spec-as-source** — see [`AGENTS.md`](./AGENTS.md) for the harness and [`specs/`](./specs/README.md) for the spec of record.

## Apps

| App | What |
|---|---|
| [`apps/watchcal`](./apps/watchcal) | Standalone Wear OS calendar reminder app: snooze-until-done notification loop on top of the OS calendar mirror. Functional spec: [`specs/00-product.md`](./specs/00-product.md) |

## Build

```sh
./scripts/setup-toolchain.sh        # once per machine: JDK 21 + Android SDK into .tooling/
JAVA_HOME=.tooling/jdk-21/Contents/Home ./gradlew test detekt assembleDebug
```

All valid entrypoints: [`specs/01-architecture.md`](./specs/01-architecture.md) § Entrypoints.
