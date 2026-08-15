import { DataSnapshot, ServerValue, getDatabase } from 'firebase-admin/database';
import { ActionExecutor } from './executor';
import { HOME_ID, TIMEZONE_OFFSET_MINUTES, initialiseFirebase } from './firebase';
import { sendSafetyAlert } from './notifications';
import { Device, THRESHOLDS, evaluateDevice } from './rules';

/**
 * The safety worker.
 *
 * It exists because the specification's cut-off has to protect life and
 * property when the phone is off, out of range, or in a pocket. Nothing in
 * this process depends on any app being running.
 *
 * ### Two triggers, deliberately
 *
 *  1. **Child listeners** — react within milliseconds of a change. This is
 *     what makes a fault or a reconnection show up on the phone almost at once.
 *  2. **A 30-second sweep** — re-evaluate everything regardless of events.
 *     This is what makes the cut-off *safe*: nothing is armed in memory, so a
 *     crash, a redeploy or a missed event cannot lose a pending cut-off. The
 *     next sweep recomputes the elapsed time from `onSince` in the database
 *     and acts immediately.
 *
 * Both call exactly the same pure evaluation, so they can never disagree.
 */
async function main(): Promise<void> {
  const app = initialiseFirebase();
  const db = getDatabase(app);
  const home = db.ref(`homes/${HOME_ID}`);
  const devicesRef = home.child('devices');

  const executor = new ActionExecutor(db, HOME_ID, sendSafetyAlert);

  console.log(`[worker] watching homes/${HOME_ID}`);
  console.log(
    `[worker] stale heartbeat ${THRESHOLDS.staleHeartbeatMs}ms · ` +
      `mismatch ${THRESHOLDS.mismatchMs}ms · sweep ${THRESHOLDS.sweepMs}ms · ` +
      `tz offset ${TIMEZONE_OFFSET_MINUTES}min`,
  );

  /** Guards against a sweep and a listener evaluating the same device at once. */
  const inFlight = new Set<string>();

  async function evaluate(deviceId: string, device: Device | null): Promise<void> {
    if (!device) return;
    if (inFlight.has(deviceId)) return;
    inFlight.add(deviceId);
    try {
      const actions = evaluateDevice(deviceId, device, { now: Date.now }, TIMEZONE_OFFSET_MINUTES);
      if (actions.length > 0) {
        await executor.apply(actions);
        const summary = actions.map((a) => (a.type === 'usage' ? a.event : a.type)).join(', ');
        console.log(`[worker] ${device.name ?? deviceId}: ${summary}`);
      }
    } catch (error) {
      // One bad device must not take the safety worker down for the others.
      console.error(`[worker] evaluating ${deviceId} failed:`, error);
    } finally {
      inFlight.delete(deviceId);
    }
  }

  // ---- trigger 1: per-child listeners ------------------------------------
  devicesRef.on('child_added', (snap: DataSnapshot) => void evaluate(snap.key!, snap.val()));
  devicesRef.on('child_changed', (snap: DataSnapshot) => void evaluate(snap.key!, snap.val()));

  // ---- trigger 2: the sweep ----------------------------------------------
  async function sweep(): Promise<void> {
    try {
      const snapshot = await devicesRef.get();
      const devices = (snapshot.val() ?? {}) as Record<string, Device>;
      for (const [deviceId, device] of Object.entries(devices)) {
        await evaluate(deviceId, device);
      }
    } catch (error) {
      console.error('[worker] sweep failed:', error);
    }
  }

  setInterval(() => void sweep(), THRESHOLDS.sweepMs);
  await sweep();

  // ---- the worker's own heartbeat ----------------------------------------
  // Written so that the app and the simulator can show whether the safety
  // system is actually running. A monitoring system that cannot be seen to be
  // alive is worth very little.
  const beat = () =>
    home
      .child('meta/workerLastSeen')
      .set(ServerValue.TIMESTAMP)
      .catch((error: unknown) => console.warn('[worker] heartbeat failed:', error));

  setInterval(beat, 10_000);
  await beat();

  // Best-effort: say plainly that the safety system has stopped.
  const shutdown = async (signal: string) => {
    console.log(`[worker] ${signal} — shutting down`);
    devicesRef.off();
    await home
      .child('meta/workerLastSeen')
      .set(0)
      .catch(() => undefined);
    process.exit(0);
  };
  process.on('SIGINT', () => void shutdown('SIGINT'));
  process.on('SIGTERM', () => void shutdown('SIGTERM'));
}

main().catch((error) => {
  console.error('[worker] fatal:', error);
  process.exit(1);
});
