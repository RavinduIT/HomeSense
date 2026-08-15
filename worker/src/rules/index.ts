import { maxOnDurationRule } from './maxOnDurationRule';
import { scheduleRule } from './scheduleRule';
import { statusRule } from './statusRule';
import { Action, Clock, Device } from './types';

export * from './types';
export * from './maxOnDurationRule';
export * from './mismatchRule';
export * from './scheduleRule';
export * from './staleHeartbeatRule';
export * from './statusRule';

/**
 * Every rule, applied to one device, in the order that matters.
 *
 * The ordering is not arbitrary:
 *
 *  1. **statusRule** first, because it stamps `onSince` when a slot genuinely
 *     starts running — and `onSince` is what the cut-off measures from.
 *  2. **maxOnDurationRule** next, so a breach is acted on in the same pass.
 *  3. **scheduleRule** last, so a scheduled switch-on cannot immediately undo
 *     a safety cut-off that fired in the same evaluation.
 *
 * The whole function is pure: it reads a device and returns the writes that
 * ought to happen. Nothing here touches the network, which is why it can be
 * tested against a fake clock — and why these files would drop into a Cloud
 * Function unchanged if the project ever moved to the Blaze plan.
 */
export function evaluateDevice(
  deviceId: string,
  device: Device,
  clock: Clock,
  timeZoneOffsetMinutes = 0,
): Action[] {
  return [
    ...statusRule(deviceId, device, clock),
    ...maxOnDurationRule(deviceId, device, clock),
    ...scheduleRule(deviceId, device, clock, timeZoneOffsetMinutes),
  ];
}

/**
 * Applies the slot patches an evaluation produced to an in-memory copy.
 *
 * Used by the tests to run several sweeps in a row without a database, and by
 * the worker to keep its local view current between snapshots.
 */
export function applyActions(device: Device, actions: Action[]): Device {
  const next: Device = { ...device, slots: { ...(device.slots ?? {}) } };

  for (const action of actions) {
    if (action.type === 'patchDevice') {
      Object.assign(next, action.patch);
    } else if (action.type === 'patchSlot') {
      const slot = next.slots?.[action.slotId];
      if (slot) {
        next.slots![action.slotId] = { ...slot, ...action.patch };
      }
    }
  }

  return next;
}
