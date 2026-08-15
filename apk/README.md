# Application builds

Signed release builds of the submitted application.

| File | Backend | Requires configuration |
|---|---|---|
| `HomeSense-1.0.apk` | Firebase Realtime Database | Yes — see `docs/FIREBASE_SETUP.md` |
| `HomeSense-1.0-demo.apk` | In-memory implementation | No |

## Which to install

`HomeSense-1.0-demo.apk` runs without any configuration, network connection or
Firebase project. It performs the roles of simulator, worker and database
locally, including a working safety cut-off, and exposes the fault-injection
controls within the device sheet. It is the appropriate build for reviewing the
application on its own.

`HomeSense-1.0.apk` is the full system and communicates with a Firebase Realtime
Database. It requires the configuration described in `docs/FIREBASE_SETUP.md`,
and the safety worker and simulator must also be running for the system to
behave as documented.

The two have different application identifiers and can be installed alongside
each other. The demo build is labelled "HomeSense Demo".

## Installation

Transfer the file to an Android device running API 26 or later and open it.
Installation from outside the Play Store must be permitted for the application
handling the file, which Android will prompt for.

## Signature

Both builds are signed with the project release key:

```
CN=HomeSense, OU=SCS 3311, O=UCSC, L=Colombo, C=LK
SHA-256: c9ccf84713da65553550ddc9530dd948dcb6836aa915f6f0b11ce7142a594806
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
