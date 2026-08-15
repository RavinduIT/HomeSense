# Development conventions

Standing rules for this project. `docs/SCHEMA.md` is the authoritative data
contract; read it before changing any model, and amend it first if the contract
itself needs to change.

## Architecture

- Kotlin with Jetpack Compose and Material 3. No XML view layouts.
- MVVM throughout: composable, ViewModel exposing `StateFlow`, repository, data
  source. A composable must not access Firebase, Room or a DAO directly.
- Firebase Realtime Database rather than Firestore, for per-child listeners and
  `onDisconnect()`. See `docs/adr/0001`.
- The safety cut-off runs in `worker/`, never in the application. Its purpose is
  to act when the phone is unavailable.
- Rules in `worker/src/rules/` are pure functions with an injected clock: no
  I/O, no direct clock access. This keeps them testable and allows them to
  transfer to a Cloud Function unchanged.

## State separation

Three fields, three writers:

| Field | Written by |
|---|---|
| `desiredState` | The application |
| `reportedState` | The simulator |
| `status` | The worker |

The application never writes `status`. The database security rules enforce
this. It is the property that makes the error and disconnected states
meaningful.

## Device and slot model

A device is one physical node with one grid cell, one connection and one
heartbeat. A device holds one or more slots, which are the controllable points.

| Kind | Slots |
|---|---|
| `OUTLET` | Exactly one |
| `MULTI_SWITCH` | Two to six, as a single device occupying one cell |
| `CAMERA` | None |

`max_on_duration` and `schedule` belong to a slot, never to a device kind. An
iron connected to an outlet and a scheduled lamp on a gang-box channel are both
ordinary cases.

A multi-switch unit must never be represented as several devices. The assignment
requires it to be mapped to a single entity.

## Other rules

- Usage events are append-only, written at the moment of transition by the
  component that caused it. Usage is never derived by comparing snapshots.
- Device positions are stored as integer grid cells, never pixels. All
  cell-to-pixel arithmetic lives in `GridMapper`.
- Status is rendered as colour together with an icon and a text label, never
  colour alone.
- No paid services. No credentials committed: `google-services.json`,
  service-account keys and keystores are excluded by `.gitignore`, with
  templates provided.
- Each gate must pass before moving on: `./gradlew assembleDebug`,
  `./gradlew test`, and `npm test` in `worker/`.

## Build environment

The project targets Android Gradle Plugin 8.13.2 with compileSdk 36 and
targetSdk 35. Current androidx releases require AGP 9 and a recent Android
Studio; the older toolchain is retained so the project opens on the machines
available to the team. See `docs/adr/0002`.

If Android Studio is not installed, a portable toolchain is sufficient:

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
```

## Conventions

- Conventional commit messages (`feat:`, `fix:`, `docs:`, `test:`, `chore:`).
- Significant decisions are recorded as short entries in `docs/adr/`.
- `docs/TRACEABILITY.md` is updated as features land. It serves both as the
  record for assessment and as the checklist for the demonstration recording.
