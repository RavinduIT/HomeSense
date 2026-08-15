import { Slot, THRESHOLDS } from './types';

/**
 * The hub asked for one thing and the hardware reports another.
 *
 * Instantaneous mismatch is *normal* — it is simply a command in flight. It
 * only means something once it has persisted, which is why `mismatchSince` is
 * recorded in the database and compared against a tolerance window rather than
 * the boolean being trusted on its own.
 */
export function isMismatched(slot: Slot): boolean {
  return (slot.desiredState === true) !== (slot.reportedState === true);
}

/**
 * True when the disagreement has lasted longer than the tolerance window.
 *
 * The window is 10 seconds, comfortably longer than a Realtime Database round
 * trip plus a relay's actuation time, and deliberately longer than the app's
 * 4-second optimistic rollback — so the user is warned that something looks
 * wrong slightly before the badge officially turns red.
 */
export function isSustainedMismatch(slot: Slot, now: number): boolean {
  if (!isMismatched(slot)) return false;
  const since = slot.mismatchSince ?? null;
  if (!since || since <= 0) return false;
  return now - since >= THRESHOLDS.mismatchMs;
}

/**
 * The value `mismatchSince` should hold, given what it currently holds.
 * Returns `undefined` when it should be left alone.
 */
export function nextMismatchSince(
  slot: Slot,
  now: number,
  stale: boolean,
): number | null | undefined {
  const mismatched = !stale && isMismatched(slot);
  const current = slot.mismatchSince ?? null;

  if (mismatched && !current) return now; // the disagreement starts now
  if (!mismatched && current) return null; // resolved, or the node went quiet
  return undefined; // no change
}
