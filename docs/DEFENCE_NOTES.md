# Defence notes

Evaluation weighs **individual defence**: each member must answer questions
about their own module orally. Rehearsing these is worth more than another
feature.

Five questions per member, with an answer each. Read your own section aloud
until you can give the answer without the page — an examiner can tell the
difference between someone who understands a design and someone reciting it.

Two questions any of you may be asked, so all three should know them:

> **"Why three fields instead of one boolean?"**
> One boolean cannot distinguish *asked for*, *actually happening*, and *what
> we believe*. `desiredState` is the app's intent, `reportedState` is the
> hardware's report, and `status` is the server's reconciliation. That is what
> makes ERROR and DISCONNECTED observations rather than decorations.

> **"Where does the safety cut-off run, and why there?"**
> In `/worker`, server-side. If it ran in the app it would only protect the
> house while someone had the app open — which is the opposite of what a safety
> feature is for.

---

## Member A — floor representation

*(Owns `ui/plan/`, `ui/floors/`, `GridMapper`, `FloorPlanSpec`.)*

**1. Why store grid cells rather than pixel coordinates?**

Pixels are meaningless across devices — the same offset is a different room on a
tablet than on a 5-inch phone, and rotating the screen would move every device.
A cell is a stable identity: cell (4,2) is the same place in the house whatever
is drawing it. The translation to pixels happens once, at draw time, in
`GridMapper`, from the current canvas size. It also means the database stores
something meaningful about the *house* rather than something about one phone.

**2. Why is `GridMapper` a separate class instead of maths inside the Composable?**

Because it is the part that can be wrong in a way you cannot see. Compose code
needs a device or a preview to exercise; a pure class with no Android dependency
runs in a JVM unit test in milliseconds. There are 16 tests: the cell↔pixel
round trip for every cell, taps on the letterbox bars, taps exactly on a cell
boundary and exactly on the plan edge, a zero-sized canvas, and the property
that the same cell resolves identically on phone and tablet geometry. Had that
maths been inline, none of it would be tested and a placement bug would first
appear during the demo.

**3. What is the letterboxing about, and why does it matter?**

The plan has a fixed aspect ratio; the available canvas usually does not. So the
plan is fitted inside the canvas preserving its ratio, leaving bars on two
sides. All cell arithmetic then happens inside that fitted rectangle, never
against raw canvas bounds. If you skipped that, the grid would stretch with the
window and every device would drift out of its room as the layout changed.
`cellAt()` also returns null for a tap on the bars — that is not "outside the
grid", it is "not on the plan at all".

**4. Why are the plans drawn as geometry rather than shipped as images?**

Three reasons, and the third is the one an examiner cares about. They stay crisp
at every screen density with no bitmap scaling; the aspect ratio is exact, so
the letterboxing maths is exact; and there is no third-party image licence to
track — the plans are our own work, so the attribution question does not arise.
`FloorPlanSpec` declares rooms, doors and windows in unit-less coordinates and
Compose `Canvas` draws them.

**5. How does the plan show a gang box with a fault on one channel?**

The marker shows the **most alarming** of its slot statuses, not an average and
not the first. A fault on one channel must not be hidden by two healthy ones —
so a three-gang box with one ERROR draws as an error marker. Shape carries the
device kind (circle, square with one pip per slot, camera outline) and fill
pattern carries status (solid, outline, cross, dashed). Nothing depends on
colour alone, which matters for accessibility and for a washed-out projector.

---

## Member B — device profiles and reporting

*(Owns `ui/device/`, `ui/report/`, `UsageCsv`, the DAO aggregates.)*

**1. Why is `max_on_duration` a property of a slot rather than a device type?**

Because an iron plugs into an ordinary socket, and a timed bulb hangs off
channel 2 of an ordinary gang box. If we had modelled `HAZARD` and `LIGHT` as
device kinds, neither would be expressible — you would need a separate device
type for every combination. A slot is a *controllable point*; the appliance is
assigned to it, and the appliance's properties (hazardous, watts) and its
policies (schedule, cut-off) belong at that point. The spec's own wording —
"specialised slots assigned to appliances" — pushed us there.

**2. Your toggles are optimistic. Isn't that dishonest, given only the server
knows the truth?**

It would be if we left it there. The switch flips immediately because waiting
for a round trip feels broken — but the app only writes `desiredState`; it
cannot move a relay. So the optimistic position is held for at most 4 seconds,
and if `reportedState` has not followed, the switch snaps back and the user is
told. The 4 seconds is chosen deliberately: longer than a normal round trip,
shorter than the worker's 10-second ERROR threshold, so the user is warned just
*before* the badge officially turns red.

**3. Why aggregate usage in SQL rather than over the Firebase snapshot?**

Two reasons. It works offline — Room is on the device, so the reporting screen
is fully populated with no network. And it scales: summing thousands of events
in Kotlin would mean holding the entire log in memory and recomputing on every
emission, where SQL does it in the database with an index. The query sums
`durationSec` from OFF and CUTOFF rows only — those are the events that *close*
an on-period — so no state reconstruction is needed at all.

**4. Why not recompute usage by diffing state snapshots?**

Because it would silently lose every transition that happened while the app was
closed — which is exactly the window the safety worker exists to cover. An iron
that switched on at 14:02 and was cut off at 14:32 while the phone was in a
drawer must appear in the report, and it does, because the *worker* logged those
events when they happened. Usage is append-only and each actor logs its own
transitions. Diffing would also double-count on reconnection; upserting on the
cloud push key means a replay is idempotent.

