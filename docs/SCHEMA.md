# Data contract

This document defines the data structures shared by the three runtimes: the
Android client (`app/`), the safety worker (`worker/`) and the hardware
simulator (`simulator/`). A change here requires a corresponding change in all
three.

---

## 1. Devices and slots

The assignment refers to *slots* — "specialised slots assigned to appliances
prone to fire hazards". A slot is a controllable point, and an appliance is
assigned to one.

It follows that `max_on_duration` and `schedule` are properties of a
controllable point rather than of a device type. An iron is connected to an
ordinary outlet, and a scheduled lamp may be wired to one channel of a gang box.
Had `HAZARD` and `LIGHT` been modelled as device kinds, neither arrangement
could be expressed.

| Concept | Definition |
|---|---|
| Device | One physical node: one grid cell, one network connection, one heartbeat |
| Slot | One controllable point on that node, with its own state, schedule and safety limit |

| Device kind | Slots | Notes |
|---|---|---|
| `OUTLET` | Exactly 1 | A single wall socket |
| `MULTI_SWITCH` | 2–6 | One gang box: a single device identifier and grid cell, with independently addressable slots |
| `CAMERA` | 0 | No controllable points; provides snapshot and stream URIs |

The requirement that a multi-switch unit be "mapped to a single entity in the
system" is stated explicitly in the assignment and is enforced by the model:
`Device` holds a list of `Slot` values, and no code path divides a gang box into
several devices.

---

## 2. Accounts and households

Identity and tenancy are separate concerns. An account proves who the caller
is; membership of a household decides what that account may see.

```
/users/{uid}
  profile:
    displayName   : String
    email         : String
    lastSeenAt    : Long
  homes/{homeId}:                 # the account's own index of its households
    homeName      : String
    role          : "OWNER" | "MEMBER"
    joinedAt      : Long

/invites/{code}:                  # bearer credential, 24-hour validity
    homeId        : String
    homeName      : String
    createdBy      : String
    createdAt     : Long
    expiresAt     : Long

/homeIndex/{homeId}: true         # lets the worker discover households
```

`/users/{uid}/homes` exists so that a client can discover its own households
without reading anything it does not own. `/homes/{homeId}/members` is the copy
the security rules consult. Both are written in the same multi-path update
whenever membership changes, so a half-joined state cannot exist.

Invite codes are stored under a top-level node keyed by the code. There is no
read rule on the collection itself, only on an individual code, so a code can be
redeemed when already known but the collection cannot be listed. Codes are drawn
from a cryptographic source, since a code admits its bearer to a household.

### Roles

| | Owner | Member |
|---|:--:|:--:|
| Operate devices, edit schedules | Yes | Yes |
| Add, move and remove devices | Yes | No |
| Configure `max_on_duration` and cut-offs | Yes | No |
| Invite and remove members | Yes | No |
| Delete the household | Yes | No |

Safety configuration is reserved to the owner because altering
`max_on_duration` changes the protection applied to everyone in the house.

## 3. Household structure

```
/homes/{homeId}
  meta:
    name          : String
    ownerUid      : String
    createdAt     : Long          # server timestamp
    workerLastSeen: Long          # worker heartbeat

  members/{uid}:
    displayName   : String
    email         : String
    role          : "OWNER" | "MEMBER"
    joinedAt      : Long
    viaCode       : String?       # the invite redeemed, checked by the rules

  floors/{floorId}:
    name          : String
    level         : Int           # 0 = ground, 1 = first, -1 = basement
    planAsset     : String        # identifier from PlanLibrary
    gridCols      : Int           # 2..16
    gridRows      : Int           # 2..16

  devices/{deviceId}:
    floorId       : String
    name          : String
    gridX         : Int           # 0 until gridCols
    gridY         : Int           # 0 until gridRows
    kind          : "OUTLET" | "MULTI_SWITCH" | "CAMERA"
    lastSeen      : Long          # server timestamp; written by the simulator
    link          : "ONLINE" | "DISCONNECTED"   # written by the worker
    camera:                       # present only when kind == CAMERA
      snapshotUrl : String
      streamUrl   : String
      lastFrameAt : Long

    slots/{slotId}:
      label          : String     # e.g. "Iron", "Ceiling light"
      appliance:
        name         : String
        hazardous    : Boolean
        watts        : Int?       # used for the energy estimate
      desiredState   : Boolean    # written by the application and the worker
      reportedState  : Boolean    # written by the simulator
      status         : "ON"|"OFF"|"ERROR"|"DISCONNECTED"   # written by the worker
      onSince        : Long?      # timestamp of the last off-to-on transition
      mismatchSince  : Long?      # timestamp at which a disagreement began
      safety:
        max_on_duration   : Long  # seconds; 0 means no limit
        autoCutoffEnabled : Boolean
      schedule:
        enabled           : Boolean
        onAtMinuteOfDay   : Int   # 0..1439
        offAtMinuteOfDay  : Int   # 0..1439
        days              : [Int] # 0 = Sunday .. 6 = Saturday; empty means daily

  usage/{deviceId}/{slotId}/{eventId}:
    at            : Long
    event         : "ON"|"OFF"|"CUTOFF"|"ERROR"|"RECONNECT"
    durationSec   : Long?         # present on OFF and CUTOFF events
    source        : "APP"|"SIMULATOR"|"WORKER"|"SCHEDULE"

  alerts/{alertId}:
    at            : Long
    deviceId      : String
    slotId        : String
    severity      : "INFO"|"WARNING"|"CRITICAL"
    message       : String
    acknowledged  : Boolean       # the only field a client may modify
```

