# Application build

`HomeSense-1.0.apk` — the submitted release build, signed with the project
release key.

## Installing

Transfer the file to an Android device running API 26 (Android 8.0) or later
and open it. Installation from outside the Play Store must be permitted for the
application handling the file, which Android prompts for.

## First run

Register an account, or continue as a guest, then create a household. There is
nothing to display until a household exists, which is what the onboarding
screen is for.

The application is configured against the project's Firebase instance, so
signing in and managing floors and devices work immediately.

For devices to report their state, two companion processes must also be running:

| Process | Purpose |
|---|---|
| `worker/` | Derives the four device states, enforces `max_on_duration`, applies schedules |
| `simulator/` | Stands in for the physical appliances |

Without the worker, a device stays `DISCONNECTED` and commands are queued
rather than applied. That is correct behaviour rather than a fault: the
application records an intention, and only the hardware and the worker can
carry it out. See the repository README for how to start both.

## Signature

```
CN=HomeSense, OU=SCS 3311, O=UCSC, L=Colombo, C=LK
SHA-256: fc02f5361fcd0e08fd4e90cf00ba53d9b4f869194eb0e04731a16eecc163e0ae
```

Verify with:

```bash
apksigner verify --print-certs HomeSense-1.0.apk
```

## Rebuilding

```bash
cp keystore.properties.template keystore.properties   # then complete it
./gradlew assembleRelease
```

Without `keystore.properties` the release build falls back to the debug signing
configuration and still produces an installable APK.

A second product flavour, `demo`, runs against an in-memory backend with no
Firebase project and no network at all. It is not distributed here, but is
useful for development and as a fallback:

```bash
./gradlew assembleDemoDebug
```
