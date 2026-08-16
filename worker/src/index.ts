import { DataSnapshot, Reference, ServerValue, getDatabase } from 'firebase-admin/database';
import { ActionExecutor } from './executor';
import { TIMEZONE_OFFSET_MINUTES, initialiseFirebase } from './firebase';
import { sendSafetyAlert } from './notifications';
import { Device, THRESHOLDS, evaluateDevice } from './rules';

/**
 * The safety worker.
 *
 * It exists because the cut-off has to protect life and property when the phone
 * is off, out of range, or in a pocket. Nothing in this process depends on any
 * application being open.
 *
 * ### Households are discovered, not configured
 * The worker watches `/homeIndex` and attaches listeners to every household it
 * finds. A household created from the application is picked up within
 * milliseconds and one that is removed is released, so no redeployment or
 * environment change is needed when a new home is set up.
 *
 * ### Two triggers, deliberately
 *
 *  1. Child listeners react within milliseconds of a change, which is what
 *     makes a fault or a reconnection appear on the phone almost at once.
 *  2. A thirty-second sweep re-evaluates everything regardless of events. This
 *     is what makes the cut-off safe: nothing is armed in memory, so a crash, a
 *     redeployment or a missed event cannot lose a pending cut-off. The next
 *     sweep recomputes elapsed time from `onSince` in the database and acts.
 *
 * Both call the same pure evaluation, so they cannot disagree.
 */

/** Listeners and bookkeeping for one household. */
interface WatchedHome {
  devicesRef: Reference;
  executor: ActionExecutor;
  handlers: Array<{ event: 'child_added' | 'child_changed'; fn: (s: DataSnapshot) => void }>;
}

async function main(): Promise<void> {
  const app = initialiseFirebase();
  const db = getDatabase(app);

  const watched = new Map<string, WatchedHome>();
  /** Guards against a sweep and a listener evaluating one device at once. */
  const inFlight = new Set<string>();

  console.log('[worker] watching /homeIndex for households');
  console.log(
    `[worker] stale heartbeat ${THRESHOLDS.staleHeartbeatMs}ms · ` +
      `mismatch ${THRESHOLDS.mismatchMs}ms · sweep ${THRESHOLDS.sweepMs}ms · ` +
      `tz offset ${TIMEZONE_OFFSET_MINUTES}min`,
  );

  async function evaluate(
    homeId: string,
    deviceId: string,
    device: Device | null,
  ): Promise<void> {
    if (!device) return;
    const key = `${homeId}/${deviceId}`;
    if (inFlight.has(key)) return;
    inFlight.add(key);
    try {
      const home = watched.get(homeId);
      if (!home) return;

      const actions = evaluateDevice(deviceId, device, { now: Date.now }, TIMEZONE_OFFSET_MINUTES);
      if (actions.length > 0) {
        await home.executor.apply(actions);
        const summary = actions.map((a) => (a.type === 'usage' ? a.event : a.type)).join(', ');
        console.log(`[worker] ${homeId}/${device.name ?? deviceId}: ${summary}`);
      }
    } catch (error) {
      // One bad device must not take the worker down for the rest.
      console.error(`[worker] evaluating ${homeId}/${deviceId} failed:`, error);
    } finally {
      inFlight.delete(key);
    }
  }

  function watchHome(homeId: string): void {
    if (watched.has(homeId)) return;

    const devicesRef = db.ref(`homes/${homeId}/devices`);
    const executor = new ActionExecutor(db, homeId, (message, deviceId) =>
      // Alerts are published per household, so one family's cut-off is never
      // delivered to another's phones.
      sendSafetyAlert(homeId, message, deviceId),
    );

    const onAdded = (snap: DataSnapshot) => void evaluate(homeId, snap.key!, snap.val());
    const onChanged = (snap: DataSnapshot) => void evaluate(homeId, snap.key!, snap.val());

    devicesRef.on('child_added', onAdded);
    devicesRef.on('child_changed', onChanged);

    watched.set(homeId, {
      devicesRef,
      executor,
      handlers: [
        { event: 'child_added', fn: onAdded },
        { event: 'child_changed', fn: onChanged },
      ],
    });

    console.log(`[worker] watching household ${homeId}`);
  }

  function releaseHome(homeId: string): void {
    const home = watched.get(homeId);
    if (!home) return;
    home.handlers.forEach(({ event, fn }) => home.devicesRef.off(event, fn));
    watched.delete(homeId);
    console.log(`[worker] released household ${homeId}`);
  }

  // ---- household discovery -------------------------------------------------
  const indexRef = db.ref('homeIndex');
  indexRef.on('child_added', (snap: DataSnapshot) => watchHome(snap.key!));
  indexRef.on('child_removed', (snap: DataSnapshot) => releaseHome(snap.key!));

  // ---- the sweep -----------------------------------------------------------
  async function sweep(): Promise<void> {
    for (const [homeId, home] of watched) {
      try {
        const snapshot = await home.devicesRef.get();
        const devices = (snapshot.val() ?? {}) as Record<string, Device>;
        for (const [deviceId, device] of Object.entries(devices)) {
          await evaluate(homeId, deviceId, device);
        }
      } catch (error) {
        console.error(`[worker] sweep of ${homeId} failed:`, error);
      }
    }
  }

  setInterval(() => void sweep(), THRESHOLDS.sweepMs);

  // ---- heartbeat -----------------------------------------------------------
  // Written per household so that the application and the simulator can show
  // whether the safety system is running. A monitoring system that cannot be
  // seen to be alive is worth very little.
  const beat = async () => {
    for (const homeId of watched.keys()) {
      await db
        .ref(`homes/${homeId}/meta/workerLastSeen`)
        .set(ServerValue.TIMESTAMP)
        .catch((error: unknown) => console.warn(`[worker] heartbeat ${homeId} failed:`, error));
    }
  };

  setInterval(() => void beat(), 10_000);

  const shutdown = async (signal: string) => {
    console.log(`[worker] ${signal} — shutting down`);
    indexRef.off();
    for (const homeId of Array.from(watched.keys())) {
      await db
        .ref(`homes/${homeId}/meta/workerLastSeen`)
        .set(0)
        .catch(() => undefined);
      releaseHome(homeId);
    }
    process.exit(0);
  };
  process.on('SIGINT', () => void shutdown('SIGINT'));
  process.on('SIGTERM', () => void shutdown('SIGTERM'));
}

main().catch((error) => {
  console.error('[worker] fatal:', error);
  process.exit(1);
});