**5. Why is the kWh figure called an estimate?**

Because it is one. It multiplies the appliance's *rated* wattage by its on-time,
which assumes it draws full rated power the whole period. A kettle does not — it
draws hard while heating and little afterwards. Measuring properly needs a real
energy meter reporting actual consumption, which is outside a simulation.
Labelling it an estimate everywhere it appears is the honest thing to do, and it
is a one-line change to accept a measured value if a real meter were added.

---

## Member C — synchronisation, simulator and safety

*(Owns `data/remote/`, `data/repository/`, `simulator/`, `worker/`,
`database.rules.json`.)*

**1. Why Realtime Database rather than Firestore?**

Per-child listeners and `onDisconnect()`. Toggling one slot delivers one small
`onChildChanged` carrying that device, not a re-send of the whole `/devices`
subtree — which is what makes a grid of independent toggles feel instant.
`onDisconnect()` lets the *server* record a client's disappearance, so
DISCONNECTED is an observed fact rather than an inference by whoever noticed
first. Firestore's document-granularity listeners and higher write latency are
the wrong shape here. The cost is no real queries and a denormalised tree, which
is fine because the tree is small, fixed and documented.

**2. How is "no manual refresh" actually guaranteed, rather than just untested?**

There is no refresh method to call. `HomeRepository` has no `refresh()`,
`reload()` or `fetch()` — the device list is a `StateFlow` fed directly by child
listeners, so a change from the simulator, the worker or a second phone arrives
as a callback and re-renders. The repository tests demonstrate it by pushing a
change onto the source and asserting the flow carries it, without ever calling
into the repository. It is not a feature we added; it is the only behaviour this
design can produce.

**3. Your security rules — can a phone really not lie about a device's status?**

No, and the mechanism is worth explaining precisely. In Realtime Database a
`.write` grant cascades to every descendant and cannot be revoked further down,
so a deeper `.write: false` would do nothing. But `.validate` *can* reject a
client write, and the Admin SDK bypasses validation entirely. We use that
asymmetry: `status`, `link`, `onSince` and `mismatchSince` carry validators that
pass only when the value is unchanged. A client is therefore physically unable
to publish a status — it cannot claim a device is healthy, clear an ERROR, or
silence a DISCONNECTED, while the worker writes them freely.

The honest limitation: app and simulator both authenticate anonymously, so the
rules cannot tell them apart, and a modified client could write `reportedState`
as though it were hardware. Separating them needs custom claims and a backend to
mint them. What no client can do is write `status`, and that is what the safety
argument rests on.

**4. Why is the cut-off timer not a `setTimeout`?**

Because it would die with the process, and die silently. `onSince` lives in the
database and elapsed time is recomputed from it on every evaluation, so a worker
that crashes, redeploys, or loses power loses nothing — the next pass sees an
iron 47 seconds into a 30-second limit and cuts it off immediately. There is a
Jest test for that exact scenario, and another proving the cut-off fires at
precisely `max_on_duration`. Two triggers drive the same pure function: child
listeners for millisecond reaction, and a 30-second sweep for guaranteed
recovery — so they cannot disagree.

**5. Why does your schedule rule fire on boundaries instead of asserting the
window?**

Because asserting the window would mean fighting the user. If the rule enforced
"on between 18:00 and 23:00", someone who switched a scheduled light off at
19:00 would find it back on within a minute. Acting only at the on-minute and
the off-minute means a manual override holds until the next boundary, which is
what people expect from a timer. Firing is idempotent — at the boundary minute
it only writes when `desiredState` differs — so the twice-a-minute sweep cannot
double-fire. The cost is that a boundary missed entirely while the worker was
down is not retro-applied, which is a deliberate trade and is stated in the
report rather than hidden.

---

## Questions that will catch you out if you have not thought about them

**"What happens if two people toggle the same switch at once?"**
Last write wins on `desiredState`, and both phones converge on whatever the
hardware then reports. No lock is needed because the relay's report — not
either phone's intent — is the source of truth for `status`.

**"Why does the app still show DISCONNECTED for a device whose relay is on?"**
Because we do not know it is on. The heartbeat stopped, so the last
`reportedState` is stale by an unknown amount. Reporting ON would be presenting
a guess as a fact, and DISCONNECTED is the honest answer. `onSince` is
deliberately *kept* across a disconnection, so if the node comes back still
running, the cut-off counts from when it actually started — not from the
reconnection.

**"You use Room, but the spec did not ask for it. Why?"**
Offline resilience and usage aggregation, and we say so in the report rather
than pretending it was required. Live device *status* is deliberately not
cached — a stale ON badge from last week is worse than an honest DISCONNECTED.

**"What if the worker is not running?"**
Nothing derives `status`, no cut-off fires, and no schedule runs. That is a real
single point of failure and we do not hide it: the simulator shows the worker's
own heartbeat and states plainly when it is down. A monitoring system that
cannot be seen to be alive is worth very little.

**"Show me where the app writes `status`."**
It does not, anywhere. `RemoteHomeSource` — the entire surface of what the app
can do to the cloud — has no method that writes it. That absence is the design,
and the database rules enforce it independently of our good intentions.
