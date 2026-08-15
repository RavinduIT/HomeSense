# Requirement traceability matrix

Every requirement from the specification → the file that implements it → where
it is verified → the point in the demo video where it is shown.

The **Demo** column doubles as the recording checklist: an empty cell at
recording time means a requirement is about to go unshown. Timings are from
`docs/DEMO_SCRIPT.md`; confirm them against the final cut.

Status: ☑ done and verified · ◐ implemented, verification is manual

| # | Requirement | Implemented in | Verified by | Demo | Status |
|---|---|---|---|---|---|
| **R1** | Add and manage multiple house-floor plans | `ui/floors/FloorsScreen.kt`, `FloorsViewModel.kt` | `DefaultHomeRepositoryTest` (floor CRUD, cascade delete) | 4:30 | ☑ |
| **R2** | Abstract (simple) grid mapping over floor layouts | `ui/plan/GridMapper.kt`, `FloorPlanCanvas.kt` | `GridMapperTest` — 16 tests | 2:15 | ☑ |
| **R3** | Free sample plans bundled | `ui/plan/FloorPlanSpec.kt` (`PlanLibrary`) | Compose `@Preview` | 2:15 | ☑ |
| **R4** | Toggling reactively updates the UI | `ui/device/DeviceCard.kt`, `DeviceViewModel.kt` | `DefaultHomeRepositoryTest` (toggle → flow) | 6:00 | ☑ |
| **R5** | Devices display ON / OFF / ERROR / DISCONNECTED | `domain/model/Slot.kt`, `ui/device/StatusPill.kt` | `SlotTest`, worker `statusRule` tests, `@Preview` all four | 2:45, 12:15, 13:00 | ☑ |
| **R6** | Outlets — single-node binaries | `DeviceKind.OUTLET`, `DeviceCard.kt` | `WireTest`, `@Preview` | 6:00 | ☑ |
| **R7** | Multi-switch: variable slots, individually addressable, **one entity** | `domain/model/Device.kt`, `DeviceCard.MultiSwitchBody` | `SlotTest`, `WireTest`, `DefaultHomeRepositoryTest` (master toggle, independent slots) | 5:00, 6:45 | ☑ |
| **R8** | Scheduling — fire-hazard appliances with `max_on_duration` | `domain/model/Slot.kt` (`Safety`), `worker/src/rules/maxOnDurationRule.ts` | `SlotTest`, worker Jest — fires at exactly the limit, survives restart | 8:30, 14:00 | ☑ |
| **R9** | Scheduling — bulbs on preset on/off periods | `domain/model/Slot.kt` (`Schedule`), `worker/src/rules/scheduleRule.ts` | `ScheduleTest`, worker Jest (boundaries, midnight wrap, days, timezone) | 7:45 | ☑ |
| **R10** | Security cameras — mock snapshots / mock URI streams | `ui/device/CameraCard.kt` | `@Preview` online + offline | 9:30 | ☑ |
| **R11** | App changes reach the cloud DB quickly | `data/remote/RealtimeDatabaseSource.kt` | `DefaultHomeRepositoryTest` (writes `desiredState` only) | 6:00 | ☑ |
| **R12** | External changes reach the app, **no manual refresh** | `callbackFlow` child listeners; no refresh method exists on `HomeRepository` | `DefaultHomeRepositoryTest` — external change → flow, repository never called | 11:00 | ☑ |
| **R13** | Server-side cutoff flips state to OFF on breach | `worker/src/rules/maxOnDurationRule.ts` | Jest fake clock + live run with the app force-stopped | 15:30 | ☑ |
| **R14** | Cutoff pushes an alert | `worker/src/notifications.ts`, `ui/alerts/AlertsScreen.kt` | Live run | 15:30, 16:00 | ◐ |
| **R15** | Usage reporting of important devices | `data/local/Daos.kt` (`UsageDao`), `ui/report/ReportScreen.kt` | `UsageDaoTest` (7), `ReportRangeTest`, `UsageCsvTest` | 18:00 | ☑ |
| **R16** | Web simulator reflecting DB updates visually | `simulator/index.html` | Live run | 10:30 | ◐ |
| **R17** | Simulator listens directly to database updates | `simulator/index.html` — `onChildAdded`/`onChildChanged` | Live run: app toggle → lamp lights | 6:00 | ◐ |
| **D1** | Source on GitHub + shared APK link | GitHub Release, linked from `README.md` | `assembleRelease` — signed, verified with `apksigner` | — | ☑ |
| **D2** | Concise report: sync mechanism, floor representation, simulator operations | `docs/REPORT.md` — those exact three headings | — | — | ☑ |
| **D3** | Demo ≤ 25 min, all three members with contributions | `docs/DEMO_SCRIPT.md` — three presenter blocks, 22 min target | — | 0:00, 5:30, 10:00 | ☑ |

## Beyond the specification

Included deliberately; each is defended in `docs/REPORT.md` or the ADRs.

| Item | Where | Why |
|---|---|---|
| Room offline cache + usage store | `data/local/` | Offline resilience and SQL aggregation, not required by the spec |
| `demo` product flavour | `data/remote/FakeRemoteSource.kt` | Records the video with no network — a real cut-off, not a mock-up |
| Security rules enforcing the invariant | `database.rules.json` | Makes the three-field split a constraint, not a convention |
| Anonymous auth | `AppContainer`, `HomeSenseApp` | Without `auth.uid` every rule rejects the write |
| CSV export | `ui/report/UsageCsv.kt` | Uses the Intents material; escaping is separately tested |
| CI | `.github/workflows/build.yml` | Proves a fresh clone with **no secrets** builds and tests green |
| Lifecycle notes | `docs/LIFECYCLE_NOTES.md` | Rotation vs process death vs force stop |

## Verification summary

| Suite | Count | Command |
|---|---|---|
| App unit tests | 73 | `./gradlew testDemoDebugUnitTest` |
| Worker rule tests | 40 | `cd worker && npm test` |
| **Total** | **113** | |

Three rows are ◐: R14, R16 and R17 are integration behaviours across a phone, a
browser and a server process. They are demonstrated live in the video rather
than asserted by an automated test — which is the appropriate evidence for
them, and is why the demo script pins each to a timestamp.
