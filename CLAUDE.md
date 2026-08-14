# HomeSense — working rules

Smart Home Monitoring & Control system. Android app + cloud-connected hardware
simulation. University mini-project (SCS 3311).

`docs/SCHEMA.md` is the authoritative data contract. Read it before changing any
model, and change it *first* if the contract needs to move.

## Architecture — non-negotiable

- **Kotlin + Jetpack Compose (Material 3).** No XML view layouts.
- **MVVM**: `Composable → ViewModel (StateFlow) → Repository → data source`.
  A composable never touches Firebase, Room or a DAO directly. Ever.
- **Firebase Realtime Database**, not Firestore. Per-child listeners, sub-second
  fan-out, and `onDisconnect()` — see `docs/adr/0001`.
- **The safety cutoff runs server-side**, in `/worker`. Never in the app. The
  whole point is that it protects life and property when the phone is off.
- Rules in `worker/src/rules/` stay **pure functions** — no I/O, no clock reads
  except through an injected clock — so they lift into a Cloud Function
  unchanged and can be tested with a fake clock.

## The invariant everything hangs off

Three fields, three writers:

- `desiredState` — the **app** writes this ("I want it on").
- `reportedState` — the **simulator** writes this ("the relay is on").
- `status` — the **worker** writes this, reconciling the two.

**The app never writes `status`.** The database security rules enforce it. That
is what makes `ERROR` and `DISCONNECTED` real observed states instead of
decorative badges.

## The slot model

A **device** is a physical node: one grid cell, one connection, one heartbeat.
A device has one or more **slots** — the actual controllable points.

- `OUTLET` → exactly 1 slot
- `MULTI_SWITCH` → 2–6 slots, **one device, one entity, one grid cell**
- `CAMERA` → 0 slots

`max_on_duration` and `schedule` belong to a **slot**, never to a device kind.
An iron on an outlet and a scheduled bulb on a gang-box channel are both normal.

Never split a multi-switch into several devices. The spec requires it be "mapped
to a single entity in the system" and it will be checked.

## Other standing rules

- Usage events are **append-only**, logged at the moment of transition by the
  actor that caused it. Never recompute usage by diffing state.
- Device positions are stored as integer **grid cells**, never pixels. All
  cell↔pixel maths lives in one pure, unit-tested `GridMapper`.
- Status is rendered as **colour + icon + text**, never colour alone.
- No paid services. No secrets committed — `google-services.json`,
  service-account JSON and keystores are gitignored, with `.template` versions.
- Every gate must actually pass before moving on: `./gradlew assembleDebug`,
  `./gradlew test`, `npm test` in `/worker`.

## Toolchain on this machine

Android Studio is **not** installed. A portable toolchain lives at
`C:\Users\ravin\android-toolchain`:

```powershell
$env:JAVA_HOME='C:\Users\ravin\android-toolchain\jdk'      # Temurin 17.0.20
$env:ANDROID_HOME='C:\Users\ravin\android-toolchain\sdk'   # platforms 35+36, build-tools 35/36
.\gradlew.bat assembleDebug
```

Versions are pinned to **AGP 8.13.2 / compileSdk 36** on purpose — newer
androidx requires compileSdk 37 and AGP 9.x, which needs a very recent Android
Studio the team may not have. See `docs/adr/0002`.

## Conventions

- Conventional commits (`feat:`, `fix:`, `docs:`, `test:`, `chore:`).
- At a fork in the road, write a two-line ADR into `docs/adr/` and move on.
- Keep `docs/TRACEABILITY.md` filled in as features land — it is both the
  marking defence and the demo run-sheet.
