# Data contract

This file is the single source of truth shared by all three runtimes: the
Android app (`/app`), the safety worker (`/worker`) and the hardware simulator
(`/simulator`). If a field changes here, it changes in all three.

---

## 1. The modelling decision that everything else follows from

The specification talks about *slots* — "specialised slots assigned to
appliances prone to fire hazards". A slot is a **controllable point**; an
appliance is *assigned* to one.

So `max_on_duration` and `schedule` are capabilities of **a controllable
point**, not of a device type. An iron is plugged into an outlet; a scheduled
bulb hangs off channel 2 of a gang box. If we had modelled `HAZARD` and `LIGHT`
as device kinds, neither of those realistic cases could be expressed.

Therefore:

| Concept | Definition |
|---|---|
| **Device** | One physical node. One grid cell, one network connection, one heartbeat. |
| **Slot** | One controllable point on that node. Has its own state, schedule and safety limit. |

| Device kind | Slots | Notes |
|---|---|---|
| `OUTLET` | exactly 1 | A single wall socket. |
| `MULTI_SWITCH` | 2–6 | One gang box. **One `deviceId`, one grid cell, one entity** — slots toggle independently. |
| `CAMERA` | 0 | Nothing to toggle; carries snapshot/stream URIs instead. |

The multi-switch rule is stated explicitly by the specification ("mapped to a
single entity in the system") and is implemented as such: `Device` holds a
`List<Slot>`, and no code path ever splits a gang box into several devices.

---

## 2. Realtime Database tree

```
/homes/{homeId}
  meta:
    name          : String
    ownerUid      : String
    createdAt     : Long          # server timestamp

  floors/{floorId}:
    name          : String
    level         : Int           # 0 = ground, 1 = first, -1 = basement
    planAsset     : String        # e.g. "plans/ground_floor.webp"
    gridCols      : Int           # 2..16
    gridRows      : Int           # 2..16

  devices/{deviceId}:
    floorId       : String
    name          : String
    gridX         : Int           # 0 until gridCols
    gridY         : Int           # 0 until gridRows
    kind          : "OUTLET" | "MULTI_SWITCH" | "CAMERA"
    lastSeen      : Long          # server ts — SIMULATOR writes only
    link          : "ONLINE" | "DISCONNECTED"   # WORKER writes only
    camera:                       # present only when kind == CAMERA
      snapshotUrl : String
      streamUrl   : String
      lastFrameAt : Long

    slots/{slotId}:
      label          : String     # "Iron", "Ceiling light"
      appliance:
        name         : String     # free text
        hazardous    : Boolean
        watts        : Int?       # used for the kWh estimate
      desiredState   : Boolean    # APP + WORKER write only
      reportedState  : Boolean    # SIMULATOR writes only
      status         : "ON"|"OFF"|"ERROR"|"DISCONNECTED"   # WORKER writes only
      onSince        : Long?      # server ts of the last off→on transition
      safety:
        max_on_duration   : Long  # SECONDS. 0 = no limit. Name kept verbatim from the spec.
        autoCutoffEnabled : Boolean
      schedule:
        enabled           : Boolean
        onAtMinuteOfDay   : Int   # 0..1439
        offAtMinuteOfDay  : Int   # 0..1439
        days              : [Int] # 0=Sun .. 6=Sat; empty list = every day

  usage/{deviceId}/{slotId}/{eventId}:
    at            : Long
    event         : "ON"|"OFF"|"CUTOFF"|"ERROR"|"RECONNECT"
    durationSec   : Long?         # present on OFF and CUTOFF
    source        : "APP"|"SIMULATOR"|"WORKER"|"SCHEDULE"

  alerts/{alertId}:
    at            : Long
    deviceId      : String
    slotId        : String
    severity      : "INFO"|"WARNING"|"CRITICAL"
    message       : String
    acknowledged  : Boolean       # the only field a client may write
```

### Field naming

`max_on_duration` keeps the exact snake_case spelling used in the
specification. Everything else is camelCase. The inconsistency is deliberate:
it makes the mapping from requirement to implementation unarguable. The Kotlin
property is idiomatic (`Safety.maxOnDuration`) and the DTO layer does the
renaming at the boundary.

---

## 3. The three-field invariant

`desiredState`, `reportedState` and `status` are three different things written
by three different actors. This is the core idea of the whole system.

| Field | Meaning | Written by |
|---|---|---|
| `desiredState` | "The user wants this on." | **App**, and the worker for cutoffs/schedules |
| `reportedState` | "The relay is actually on." | **Simulator** only |
| `status` | The reconciled truth shown in the UI. | **Worker** only |

The worker computes `status` as:

| Condition | Resulting `status` |
|---|---|
| `now - lastSeen > 15s` | `DISCONNECTED` |
| `desiredState != reportedState` sustained > 10s | `ERROR` |
| otherwise | `ON` / `OFF`, mirroring `reportedState` |

Consequences worth stating out loud, because they are what an examiner probes:

- `ERROR` and `DISCONNECTED` are **observed facts**, not decorative badges. They
  can only arise from a real disagreement or a real silence.
- A phone **cannot** write `status`. The database security rules reject it, so
  the app is physically unable to claim a device is fine when it is not.
- Equally, a phone cannot fake a heartbeat, so it cannot mask a disconnection.

---

## 4. Usage events are append-only

A transition event is written **at the moment of the transition**, by whichever
actor caused it. Usage is never recomputed later by diffing snapshots.

Diffing would silently lose every transition that happened while the app was
closed — which is precisely the window the safety worker exists to cover. An
`ON` at 14:02 followed by a `CUTOFF` at 14:32 is a fact recorded by the worker;
a phone that was switched off all afternoon still shows it correctly.

---

## 5. Who writes what — summary

| Path | App | Simulator | Worker |
|---|:--:|:--:|:--:|
| `floors/*` | ✅ | — | — |
| `devices/*` (metadata, grid position) | ✅ | — | — |
| `devices/*/lastSeen` | — | ✅ | — |
| `devices/*/link` | — | — | ✅ |
| `slots/*/desiredState` | ✅ | — | ✅ |
| `slots/*/reportedState` | — | ✅ | — |
| `slots/*/status` | — | — | ✅ |
| `slots/*/onSince` | — | — | ✅ |
| `slots/*/safety`, `slots/*/schedule` | ✅ | — | — |
| `usage/*` | ✅ (append) | ✅ (append) | ✅ (append) |
| `alerts/*` | acknowledge only | — | ✅ |

`database.rules.json` enforces this table. It is not a convention — it is a
constraint, and that is the argument that safety cannot be bypassed from the
phone.
