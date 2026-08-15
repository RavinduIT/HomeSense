import { ServerValue, getDatabase } from 'firebase-admin/database';
import { HOME_ID, initialiseFirebase } from './firebase';

/**
 * Generates a week of plausible usage history.
 *
 * Run before recording the demo so the reporting screen has something honest
 * to show — an empty chart proves nothing to an examiner, and inventing
 * numbers by hand during the video would be worse.
 *
 * The events written here are the same shape the app, simulator and worker
 * write at runtime; nothing about the reporting path is special-cased for
 * seeded data.
 *
 *   npm run build && node dist/seed.js
 */

interface Session {
  deviceId: string;
  slotId: string;
  /** Local hour the appliance is typically switched on. */
  hour: number;
  /** Typical run length in seconds. */
  durationSec: number;
  /** Chance on any given day that it ran at all. */
  probability: number;
  /** Chance the run ended in a safety cut-off rather than a normal off. */
  cutoffChance?: number;
  /** Cap enforced by the worker, used to make CUTOFF durations honest. */
  maxOnDuration?: number;
}

const SESSIONS: Session[] = [
  { deviceId: 'dev-iron', slotId: 's1', hour: 7, durationSec: 480, probability: 0.7, cutoffChance: 0.25, maxOnDuration: 30 },
  { deviceId: 'dev-kitchen', slotId: 's1', hour: 6, durationSec: 150, probability: 0.9, cutoffChance: 0.1, maxOnDuration: 120 },
  { deviceId: 'dev-kitchen', slotId: 's1', hour: 17, durationSec: 180, probability: 0.6 },
  { deviceId: 'dev-hall-gang', slotId: 's1', hour: 18, durationSec: 5 * 3600, probability: 1 },
  { deviceId: 'dev-hall-gang', slotId: 's2', hour: 18, durationSec: 11 * 3600, probability: 1 },
  { deviceId: 'dev-hall-gang', slotId: 's3', hour: 13, durationSec: 3 * 3600, probability: 0.5 },
  { deviceId: 'dev-bed-gang', slotId: 's1', hour: 21, durationSec: 2 * 3600, probability: 0.85 },
  { deviceId: 'dev-bed-gang', slotId: 's2', hour: 22, durationSec: 40 * 60, probability: 0.5 },
];

const DAYS = 7;

async function main(): Promise<void> {
  const app = initialiseFirebase();
  const db = getDatabase(app);
  const usage = db.ref(`homes/${HOME_ID}/usage`);

  const now = new Date();
  let written = 0;
  let cutoffs = 0;

  for (let dayOffset = DAYS - 1; dayOffset >= 0; dayOffset--) {
    for (const session of SESSIONS) {
      if (Math.random() > session.probability) continue;

      const start = new Date(now);
      start.setDate(start.getDate() - dayOffset);
      // Scatter the start time so the data does not look machine-generated.
      start.setHours(session.hour, Math.floor(Math.random() * 50), 0, 0);
      if (start.getTime() > now.getTime()) continue; // never write the future

      const wasCutOff = Math.random() < (session.cutoffChance ?? 0);
      const jitter = 0.7 + Math.random() * 0.6;
      const durationSec = wasCutOff
        ? (session.maxOnDuration ?? session.durationSec)
        : Math.round(session.durationSec * jitter);

      const endMs = Math.min(start.getTime() + durationSec * 1000, now.getTime());

      const slotUsage = usage.child(`${session.deviceId}/${session.slotId}`);

      await slotUsage.push({
        at: start.getTime(),
        event: 'ON',
        source: dayOffset % 3 === 0 ? 'SCHEDULE' : 'APP',
      });

      await slotUsage.push({
        at: endMs,
        event: wasCutOff ? 'CUTOFF' : 'OFF',
        durationSec: Math.round((endMs - start.getTime()) / 1000),
        source: wasCutOff ? 'WORKER' : 'APP',
      });

      written += 2;
      if (wasCutOff) {
        cutoffs++;
        await db.ref(`homes/${HOME_ID}/alerts`).push({
          at: endMs,
          deviceId: session.deviceId,
          slotId: session.slotId,
          severity: 'CRITICAL',
          message:
            `Appliance was switched off automatically after ${durationSec}s ` +
            `(max_on_duration is ${session.maxOnDuration}s).`,
          acknowledged: dayOffset > 1,
        });
      }
    }
  }

  console.log(
    `[seed] wrote ${written} usage events across ${DAYS} days, ` +
      `including ${cutoffs} safety cut-off(s).`,
  );
  await db.goOffline();
  process.exit(0);
}

main().catch((error) => {
  console.error('[seed] failed:', error);
  process.exit(1);
});
