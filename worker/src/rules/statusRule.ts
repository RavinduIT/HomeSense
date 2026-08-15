import { isSustainedMismatch, nextMismatchSince } from './mismatchRule';
import { isStale, linkStateOf } from './staleHeartbeatRule';
import { Action, Clock, Device, Slot, SlotStatus, slotsOf } from './types';

/**
 * Derives `status` — the only field the UI renders, and the only field a client
 * is forbidden from writing.
 *
 * The precedence is deliberate and is what makes the four states mean anything:
 *
 *  1. **DISCONNECTED** wins over everything. If the node has stopped sending
 *     heartbeats we do not know what the relay is doing, and saying "OFF"
 *     would be a guess presented as a fact.
 *  2. **ERROR** next: the hub asked for one thing and the hardware reports
 *     another, for longer than the tolerance window. Brief disagreement is
 *     normal — that is just a command in flight — so the disagreement has to
 *     persist before it is called a fault.
 *  3. Otherwise the status **mirrors what the hardware reports**, never what
 *     the app asked for.
 *
 * This function also owns `onSince` (stamped on a genuine off→on transition,
 * cleared on off) and `mismatchSince` (when the disagreement began). Both live
 * in the database, so a worker restart cannot forget them.
 */
export function statusRule(deviceId: string, device: Device, clock: Clock): Action[] {
  const now = clock.now();
  const actions: Action[] = [];

  const stale = isStale(device, now);
  const link = linkStateOf(device, now);

  if (device.link !== link) {
    actions.push({ type: 'patchDevice', deviceId, patch: { link } });
  }

  for (const [slotId, slot] of slotsOf(device)) {
    const patch: Partial<Slot> = {};

    const desired = slot.desiredState === true;
    const reported = slot.reportedState === true;

    // --- track when the disagreement started -----------------------------
    const updatedMismatchSince = nextMismatchSince(slot, now, stale);
    if (updatedMismatchSince !== undefined) {
      patch.mismatchSince = updatedMismatchSince;
    }
    const effectiveSince =
      updatedMismatchSince !== undefined ? updatedMismatchSince : (slot.mismatchSince ?? null);

    const sustained =
      !stale && isSustainedMismatch({ ...slot, mismatchSince: effectiveSince }, now);

    // --- decide the status ------------------------------------------------
    const next: SlotStatus = stale
      ? 'DISCONNECTED'
      : sustained
        ? 'ERROR'
        : reported
          ? 'ON'
          : 'OFF';

    const previous = slot.status ?? 'OFF';
    if (previous !== next) {
      patch.status = next;
    }

    // --- own onSince ------------------------------------------------------
    // Stamped when the hardware genuinely starts running, which is what the
    // cut-off measures from. Not when the user asked for it.
    if (next === 'ON' && (!slot.onSince || slot.onSince <= 0)) {
      patch.onSince = now;
    } else if (next !== 'ON' && next !== 'ERROR' && slot.onSince) {
      // A DISCONNECTED slot keeps its onSince: if it comes back still running,
      // the cut-off must count from when it actually started, not from the
      // reconnection. Only a real off clears it.
      if (next === 'OFF') {
        patch.onSince = null;
      }
    }

    // --- log the transitions the worker is responsible for ---------------
    if (previous !== next) {
      if (next === 'ERROR') {
        actions.push({
          type: 'usage',
          deviceId,
          slotId,
          event: 'ERROR',
          source: 'WORKER',
        });
        actions.push({
          type: 'alert',
          deviceId,
          slotId,
          severity: slot.appliance?.hazardous ? 'CRITICAL' : 'WARNING',
          message:
            `${slot.label || slotId} is not responding: the hub asked for ` +
            `${desired ? 'ON' : 'OFF'} but it reports ${reported ? 'ON' : 'OFF'}.`,
        });
      }

      if (previous === 'DISCONNECTED' && next !== 'DISCONNECTED') {
        actions.push({
          type: 'usage',
          deviceId,
          slotId,
          event: 'RECONNECT',
          source: 'WORKER',
        });
      }

      // A slot that stopped running while nobody was watching still owes the
      // usage log an OFF, with the duration it actually ran for.
      if (previous === 'ON' && next === 'OFF' && slot.onSince) {
        actions.push({
          type: 'usage',
          deviceId,
          slotId,
          event: 'OFF',
          durationSec: Math.max(0, Math.floor((now - slot.onSince) / 1000)),
          source: 'WORKER',
        });
      }
    }

    if (Object.keys(patch).length > 0) {
      actions.push({ type: 'patchSlot', deviceId, slotId, patch });
    }
  }

  return actions;
}
