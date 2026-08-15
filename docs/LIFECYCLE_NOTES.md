# Lifecycle behaviour

Android applications lose state under three different circumstances, and the
distinction matters when explaining what this system guarantees.

| Event | Effect | Example |
|---|---|---|
| Configuration change | The activity is destroyed and recreated; the process continues. ViewModels are retained | Rotation, dark mode, font size change |
| Process death | The process is terminated while in the background. All in-memory state is lost | The system reclaims memory |
| Explicit termination | The user dismisses the application or force-stops it | Force stop from system settings |

## Configuration change

ViewModels survive, so `PlanViewModel`, `DeviceViewModel` and the others retain
their state and any coroutines in progress. The Firebase listeners behind the
repository are not torn down and re-established, so the floor plan does not
briefly render empty.

`rememberSaveable` covers transient interface state that does not warrant a
ViewModel: which dialog is open, partially entered text in the add-floor form,
and the selected grid size. Plain `remember` would lose these values, whereas
`rememberSaveable` writes them to the saved instance state.

The repository shares its flows eagerly rather than using `WhileSubscribed`, so
a rotation cannot momentarily drop every subscriber and trigger listener
teardown. The reasoning is recorded in the repository source.

## Process death

ViewModels do not survive. They are reconstructed from the repository, which is
reconstructed from Realtime Database. Disk persistence is enabled in
`AppContainer`, so the most recently received tree is available locally and the
first frame after a cold start shows real data rather than a loading indicator.

Room holds the floor layout, the alert history and the usage log, so the
reporting screen is fully populated without a network connection.

State held in `rememberSaveable` survives if the system preserved the bundle. It
is used only for values whose loss is a minor inconvenience.

## Explicit termination

No part of the safety guarantee depends on the application running.

`max_on_duration` is enforced by the worker, using the `onSince` value stored in
the database. Force-stopping the application, switching the phone off, or moving
out of network range does not affect it: the appliance is still switched off at
the correct time.

Schedules are applied by the worker's minute evaluation in the same way.

The usage log remains complete, because each component records its own
transitions as they occur. A cut-off at 14:32 with the phone unavailable is
recorded as a `CUTOFF` event attributed to the worker, and appears in the report
when the application is next opened.

An implementation that armed a timer within the application, or within the
worker's memory, would fail in exactly this case, and would fail without any
indication that it had done so.

## Where state resides

| State | Location | Rotation | Process death | Force stop |
|---|---|:--:|:--:|:--:|
| Device status, floors, slots | Realtime Database | Retained | Retained | Retained |
| Offline copy for cold start | Realtime Database disk persistence | Retained | Retained | Retained |
| Usage log, alerts, layout | Room | Retained | Retained | Retained |
| Armed cut-off timers | Database `onSince` and worker | Retained | Retained | Retained |
| Interface state (open dialog, form text) | `rememberSaveable` | Retained | Usually retained | Lost |
| Selected report range | ViewModel | Retained | Lost | Lost |
| Optimistic toggle in progress | ViewModel | Retained | Reconciled from the database | Lost |

Only the final two rows can be lost, and neither loss is visible to the user:
the report reopens on the current day, and an outstanding toggle resolves from
`reportedState` when the screen is next displayed.

## Anticipated questions

**What happens if the phone is rotated while a cut-off is counting down?**

Nothing. The indicator is drawn from the `onSince` value in the database
together with a clock tick in the ViewModel. The ViewModel survives rotation,
and even if it did not, the countdown would be recomputed identically from
stored state.

**What happens if the application is force-stopped at 29 seconds of a 30-second
limit?**

The worker switches the appliance off at 30 seconds regardless. The alert is
written to the database and delivered by push notification. Reopening the
application shows the cut-off in the alert centre and the corresponding event in
the usage report.

**What happens if the worker restarts at 29 seconds?**

Also nothing. No timer is held in memory; elapsed time is recomputed from
`onSince` at every evaluation, so the first pass after the restart applies the
cut-off immediately. This case is covered by a unit test.
