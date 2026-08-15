import * as fs from 'node:fs';
import * as path from 'node:path';
import { App, cert, getApps, initializeApp } from 'firebase-admin/app';
import * as dotenv from 'dotenv';

dotenv.config();

/**
 * Admin SDK initialisation.
 *
 * firebase-admin v14 dropped the old `admin.*` namespaces in favour of these
 * modular entry points, so everything is imported from `firebase-admin/app`,
 * `firebase-admin/database` and `firebase-admin/messaging`.
 *
 * The service-account key is read from a file that is gitignored, or from
 * `GOOGLE_APPLICATION_CREDENTIALS_JSON` when running somewhere that only
 * offers environment variables (Render, Railway). Nothing is ever committed.
 */
export function initialiseFirebase(): App {
  const existing = getApps();
  if (existing.length > 0) return existing[0];

  const databaseURL = process.env.DATABASE_URL;
  if (!databaseURL) {
    throw new Error(
      'DATABASE_URL is not set. Copy .env.template to .env and fill it in — ' +
        'see docs/FIREBASE_SETUP.md.',
    );
  }

  const inlineJson = process.env.GOOGLE_APPLICATION_CREDENTIALS_JSON;
  const keyPath = path.resolve(
    process.env.SERVICE_ACCOUNT_PATH ?? path.join(__dirname, '..', 'serviceAccountKey.json'),
  );

  if (inlineJson) {
    return initializeApp({ credential: cert(JSON.parse(inlineJson)), databaseURL });
  }

  if (fs.existsSync(keyPath)) {
    return initializeApp({
      credential: cert(JSON.parse(fs.readFileSync(keyPath, 'utf8'))),
      databaseURL,
    });
  }

  throw new Error(
    `No credentials. Put a service-account key at ${keyPath}, or set ` +
      'GOOGLE_APPLICATION_CREDENTIALS_JSON. See docs/FIREBASE_SETUP.md.',
  );
}

export const HOME_ID = process.env.HOME_ID ?? 'home-1';

/**
 * Minutes east of UTC used to evaluate schedules. Sri Lanka is +330.
 * Kept explicit rather than read from the host clock, because the worker may
 * well run on a server in another timezone from the house.
 */
export const TIMEZONE_OFFSET_MINUTES = Number(process.env.TIMEZONE_OFFSET_MINUTES ?? 330);
