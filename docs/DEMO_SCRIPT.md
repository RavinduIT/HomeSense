# Demo video run-sheet

**Target: 22 minutes.** The limit is 25, and every demo overruns — the three
minutes of slack are the plan, not an accident.

The specification requires **all three members present**, each introducing
themselves and stating their contribution. That is checked, so it opens each
block rather than being mentioned once at the start.

> Names marked *(member A/B/C)* — fill these in from `docs/CONTRIBUTIONS.md`
> before recording.

## Before you press record

- [ ] `/worker` running (`npm start`) — the terminal visible in a corner of the
      screen, because the audience needs to see the safety system is alive
- [ ] Simulator open in a browser, worker pill showing **online**
- [ ] `npm run seed` already run, so the reporting screen has a week of history
- [ ] Phone screen mirrored, notifications **on**, do-not-disturb **off**
- [ ] Iron's `max_on_duration` set to **30 s** — long enough to narrate, short
      enough not to stall the video
- [ ] A second browser tab with `database.rules.json` open, ready for §3
- [ ] Screen recorder capturing phone **and** desktop in one frame

---

## 0:00 — 1:30 · Opening (member A)

> "I'm *(name, index)*. I built the floor representation — the plans, the grid
> and device placement."

One sentence on the problem: a house has several floors, appliances of quite
different kinds, and some of them are dangerous if left on.

Then the sentence the whole system hangs on, said once, clearly:

> "Every switch in our system stores three separate things: what the app
> **wants**, what the hardware **reports**, and the status the server
> **derives** from those two. That is why our ERROR and DISCONNECTED states are
> real observations, not decorations. You'll see the difference in a minute."

## 1:30 — 5:30 · Floors and the grid (member A)

| Time | Action | Say |
|---|---|---|
| 1:30 | Floors list — two storeys, live device counts | Multiple floor plans, each with its own grid |
| 2:15 | Open the ground floor | The plan is drawn as geometry, not a photo — crisp at any density, exact aspect ratio |
| 2:45 | Point at the legend | Shape is the device kind; fill pattern is the status. Nothing is colour-alone — it survives a projector and colour-blindness |
| 3:15 | Tap an empty cell → place an outlet | Positions are **grid cells**, never pixels, so a plan looks identical on a phone and a tablet |
| 4:00 | Rotate the phone | Everything stays put — the cell↔pixel maths is one pure class with 16 unit tests |
| 4:30 | Add a floor, choose plan and grid size | Floors are added and managed at runtime |
| 5:00 | Tap the gang box | **One** device, one cell, one heartbeat — with three independently addressable switches. The spec asks for a single entity and the data model makes anything else unrepresentable |

**Hand over:** "…and how those switches actually reach the hardware is *(member B)*."

## 5:30 — 6:00 · Handover (member B)

> "I'm *(name, index)*. I built the device profiles, the control UI and the
> usage reporting."

## 6:00 — 10:00 · Device profiles (member B)

| Time | Action | Say |
|---|---|---|
| 6:00 | Outlet card — toggle it | Watch the simulator: the lamp lights within a second, no refresh anywhere |
| 6:45 | Gang box — toggle switch 2 only | Independently addressable. Switch 1 and 3 are untouched |
| 7:15 | Master toggle | Addresses each slot in turn — still one device |
| 7:45 | Open the schedule editor on the porch light | On at 18:30, off at 06:00 — a window that wraps midnight, which is the ordinary case |
| 8:30 | Open the safety editor on the iron | `max_on_duration` — the field name is the spec's own spelling. **Enforced on the server**, not here |
| 9:00 | Switch the iron on — the countdown ring appears | It drains as the allowance is used and turns amber under 20% |
| 9:30 | Open the camera, tap for full screen | Mock snapshot and mock stream, as the spec permits. The real URIs are shown underneath |

*(Switch the iron off before moving on — the cut-off has its own slot at 14:00.)*

**Hand over:** "…and the part that makes ERROR and DISCONNECTED *true* is *(member C)*."

## 10:00 — 10:30 · Handover (member C)

> "I'm *(name, index)*. I built the synchronisation layer, the hardware
> simulator and the safety worker."

## 10:30 — 14:00 · The simulator and the three chaos buttons (member C)

This is the evidence section. Keep phone and browser both in frame throughout.

