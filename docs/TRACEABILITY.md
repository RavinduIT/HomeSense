# Requirement traceability matrix

Every requirement sentence from the specification → the file that implements it
→ the point in the demo video where it is shown.

Status: ☐ not started · ◐ in progress · ☑ done and verified

| # | Requirement (from the spec) | Implemented in | Verified by | Demo | Status |
|---|---|---|---|---|---|
| **R1** | Add and manage multiple house-floor plans | `ui/floors/*`, `domain/model/Floor.kt` | — | — | ☐ |
| **R2** | Abstract (simple) grid mapping overlaid on floor layouts | `ui/plan/GridOverlay.kt`, `ui/plan/GridMapper.kt` | `GridMapperTest` | — | ☐ |
| **R3** | Free sample plans bundled for the demo | `app/src/main/assets/plans/` | README attribution | — | ☐ |
| **R4** | Toggling reactively updates the UI | `ui/device/DeviceCard.kt`, `*ViewModel` | — | — | ☐ |
| **R5** | Devices display ON / OFF / ERROR / DISCONNECTED | `domain/model/Slot.kt` (`SlotStatus`), `ui/device/StatusPill.kt` | `SlotTest` | — | ◐ |
| **R6** | Outlets — single-node binaries | `DeviceKind.OUTLET`, `ui/device/OutletCard.kt` | `SlotTest` | — | ◐ |
| **R7** | Multi-switch: variable slot count, individually addressable, **one entity** | `domain/model/Device.kt`, `ui/device/MultiSwitchCard.kt` | `SlotTest.multi switch aggregates…` | — | ◐ |
| **R8** | Scheduling — fire-hazard appliances with `max_on_duration` | `domain/model/Slot.kt` (`Safety`), `worker/src/rules/maxOnDurationRule.ts` | `SlotTest`, worker Jest | — | ◐ |
| **R9** | Scheduling — bulbs on preset on/off periods | `domain/model/Slot.kt` (`Schedule`), `worker/src/rules/scheduleRule.ts` | `ScheduleTest` | — | ◐ |
| **R10** | Security cameras — mock snapshots / mock URI streams | `DeviceKind.CAMERA`, `ui/device/CameraCard.kt` | — | — | ☐ |
| **R11** | App changes reach the cloud DB quickly | `data/remote/RealtimeDatabaseSource.kt` | repository test | — | ☐ |
| **R12** | External changes reach the app with **no manual refresh** | `callbackFlow` child listeners | repository test | — | ☐ |
| **R13** | Server-side cutoff flips state to OFF on `max_on_duration` breach | `worker/src/rules/maxOnDurationRule.ts` | Jest fake clock + live run | — | ☐ |
| **R14** | Cutoff pushes an alert | `worker/src/alerts.ts`, `ui/alerts/*` | live run | — | ☐ |
| **R15** | Usage reporting of important devices, tracked from the app | `data/local/UsageDao.kt`, `ui/report/*` | DAO tests | — | ☐ |
| **R16** | Web-based simulator reflecting DB updates visually | `simulator/index.html` | live run | — | ☐ |
| **R17** | Simulator listens directly to database updates | `simulator/index.html` (`onValue`) | live run | — | ☐ |
| **D1** | Source on GitHub + shared APK link | GitHub Release | — | — | ☐ |
| **D2** | Concise report: synchronising mechanism, floor representation, simulator operations | `docs/REPORT.md` | — | — | ☐ |
| **D3** | Demo video ≤ 25 min, all three members present with contributions | `docs/DEMO_SCRIPT.md` | — | — | ☐ |

## Notes

- The **Demo** column is filled with `mm:ss` timestamps once the run-sheet in
  `docs/DEMO_SCRIPT.md` is timed. It doubles as the recording checklist: an
  empty cell at recording time means a requirement is about to go unshown.
- Rows marked ◐ have their model-level contract and tests in place from Phase 1;
  they complete when the corresponding UI or worker rule lands.
