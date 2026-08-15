# What survives what

Examiners like asking this, and the honest answer is that most of it survives
because the state does not live in the app at all.

## Three kinds of "going away"

| Event | What happens | Example |
|---|---|---|
| **Configuration change** | Activity destroyed and recreated; the process lives. ViewModels survive. | Rotation, dark mode, font size |
| **Process death** | The whole process is killed while backgrounded. Everything in memory is gone. | The system reclaims memory |
| **Explicit exit** | The user swipes the app away or force-stops it. | Force stop from Settings |

## What we rely on for each

### Configuration change (rotation)

- **ViewModels survive**, so `PlanViewModel`, `DeviceViewModel` and the rest
  keep their state and their in-flight coroutines. The Firebase listeners
  behind the repository are not torn down and re-established, so the floor
  plan does not repaint from empty.
- **`rememberSaveable`** covers the transient UI state that is *not* worth a
  ViewModel: which dialog is open, half-typed text in the add-floor form, the
  chosen grid size. `remember` alone would lose these; `rememberSaveable`
  writes them to the saved-instance-state bundle.
- The `DefaultHomeRepository` shares its flows **eagerly**, not with
  `WhileSubscribed`, so a rotation cannot briefly drop every subscriber and
  trigger a listener teardown. That is a deliberate choice, documented in the
  repository itself.

### Process death

- **ViewModels do not survive.** They are rebuilt from the repository, which
  is rebuilt from Realtime Database — and RTDB **disk persistence is enabled**
  in `AppContainer`, so the last known tree is on the device and the first
  frame after a cold start shows real data rather than a spinner.
- **Room** holds the floor layout, the alert history and the usage log, so
  the reporting screen is fully populated offline, with no network at all.
- `rememberSaveable` state survives *if* the system saved the bundle. It is
  used only for things whose loss is a mild annoyance, never for anything the
  system's correctness depends on.

### Explicit exit — the interesting case

**Nothing about the safety guarantee depends on the app running.**

- `max_on_duration` is enforced by `/worker`, from `onSince` stored in the
  database. Force-stopping the app, switching the phone off, or walking out of
  Wi-Fi range changes nothing: the iron is still switched off on time.
- Schedules likewise fire from the worker's minute ticker.
- The usage log stays complete, because every actor logs its own transitions
  at the moment they happen. A cut-off that fired at 14:32 while the phone was
  in a drawer is in the log as a `CUTOFF` from `WORKER`, and appears in the
  report next time the app opens.

This is the point worth making out loud in the viva: an implementation that
armed a `setTimeout` in the app, or in the worker's memory, would fail exactly
here — and it would fail *silently*, which is worse.

## Where each piece of state actually lives

| State | Home | Survives rotation | Survives process death | Survives force stop |
|---|---|---|---|---|
| Device status, floors, slots | Realtime Database | ✅ | ✅ | ✅ |
| Cached copy for offline start | RTDB disk persistence | ✅ | ✅ | ✅ |
| Usage log, alerts, layout | Room | ✅ | ✅ | ✅ |
| Armed cut-off timers | RTDB (`onSince`) + worker | ✅ | ✅ | ✅ |
| Screen state (open dialog, form text) | `rememberSaveable` | ✅ | usually | ❌ |
| Selected report range | ViewModel | ✅ | ❌ | ❌ |
| Optimistic toggle in flight | ViewModel | ✅ | ❌ (reconciles from the DB) | ❌ |

The bottom two rows are the only things that can be lost, and losing either is
invisible to the user: the report reopens on "Today", and an in-flight toggle
resolves itself from `reportedState` the moment the screen comes back.

## Questions to expect

**"What happens if I rotate the phone while the iron is counting down?"**
Nothing. The ring is drawn from `onSince` in the database and a clock tick in
the ViewModel; the ViewModel survives rotation, and even if it did not, the
countdown would be recomputed identically from stored state.

**"What if I force-stop the app at 29 seconds of a 30-second limit?"**
The worker cuts the iron off at 30 seconds regardless. The alert is written to
`/alerts` and pushed by FCM. Reopening the app shows the cut-off in the alert
centre and the `CUTOFF` event in the report.

**"What if the *worker* restarts at 29 seconds?"**
Also nothing. No timer is armed in memory — the elapsed time is recomputed from
`onSince` on every pass, so the next evaluation after restart cuts it off
immediately. There are Jest tests for exactly this.
