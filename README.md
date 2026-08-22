# HomeSense

Smart Home Monitoring and Control System — an Android client, a companion
hardware simulator and a server-side safety worker, communicating through a
Firebase Realtime Database.

Developed for **SCS 3311**.

| Member | Index number | Module |
|---|---|---|
| R.L. Weerasinghe | 23002204 | Synchronisation, simulator and safety worker |
| W.T. Mahagamage | 23001038 | Device profiles, control interface and reporting |
| D.M. Isakya | 23000643 | Floor representation and grid mapping |

**[Download the APK](apk/HomeSense-1.0.apk)** ·
[Technical report](HomeSense-Technical-Report.pdf)

The submitted build is committed to `apk/HomeSense-1.0.apk`, signed with the
project release key; see `apk/README.md`.

---

## Overview

The system supports multiple floor plans with an abstract grid overlay, devices
of differing capabilities (power outlets, multi-switch gang boxes and security
cameras), bidirectional cloud synchronisation without manual refresh, and an
automatic safety cut-off that switches off a fire-hazard appliance even when the
mobile client is not running.

Every controllable point in the house is modelled as a *slot*. An outlet has
one; a gang box has between two and six while remaining a single entity on the
floor plan; a camera has none. Each slot stores three separate values: the state
requested by the application (`desiredState`), the state confirmed by the
hardware (`reportedState`), and the reconciled `status` presented in the
interface. The application may write only the first, the simulator only the
second, and the worker only the third, and the database security rules enforce
that separation.

## Repository structure

| Path | Contents |
|---|---|
| `app/` | Android client — Kotlin, Jetpack Compose, MVVM |
| `worker/` | Safety worker — Node, TypeScript, `firebase-admin` |
| `simulator/` | Hardware simulator — a single self-contained HTML page |
| `apk/` | Signed release builds |
| `HomeSense-Technical-Report.pdf` | The submitted technical report |
| `database.rules.json` | Database security rules |

`database.rules.json` is the appropriate starting point: it defines both the
shape of the data and who may write each part of it.

---

## Building without configuration

The `demo` flavour runs against an in-memory backend that performs the roles of
simulator, worker and database, including a working safety cut-off. It requires
no Firebase project and no network connection.

```bash
git clone https://github.com/<organisation>/HomeSense.git
cd HomeSense
./gradlew assembleDemoDebug
```

The APK is written to `app/build/outputs/apk/demo/debug/`.

Requirements are JDK 17 and the Android SDK with platform 36 and build-tools
35.0.0 or later. Android Studio provides both. Gradle locates the SDK either
through the `ANDROID_HOME` environment variable or through a `local.properties`
file containing `sdk.dir=/path/to/sdk`.

### Running the tests

```bash
./gradlew testDemoDebugUnitTest    # 77 application tests
cd worker && npm ci && npm test    # 40 worker tests, no credentials required
```

---

## Configuring the full system

The complete system consists of three processes sharing one Realtime Database.

### 1. Firebase project

`app/google-services.json` is committed, so the application builds and connects
without further configuration. It holds identifiers rather than credentials;
what protects the data is `database.rules.json`.

The Firebase configuration is committed, including the worker's environment
file and service-account key, so a checkout runs without a setup step. This is
coursework on the free plan, and access is governed by `database.rules.json`,
which confines a household to its members and prevents any client from writing
a device's `status`.

The signing keystore is the one thing not committed, since it is not
reproducible if lost. `keystore.properties.template` shows what it needs.

The simulator discovers households from the account it signs in with, so no
household identifier needs configuring. Create a household in the application
first; there is nothing to control until one exists.

### 2. Safety worker

```bash
cd worker
npm ci
# place the service-account key at worker/serviceAccountKey.json
npm run build && npm start
```

Start the worker before the other components. Without it, no status is derived,
`max_on_duration` is not enforced and devices are not marked offline. The
simulator displays the worker's heartbeat and reports when it is not running.

Optionally, run `npm run seed` before recording a demonstration to populate the
database with a week of usage history.

### 3. Simulator

The page uses ES modules, which browsers refuse to load over `file://`, so it
must be served over HTTP. Any static server will do:

```bash
cd simulator
python -m http.server 8080      # or: npx serve
```

Open `http://127.0.0.1:8080` and sign in with the same account as the
application. The simulator discovers the households that account belongs to; a
single household is selected automatically. Use *Seed demo home* once to create
the sample house.

There is no build step.

### 4. Application

```bash
./gradlew assembleLiveDebug
```

On first run, register an account or continue as a guest, then create a
household. A guest session can be converted to a permanent account later from
the Account tab without losing anything.

---

## Product flavours

| Flavour | Backend | Purpose |
|---|---|---|
| `demo` | In-memory implementation | Recording the demonstration, offline development, continuous integration |
| `live` | Firebase Realtime Database | Normal operation |

The two flavours have different application identifiers and can be installed
alongside each other; the demo build is labelled "HomeSense Demo". The demo
build also exposes the simulator's fault-injection controls within the device
sheet, since no browser simulator is present.

## Release builds

The submitted builds are committed to `apk/` and described in `apk/README.md`.
Both are signed with the project release key.

To rebuild them:

```bash
cp keystore.properties.template keystore.properties   # then complete it
./gradlew assembleRelease
```

If `keystore.properties` is absent the release build falls back to the debug
signing configuration, so `assembleRelease` still produces an installable APK.

---

## Documentation

The technical report is `HomeSense-Technical-Report.pdf` in the repository root.
It covers the three areas the assignment specifies for assessment: the
synchronising mechanism, the floor representation and the simulator operations,
with the architecture, the verification results, the requirement traceability
matrix and the limitations as appendices.

Each component carries its own notes: `worker/README.md`, `simulator/README.md`
and `apk/README.md`.


## Floor plan images

None are used. Plans are declared as geometry — rooms, doorways and windows in
a unit-less coordinate space — and rendered with the Compose `Canvas` API. They
are original work, so no third-party image licence applies, and they remain
sharp at any screen density.

## Testing

| Suite | Tests |
|---|---|
| Application unit tests | 77 |
| Worker rule tests | 40 |
| Total | 117 |

The continuous integration workflow in `.github/workflows/build.yml` runs both
suites on every push, from a checkout containing no credentials.

## Licence

Academic coursework. No licence is granted for reuse.
