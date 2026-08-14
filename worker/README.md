# worker — server-side safety worker

Node + TypeScript + `firebase-admin`. Runs the rules that must keep working
when every phone is switched off.

**Built in Phase 6.** Planned contents:

| File | Rule |
|---|---|
| `src/rules/maxOnDurationRule.ts` | Cutoff when a slot exceeds `max_on_duration` |
| `src/rules/staleHeartbeatRule.ts` | `lastSeen` older than 15s → `DISCONNECTED` |
| `src/rules/mismatchRule.ts` | `desiredState != reportedState` for >10s → `ERROR` |
| `src/rules/scheduleRule.ts` | Minute ticker driving each slot's on/off window |

Rules are **pure functions** with an injected clock — no I/O, no `Date.now()`.
That keeps them testable with a fake clock and lets them lift into a Cloud
Function unchanged (`docs/CLOUD_FUNCTIONS.md`).

Armed-timer state lives in the **database**, not in process memory, and a 30s
sweep re-arms everything on start — so restarting the worker cannot lose a
pending cutoff.

See `docs/adr/0003-worker-process-over-cloud-functions.md` for why this is a
plain process rather than Cloud Functions.