### Field naming

`max_on_duration` retains the exact spelling used in the assignment
specification. All other fields use camel case. The Kotlin property is named
idiomatically (`Safety.maxOnDuration`) and the conversion occurs at the data
layer boundary.

---

## 3. State separation

The fields `desiredState`, `reportedState` and `status` represent three distinct
facts, written by three distinct components.

| Field | Meaning | Written by |
|---|---|---|
| `desiredState` | The state requested by the user | Application; also the worker, for cut-offs and schedules |
| `reportedState` | The state confirmed by the hardware | Simulator |
| `status` | The reconciled value shown in the interface | Worker |

The worker derives `status` as follows:

| Condition | Resulting status |
|---|---|
| `now - lastSeen > 15s` | `DISCONNECTED` |
| `desiredState != reportedState` sustained beyond 10s | `ERROR` |
| Otherwise | `ON` or `OFF`, following `reportedState` |

Three consequences follow:

- `ERROR` and `DISCONNECTED` arise only from an actual disagreement or an actual
  absence of reporting.
- A client cannot write `status`. The security rules reject such writes, so the
  application cannot report a device as functioning when it is not.
- A client cannot write a heartbeat, and so cannot conceal a disconnection.

---

## 4. Usage logging

Usage events are append-only. Each is written at the moment of transition by
whichever component caused it, and usage is never reconstructed by comparing
successive snapshots.

Reconstruction by comparison would omit every transition occurring while the
application was closed, which is the period the safety worker exists to cover.
An `ON` event at 14:02 followed by a `CUTOFF` at 14:32 is recorded by the
worker, and a client that was switched off throughout that afternoon still
reports both correctly.

---

## 5. Write responsibilities

Every entry below is additionally confined to members of the household in
question. An account that is not a member can neither read nor write any part of
it.

| Path | Application | Simulator | Worker |
|---|:--:|:--:|:--:|
| `floors/*` | Yes | — | — |
| `devices/*` metadata and position | Owner | — | — |
| `slots/*/safety` | Owner | — | — |
| `devices/*/lastSeen` | — | Yes | — |
| `devices/*/link` | — | — | Yes |
| `slots/*/desiredState` | Yes | — | Yes |
| `slots/*/reportedState` | — | Yes | — |
| `slots/*/status` | — | — | Yes |
| `slots/*/onSince`, `mismatchSince` | — | — | Yes |
| `slots/*/schedule`, `slots/*/label` | Yes | — | — |
| `usage/*` | Append | Append | Append |
| `alerts/*` | Acknowledge only | — | Yes |

`database.rules.json` enforces this table. The restriction is applied by the
database rather than observed by convention.

Two mechanisms are used, because they behave differently. A `.write` grant
cascades to every descendant and cannot be withdrawn lower down, so it is used
only where the whole subtree should be writable. Where a narrower restriction is
required — `status` and `link`, which no client may move, and `safety`, which
only an owner may change — a `.validate` rule is used instead, since validation
does constrain clients and the Admin SDK bypasses it entirely.
