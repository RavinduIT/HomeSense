import { Action, Clock, Device, slotsOf } from './types';

/**
 * The safety cut-off. This is the rule the whole project exists for.
 *
 * A slot with an armed cut-off that has been continuously ON for longer than
 * `max_on_duration` is switched off, logged and alerted on.
 *
 * ### Why this cannot be a timer in memory
 * `onSince` lives in the database, so the elapsed time is recomputed from
 * stored state on every evaluation. Restarting the worker — a crash, a deploy,
 * a laptop lid closing — therefore loses nothing: the very next sweep sees an
 * iron that has been on for 47 seconds against a 30-second limit and cuts it
 * off immediately. A `setTimeout` armed at switch-on would have died with the
 * process, and nobody would ever have known.
 *
 * ### Why it writes desiredState rather than asserting status
 * The worker owns `status`, but `status` is *derived* from what the hardware
 * reports. Setting `desiredState=false` is the instruction that actually opens
 * the relay; the status follows a moment later when the node confirms. If the
 * node is faulty and never confirms, the mismatch rule raises an ERROR — which
 * is exactly right, because an iron that refuses to switch off is worse news
 * than one that does.
 */
export function maxOnDurationRule(
  deviceId: string,
  device: Device,
  clock: Clock,
): Action[] {
  const now = clock.now();
  const actions: Action[] = [];

  for (const [slotId, slot] of slotsOf(device)) {
    const limitSeconds = slot.safety?.max_on_duration ?? 0;
    const armed = slot.safety?.autoCutoffEnabled === true && limitSeconds > 0;
    if (!armed) continue;

    // Only a slot the hardware actually reports as on can be over its limit.
    if (slot.reportedState !== true) continue;
    if (!slot.onSince || slot.onSince <= 0) continue;

    const elapsedSeconds = Math.floor((now - slot.onSince) / 1000);
    if (elapsedSeconds < limitSeconds) continue;

    // Already commanded off and just waiting for the relay: don't cut twice.
    if (slot.desiredState === false) continue;

    const label = slot.label || slot.appliance?.name || slotId;

    actions.push({
      type: 'patchSlot',
      deviceId,
      slotId,
      patch: { desiredState: false },
    });

    actions.push({
      type: 'usage',
      deviceId,
      slotId,
      event: 'CUTOFF',
      durationSec: elapsedSeconds,
      source: 'WORKER',
    });

    actions.push({
      type: 'alert',
      deviceId,
      slotId,
      severity: 'CRITICAL',
      message:
        `${label} was switched off automatically after ${elapsedSeconds}s ` +
        `(max_on_duration is ${limitSeconds}s).`,
    });
  }

  return actions;
}
