# HomeSense

Smart Home Monitoring & Control — an Android app plus a cloud-connected
hardware simulation, built for **SCS 3311**.

Multiple floor plans with an abstract grid overlay, heterogeneous device
profiles (outlets, multi-switch gang boxes, cameras), bidirectional cloud
synchronisation with no manual refresh, and a **server-side safety cutoff** that
switches off a fire-hazard appliance even when the phone is closed.

> **Status: Phase 1 of 9 complete.** Build skeleton, data contract and domain
> models are in place and verified. See `docs/TRACEABILITY.md` for exactly what
> is implemented so far.

---

## Repository layout

| Path | What it is |
|---|---|
| `app/` | Android client — Kotlin, Jetpack Compose, MVVM |
| `worker/` | Server-side safety worker — Node + TypeScript + `firebase-admin` |
| `simulator/` | Companion hardware simulator — a single self-contained HTML page |
| `docs/` | Data contract, ADRs, report, demo script, traceability matrix |

Start with **`docs/SCHEMA.md`** — it is the contract all three runtimes share.

## Requirements

- JDK 17
- Android SDK with platform 36 and build-tools 35.0.0
- Node 20+ (for `worker/` and to serve `simulator/`)

Android Studio bundles the first two. Without it, the SDK can be installed
headlessly with the `cmdline-tools` package; `CLAUDE.md` records the paths used
on the original development machine.

## Build

```bash
git clone <this repo>
cd HomeSense

# demo flavour — no Firebase project, no network, nothing to configure
./gradlew assembleDemoDebug

# tests
./gradlew test
```

The APK lands in `app/build/outputs/apk/demo/debug/`.

### Product flavours

| Flavour | Backend | Use it for |
|---|---|---|
| `demo` | in-memory fake repository | recording the video, offline development |
| `live` | Firebase Realtime Database | the real system |

`demo` installs alongside `live` (different application ID) and is labelled
"HomeSense Demo" on the launcher, so the two are never confused on stage.

### Building the `live` flavour

Follow **`docs/FIREBASE_SETUP.md`**. In short: create a Firebase project, add a
Realtime Database, drop `google-services.json` into `app/`, enable Anonymous
Auth, and deploy `database.rules.json`.

No secret is committed to this repository. Templates are provided:

| Template | Copy to |
|---|---|
| `app/google-services.json.template` | `app/google-services.json` |
| `worker/.env.template` | `worker/.env` |
| `simulator/firebase-config.template.js` | `simulator/firebase-config.js` |

## The idea in one paragraph

Every controllable point in the house is a **slot**. A wall outlet has one; a
gang box has two to six and is still a *single* entity on the floor plan; a
camera has none. Each slot carries three separate booleans — what the app
*wants* (`desiredState`), what the hardware *reports* (`reportedState`), and the
reconciled `status` that the UI shows. The app may only write the first, the
simulator only the second, and the server-side worker only the third, and the
database security rules enforce that split. That is what makes `ERROR` and
`DISCONNECTED` observed facts rather than decorative badges, and it is why a
safety cutoff cannot be bypassed from a phone.

## Floor plan images

Sample plans in `app/src/main/assets/plans/` are freely licensed; each is
credited in `app/src/main/assets/plans/ATTRIBUTION.md`.

## Documentation

| Document | Contents |
|---|---|
| `docs/SCHEMA.md` | Database tree, the three-field invariant, who writes what |
| `docs/REPORT.md` | Technical report — synchronising mechanism, floor representation, simulator operations |
| `docs/TRACEABILITY.md` | Requirement → implementation → demo timestamp |
| `docs/DEMO_SCRIPT.md` | Timed run-sheet for the video |
| `docs/DEFENCE_NOTES.md` | Per-member viva preparation |
| `docs/CONTRIBUTIONS.md` | Who built what |
| `docs/adr/` | Design decisions, two lines each |

## Team

To be filled in — see `docs/CONTRIBUTIONS.md`.

## Licence

Coursework. No licence granted for reuse.