| Time | Action | Say |
|---|---|---|
| 10:30 | Show the simulator | This is the *hardware*. It writes `reportedState` and a heartbeat — and nothing else. It never writes status |
| 11:00 | **Physical toggle** on the ceiling light | A change starting at the wall switch. Nobody touched the phone — and there it is. Logged as source SIMULATOR |
| 11:45 | **Simulate fault** on the fan, then toggle it in the app | The relay simply stops obeying. Nothing writes "error" |
| 12:15 | Wait ~10 s, watch the app | The app rolls its optimistic switch back at 4 s and warns; the worker publishes ERROR at 10 s. The error was **derived** by a third process from a disagreement between two others |
| 13:00 | **Unplug** the kitchen outlet | The heartbeat stops. Within 15 s the worker publishes DISCONNECTED — silence measured, not guessed |
| 13:30 | Close the simulator tab entirely | `onDisconnect()` fires: everything goes DISCONNECTED. The *server* recorded the absence |

*(Reopen the tab and clear the fault before the next section.)*

## 14:00 — 17:00 · The safety cut-off (member C) — **the centrepiece**

| Time | Action | Say |
|---|---|---|
| 14:00 | Set the iron to `max_on_duration = 30`, switch it on | The ring starts; the simulator's heat bar fills |
| 14:30 | **Force-stop the app** — show Settings doing it | The phone is now out of the picture entirely |
| 14:45 | Point at the worker terminal and the simulator | Only these are running |
| 15:00 | Wait. Narrate the timer draining | Nothing is armed in memory. The worker recomputes elapsed time from `onSince` in the database on every pass |
| 15:30 | **The cut-off fires** — lamp goes out, log line appears, notification arrives on the phone | The appliance was switched off with the app force-stopped |
| 16:00 | Reopen the app → alert centre | The CRITICAL alert, written by the server. A client can only mark it acknowledged |
| 16:30 | **Kill and restart the worker mid-countdown**, repeat briefly | A crash loses nothing — state is in the database, so the next pass cuts off immediately. There's a Jest test for exactly this |

## 17:00 — 19:00 · Enforcement and reporting (members C then B)

| Time | Action | Say |
|---|---|---|
| 17:00 | Show `database.rules.json` (member C) | A `.write` grant cascades in RTDB and can't be revoked deeper — but `.validate` can reject a client write, and the Admin SDK bypasses validation. So `status` carries a validator that only passes when unchanged |
| 17:45 | State the limitation plainly | App and simulator share anonymous auth, so the rules can't tell them apart. What no client can do is publish a status — that's the property the safety argument rests on |
| 18:00 | Usage screen (member B) | Every number comes from logged transitions, never from diffing state — so the cut-off that fired with the phone closed is counted like everything else |
| 18:30 | Leaderboard, cut-off count, kWh | Labelled an estimate, because it assumes rated wattage throughout |
| 18:50 | CSV export via the share sheet | |

## 19:00 — 20:30 · Architecture and honesty (all three, one each)

- **(A)** MVVM: Composable → ViewModel → Repository → data source. No composable
  touches Firebase.
- **(C)** Why Realtime Database, not Firestore: per-child listeners and
  `onDisconnect()`. Why a Node worker, not Cloud Functions: Blaze needs a card —
  and the rules are pure functions that would lift into a Function unchanged.
- **(B)** What we did *not* do: single home, mocked cameras, estimated energy,
  and the worker must be running — which is why the simulator shows its
  heartbeat and says so when it isn't.

## 20:30 — 21:30 · Verification (member B)

Run them live, on camera:

```
./gradlew test          # 73 tests
cd worker && npm test   # 40 tests
```

Call out the two that matter: the cut-off fires at **exactly**
`max_on_duration`, and it still fires after a simulated worker restart.

## 21:30 — 22:00 · Close (member A)

One sentence each on what you'd do next: custom claims to separate app from
hardware, multi-home, real camera streams.

---

## The three shots that must be in the video

If time runs short, protect these — everything else is narration:

1. **14:00–16:00** — the iron cutting itself off with the app force-stopped.
2. **11:00** — the physical toggle appearing on the phone with nothing touched.
3. **12:15** — ERROR arising from a disagreement, derived by the worker.

## If something goes wrong on the day

| Problem | Do this |
|---|---|
| Campus Wi-Fi is down | Switch to the **demo** APK. It runs the same UI on an in-memory backend with a real cut-off, and the chaos controls are in the device sheet |
| The worker won't start | Check `worker/.env` and `serviceAccountKey.json`. The simulator's pill tells you outright |
| No notification arrives | Say so and carry on — the cut-off already happened, and the alert centre proves it. That is the point, not a save |
| Nothing appears in the app | Check `homeId` matches across app, worker `.env` and `simulator/firebase-config.js` |
