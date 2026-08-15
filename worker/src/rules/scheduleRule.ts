import { Action, Clock, Device, normaliseDays, slotsOf } from './types';

/** Local wall-clock minute of day and day of week, 0 = Sunday. */
export interface LocalTime {
  minuteOfDay: number;
  dayOfWeek: number;
}

export function localTimeOf(now: number, timeZoneOffsetMinutes = 0): LocalTime {
  const shifted = new Date(now + timeZoneOffsetMinutes * 60_000);
  return {
    minuteOfDay: shifted.getUTCHours() * 60 + shifted.getUTCMinutes(),
    dayOfWeek: shifted.getUTCDay(),
  };
}

/**
 * Preset on/off periods — the light-bulb half of the scheduling requirement.
 *
 * ### Edges, not levels
 * The rule fires only at the boundary minutes, not continuously through the
 * window. If it asserted the whole window, a user who switched a scheduled
 * light off at 19:00 would find it back on within a minute, which is
 * infuriating and would be scored as a bug rather than a feature. Acting only
 * on the transition means a manual override holds until the next boundary.
 *
 * ### Why no extra bookkeeping is needed
 * Firing is idempotent: at the boundary minute the rule only writes when
 * `desiredState` differs from the target. The sweep runs twice a minute, so
 * the second pass is a no-op, and a worker restarting mid-minute cannot
 * double-fire. The cost is that a boundary missed entirely while the worker
 * was down is not retro-applied — a deliberate trade, documented in the report.
 */
export function scheduleRule(
  deviceId: string,
  device: Device,
  clock: Clock,
  timeZoneOffsetMinutes = 0,
): Action[] {
  const actions: Action[] = [];
  const { minuteOfDay, dayOfWeek } = localTimeOf(clock.now(), timeZoneOffsetMinutes);

  for (const [slotId, slot] of slotsOf(device)) {
    const schedule = slot.schedule;
    if (!schedule?.enabled) continue;

    const onAt = schedule.onAtMinuteOfDay ?? 0;
    const offAt = schedule.offAtMinuteOfDay ?? 0;
    // A zero-length window would switch on and off in the same minute.
    if (onAt === offAt) continue;

    const days = normaliseDays(schedule.days);
    if (days.length > 0 && !days.includes(dayOfWeek)) continue;

    let target: boolean | null = null;
    if (minuteOfDay === onAt) target = true;
    else if (minuteOfDay === offAt) target = false;
    if (target === null) continue;

    if (slot.desiredState === target) continue; // already there; nothing to do

    actions.push({
      type: 'patchSlot',
      deviceId,
      slotId,
      patch: { desiredState: target },
    });
    actions.push({
      type: 'usage',
      deviceId,
      slotId,
      event: target ? 'ON' : 'OFF',
      source: 'SCHEDULE',
    });
  }

  return actions;
}

/**
 * Whether a schedule says a slot should be on at a given moment.
 *
 * Not used to drive the rule above — it is the query the UI and the tests ask,
 * and it handles the window that wraps past midnight, which is the ordinary
 * case for a porch light ("on 18:30, off 06:00").
 */
export function shouldBeOnAt(
  schedule: NonNullable<Device['slots']>[string]['schedule'],
  minuteOfDay: number,
  dayOfWeek: number,
): boolean {
  if (!schedule?.enabled) return false;
  const onAt = schedule.onAtMinuteOfDay ?? 0;
  const offAt = schedule.offAtMinuteOfDay ?? 0;
  if (onAt === offAt) return false;

  const days = normaliseDays(schedule.days);
  if (days.length > 0 && !days.includes(dayOfWeek)) return false;

  return offAt < onAt
    ? minuteOfDay >= onAt || minuteOfDay < offAt
    : minuteOfDay >= onAt && minuteOfDay < offAt;
}
