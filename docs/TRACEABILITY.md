# Requirement traceability matrix

Each requirement from the assignment specification is mapped to the source file
implementing it, the test verifying it, and the point in the demonstration
recording at which it is shown.

Timings correspond to `docs/DEMO_SCRIPT.md` and should be confirmed against the
final recording.

Status: **A** — verified by automated test · **M** — verified by manual
demonstration

| # | Requirement | Implementation | Verification | Demo | Status |
|---|---|---|---|---|---|
| R1 | Add and manage multiple house-floor plans | `ui/floors/FloorsScreen.kt`, `FloorsViewModel.kt` | `DefaultHomeRepositoryTest` — floor creation and cascading delete | 4:30 | A |
| R2 | Abstract grid mapping over floor layouts | `ui/plan/GridMapper.kt`, `FloorPlanCanvas.kt` | `GridMapperTest` — 16 tests | 2:15 | A |
| R3 | Sample plans supplied for demonstration | `ui/plan/FloorPlanSpec.kt` (`PlanLibrary`) | Compose preview | 2:15 | A |
| R4 | Toggling reactively updates the interface | `ui/device/DeviceCard.kt`, `DeviceViewModel.kt` | `DefaultHomeRepositoryTest` — toggle propagates to flow | 6:00 | A |
| R5 | Devices display ON, OFF, ERROR, DISCONNECTED | `domain/model/Slot.kt`, `ui/device/StatusPill.kt` | `SlotTest`, worker `statusRule` tests, previews for all four states | 2:45, 12:15, 13:00 | A |
| R6 | Outlets as single-node binaries | `DeviceKind.OUTLET`, `DeviceCard.kt` | `WireTest`, Compose preview | 6:00 | A |
| R7 | Multi-switch: variable slot count, individually addressable, single entity | `domain/model/Device.kt`, `DeviceCard.MultiSwitchBody` | `SlotTest`, `WireTest`, `DefaultHomeRepositoryTest` — master toggle and independent slots | 5:00, 6:45 | A |
| R8 | Scheduling: fire-hazard appliances with `max_on_duration` | `domain/model/Slot.kt`, `worker/src/rules/maxOnDurationRule.ts` | `SlotTest`; worker tests for exact timing and restart recovery | 8:30, 14:00 | A |
| R9 | Scheduling: lights on preset on/off periods | `domain/model/Slot.kt`, `worker/src/rules/scheduleRule.ts` | `ScheduleTest`; worker tests for boundaries, midnight wrap, day selection, timezone | 7:45 | A |
| R10 | Security cameras with simulated snapshots and stream URIs | `ui/device/CameraCard.kt` | Compose previews, online and offline | 9:30 | A |
| R11 | Application changes reach the database promptly | `data/remote/RealtimeDatabaseSource.kt` | `DefaultHomeRepositoryTest` — writes `desiredState` only | 6:00 | A |
| R12 | External changes reach the client without manual refresh | `callbackFlow` child listeners; no refresh operation exists on `HomeRepository` | `DefaultHomeRepositoryTest` — external change reaches the flow with no repository call | 11:00 | A |
| R13 | Server-side cut-off sets state to off on breach | `worker/src/rules/maxOnDurationRule.ts` | Worker tests under a controlled clock; live run with the client force-stopped | 15:30 | A |
| R14 | Cut-off raises an alert | `worker/src/notifications.ts`, `ui/alerts/AlertsScreen.kt` | Live demonstration | 15:30, 16:00 | M |
| R15 | Usage reporting for significant devices | `data/local/Daos.kt`, `ui/report/ReportScreen.kt` | `UsageDaoTest`, `ReportRangeTest`, `UsageCsvTest` | 18:00 | A |
| R16 | Web-based simulator reflecting database updates | `simulator/index.html` | Live demonstration | 10:30 | M |
| R17 | Simulator listens directly to database updates | `simulator/index.html` — `onChildAdded`, `onChildChanged` | Live demonstration: client toggle illuminates the lamp | 6:00 | M |
| D1 | Source repository and shared APK link | GitHub release, linked from `README.md` | `assembleRelease`, signature confirmed with `apksigner` | — | A |
| D2 | Report covering synchronising mechanism, floor representation, simulator operations | `docs/REPORT.md`, structured under those headings | — | — | A |
| D3 | Demonstration under 25 minutes with all three members and their contributions | `docs/DEMO_SCRIPT.md` — three presenter blocks, 22-minute target | — | 0:00, 5:30, 10:00 | A |

## Additional work

The following was included beyond the stated requirements. Each is justified in
`docs/REPORT.md` or in the decision records.

| Item | Location | Rationale |
|---|---|---|
| Room offline cache and usage store | `data/local/` | Offline availability and SQL aggregation |
| `demo` product flavour | `data/remote/FakeRemoteSource.kt` | Allows the demonstration to be recorded without a network connection |
| Security rules enforcing the state separation | `database.rules.json` | Makes the write separation a constraint rather than a convention |
| Anonymous authentication | `AppContainer`, `HomeSenseApp` | Required for the security rules to apply |
| CSV export | `ui/report/UsageCsv.kt` | Applies the Intents material; escaping is separately tested |
| Continuous integration | `.github/workflows/build.yml` | Confirms that a checkout without credentials builds and tests successfully |
| Lifecycle documentation | `docs/LIFECYCLE_NOTES.md` | Behaviour under rotation, process death and force stop |

## Verification summary

| Suite | Tests | Command |
|---|---|---|
| Application unit tests | 73 | `./gradlew testDemoDebugUnitTest` |
| Worker rule tests | 40 | `cd worker && npm test` |
| Total | 113 | |

Requirements R14, R16 and R17 describe integration behaviour spanning a mobile
device, a browser and a server process. They are verified by live demonstration
rather than by automated test, which is the appropriate form of evidence for
them, and each is assigned a timestamp in the demonstration script.
