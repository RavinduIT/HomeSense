# Migrating the worker to Cloud Functions

The safety worker runs as a plain Node process (`/worker`) because Cloud
Functions require the Blaze plan and a card on file, and the project constraint
is no paid services — see `docs/adr/0003`.

This document exists to show that the choice was an *engineering* decision
about billing, not an inability to write the Cloud Function version. The rules
were written so that the migration is a change of entry point and nothing else.

## What moves, and what does not

| Component | Migrates? |
|---|---|
| `worker/src/rules/**` | **Unchanged.** Pure functions, injected clock, no I/O. |
| `worker/src/executor.ts` | **Unchanged.** Same Admin SDK calls. |
| `worker/src/index.ts` | Replaced — the trigger, and only the trigger, differs. |
| `worker/src/firebase.ts` | Deleted. Cloud Functions initialise the SDK for you. |
| `worker/src/rules/rules.test.ts` | **Unchanged.** The tests never knew where they ran. |

That table is the argument. Every line that decides *what is safe* is portable;
only the lines that decide *when to look* are environment-specific.

## The replacement entry point

```ts
// functions/src/index.ts
import { onValueWritten } from 'firebase-functions/v2/database';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { getDatabase, ServerValue } from 'firebase-admin/database';
import { initializeApp } from 'firebase-admin/app';

import { ActionExecutor } from './executor';
import { Device, evaluateDevice } from './rules';

initializeApp();

const HOME_ID = 'home-1';
const TZ_OFFSET = 330; // Sri Lanka, UTC+5:30

async function evaluate(deviceId: string, device: Device | null) {
  if (!device) return;
  const db = getDatabase();
  const actions = evaluateDevice(deviceId, device, { now: Date.now }, TZ_OFFSET);
  if (actions.length > 0) {
    await new ActionExecutor(db, HOME_ID, sendSafetyAlert).apply(actions);
  }
}

// Trigger 1 — replaces the child listeners in worker/src/index.ts.
export const onDeviceWritten = onValueWritten(
  `/homes/${HOME_ID}/devices/{deviceId}`,
  async (event) => {
    await evaluate(event.params.deviceId, event.data.after.val() as Device | null);
  },
);

// Trigger 2 — replaces the 30-second sweep. Cloud Scheduler's floor is one
// minute, so a max_on_duration under 60s relies on the write trigger alone;
// see "The one real difference" below.
export const sweep = onSchedule('every 1 minutes', async () => {
  const snapshot = await getDatabase().ref(`homes/${HOME_ID}/devices`).get();
  const devices = (snapshot.val() ?? {}) as Record<string, Device>;
  await Promise.all(Object.entries(devices).map(([id, d]) => evaluate(id, d)));
});
```

## Steps

1. Upgrade the project to **Blaze**. The free tier still covers this workload;
   Blaze exists to attach a payment method, not because the usage costs money.
2. `firebase init functions` → TypeScript.
3. Copy `worker/src/rules/` and `worker/src/executor.ts` into `functions/src/`
   **unmodified**, and copy the test file with them.
4. Add the entry point above as `functions/src/index.ts`.
5. `firebase deploy --only functions`.
6. Stop the standalone worker. Delete the `meta/workerLastSeen` heartbeat, or
   have the scheduled function write it so the simulator's status pill keeps
   working.

## The one real difference

The standalone worker sweeps every **30 seconds**; Cloud Scheduler's minimum
interval is **60 seconds**. For a `max_on_duration` of 30 seconds the sweep is
therefore no longer the tighter of the two safety nets.

In practice the write trigger fires within milliseconds of any change, so the
cut-off still happens on time in the normal case. The sweep only matters for the
recovery case — an event that was missed while the function was being
redeployed. Closing that gap properly means either:

- accepting up to 60 seconds of latency on recovery, which for a 30-second iron
  limit is a real weakening and should be stated plainly; or
- using Cloud Tasks to enqueue a per-slot check at `onSince + max_on_duration`,
  which restores exact timing at the cost of one more service.

This is worth raising in the viva: it is a case where the free-tier
architecture is genuinely *better* on one axis than the paid one, and knowing
why is more interesting than having deployed either.
