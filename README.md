# HomeSense

Smart Home Monitoring & Control — an Android app, a companion hardware
simulator and a server-side safety worker. Built for **SCS 3311**.

Multiple floor plans with an abstract grid overlay, heterogeneous device
profiles (outlets, multi-switch gang boxes, cameras), bidirectional cloud
synchronisation with no manual refresh, and an automatic safety cut-off that
switches a fire-hazard appliance off **even when the phone is force-stopped**.

**[⬇ Download the APK](../../releases/latest)** · [Technical report](docs/REPORT.md) ·
[Data contract](docs/SCHEMA.md)

---

## The idea in one paragraph

Every controllable point in the house is a **slot**. A wall outlet has one; a
gang box has two to six and is still a *single* entity on the floor plan; a
camera has none. Each slot carries three separate booleans — what the app
**wants** (`desiredState`), what the hardware **reports** (`reportedState`), and
the reconciled **`status`** the UI shows. The app may write only the first, the
simulator only the second, and the server-side worker only the third, and the
database security rules enforce that split. That is what makes `ERROR` and
`DISCONNECTED` observed facts rather than decorative badges, and it is why a
safety cut-off cannot be bypassed from a phone.

## Repository layout

| Path | What it is |
|---|---|
| `app/` | Android client — Kotlin, Jetpack Compose, MVVM |
| `worker/` | Safety worker — Node + TypeScript + `firebase-admin` |
| `simulator/` | Hardware simulator — one self-contained HTML page |
| `docs/` | Report, data contract, demo script, defence notes, ADRs |
| `database.rules.json` | The rules that make the invariant a constraint |

Start with **[`docs/SCHEMA.md`](docs/SCHEMA.md)** — the contract all three
runtimes share.

---

## Quick start — no accounts, no configuration

The `demo` flavour runs on an in-memory backend that plays simulator, worker and
cloud at once, including a **real** safety cut-off. No Firebase project, no
network.

```bash
git clone https://github.com/<your-org>/HomeSense.git
cd HomeSense
./gradlew assembleDemoDebug
```

The APK lands in `app/build/outputs/apk/demo/debug/`. Verified: a fresh clone
containing no secrets builds and passes its tests.

Requirements: **JDK 17** and the **Android SDK** (platform 36, build-tools
35.0.0+). Android Studio bundles both. Point Gradle at the SDK either by setting
`ANDROID_HOME` or by creating `local.properties` with `sdk.dir=/path/to/sdk`.

### Run the tests

```bash
./gradlew testDemoDebugUnitTest    # 73 app tests
cd worker && npm ci && npm test    # 40 worker tests, no credentials needed
```

---

## Full setup — the live system

Three processes talking to one Realtime Database.

### 1. Firebase

Follow **[`docs/FIREBASE_SETUP.md`](docs/FIREBASE_SETUP.md)**: create a project,
add a Realtime Database, enable Anonymous auth, and deploy
`database.rules.json`. Everything is on the free Spark plan; nothing needs a
card.

No secret is committed to this repository. Copy each template:

| Template | Copy to |
|---|---|
| `app/google-services.json.template` | `app/google-services.json` |
| `worker/.env.template` | `worker/.env` |
| `simulator/firebase-config.template.js` | `simulator/firebase-config.js` |
| `keystore.properties.template` | `keystore.properties` *(release builds only)* |

`homeId` must match across all three — the default is `home-1`.

### 2. The safety worker

```bash
cd worker
npm ci
# put the service-account key at worker/serviceAccountKey.json
npm run build && npm start
```

**Start this first.** Nothing derives `status`, enforces `max_on_duration` or
marks nodes offline without it — the simulator shows the worker's own heartbeat
and says outright when it is not running.

Optional, before recording a demo: `npm run seed` writes a week of plausible
usage history so the reporting screen has something honest to show.

### 3. The simulator

Open `simulator/index.html` in a browser — no build step, no server. Press
**Seed demo home** once to write the sample house into the database.

### 4. The app

```bash
./gradlew assembleLiveDebug
```

---

## Product flavours

| Flavour | Backend | Use it for |
|---|---|---|
| `demo` | in-memory fake | recording the video, offline development, CI |
| `live` | Firebase Realtime Database | the real system |

`demo` installs alongside `live` — different application ID, labelled "HomeSense
Demo" on the launcher — so the two are never confused on stage. It also exposes
the simulator's three chaos controls inside the device sheet, since it has no
browser simulator to press.

## Release build

```bash
cp keystore.properties.template keystore.properties   # then fill it in
./gradlew assembleRelease
```

Without `keystore.properties` the release build falls back to the debug signing
config, so `assembleRelease` still produces an installable APK rather than
failing.

---

## What to look at first

If you have ten minutes and want the interesting parts:

| File | Why |
|---|---|
| [`docs/SCHEMA.md`](docs/SCHEMA.md) | The three-field invariant and who may write what |
| [`worker/src/rules/maxOnDurationRule.ts`](worker/src/rules/maxOnDurationRule.ts) | The cut-off, and why its timer is not a timer |
| [`database.rules.json`](database.rules.json) | How a client is *made* unable to write `status` |
| [`app/.../ui/plan/GridMapper.kt`](app/src/main/java/lk/ac/ucsc/scs3311/smarthome/ui/plan/GridMapper.kt) | All cell↔pixel maths, pure and heavily tested |
| [`app/.../DefaultHomeRepositoryTest.kt`](app/src/test/java/lk/ac/ucsc/scs3311/smarthome/data/repository/DefaultHomeRepositoryTest.kt) | The "no manual refresh" proof |

## Documentation

| Document | Contents |
|---|---|
| [`docs/REPORT.md`](docs/REPORT.md) | Technical report — synchronising mechanism, floor representation, simulator operations |
| [`docs/SCHEMA.md`](docs/SCHEMA.md) | Database tree, the invariant, the who-writes-what table |
| [`docs/TRACEABILITY.md`](docs/TRACEABILITY.md) | Requirement → implementation → test → demo timestamp |
| [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md) | Timed 22-minute run-sheet, three presenter blocks |
| [`docs/DEFENCE_NOTES.md`](docs/DEFENCE_NOTES.md) | Five viva questions and answers per member |
| [`docs/LIFECYCLE_NOTES.md`](docs/LIFECYCLE_NOTES.md) | Rotation vs process death vs force stop |
| [`docs/FIREBASE_SETUP.md`](docs/FIREBASE_SETUP.md) | Click-by-click, from zero |
| [`docs/CLOUD_FUNCTIONS.md`](docs/CLOUD_FUNCTIONS.md) | Migrating the worker, if the project ever moves to Blaze |
| [`docs/CONTRIBUTIONS.md`](docs/CONTRIBUTIONS.md) | Who built what |
| [`docs/adr/`](docs/adr/) | Design decisions, two lines each |

## Floor plan images

None. The plans are declared as geometry — rooms, doors and windows in a
unit-less coordinate space — and drawn with Compose `Canvas`. They are our own
work, so there is no third-party image licence to attribute, and they stay crisp
at every screen density.

## Tests

| Suite | Count |
|---|---|
| App unit tests | 73 |
| Worker rule tests | 40 |
| **Total** | **113** |

CI (`.github/workflows/build.yml`) runs both on every push, from a clone with no
secrets at all.

## Team

See [`docs/CONTRIBUTIONS.md`](docs/CONTRIBUTIONS.md).

## Licence

Coursework. No licence granted for reuse.
