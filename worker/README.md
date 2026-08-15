# worker — server-side safety worker

Node + TypeScript + `firebase-admin`. Runs the rules that must keep working
when every phone is switched off.

## Run it

```bash
cd worker
npm install

cp .env.template .env                  # fill in DATABASE_URL
# put the service-account key at worker/serviceAccountKey.json

npm test        # 40 tests, no credentials needed
npm run build
npm start
```

`npm test` needs no Firebase project at all — the rules are pure, so the tests
run against a fake clock.

## The rules

| File | Rule |
|---|---|
| `src/rules/maxOnDurationRule.ts` | Cut off a slot that has run past `max_on_duration` |
| `src/rules/statusRule.ts` | Derive `status`, own `onSince` and `mismatchSince` |
| `src/rules/staleHeartbeatRule.ts` | `lastSeen` older than 15s → `DISCONNECTED` |
| `src/rules/mismatchRule.ts` | `desiredState ≠ reportedState` for >10s → `ERROR` |
| `src/rules/scheduleRule.ts` | Minute-boundary on/off windows |

Every rule is a **pure function** taking an injected clock — no I/O, no
`Date.now()`. `src/executor.ts` is the only file that touches Firebase.

## Why the timer is not a timer

`onSince` lives in the database, and elapsed time is recomputed from it on
every evaluation. A `setTimeout` armed at switch-on would die with the process
and nobody would ever know. Because the state is stored, a worker that crashes
and restarts sees an iron that has been on for 47 seconds against a 30-second
limit and cuts it off on the very next pass.

That is what the 30-second sweep is for. The child listeners give millisecond
reaction; the sweep guarantees that *nothing pending can be lost*.

## Thresholds

| Constant | Value | Why |
|---|---|---|
| `staleHeartbeatMs` | 15 s | Three missed 5-second heartbeats |
| `mismatchMs` | 10 s | Longer than a round trip, so a command in flight is not called a fault |
| `sweepMs` | 30 s | Bounds the recovery window after a restart |

The app rolls an optimistic toggle back after 4 s — deliberately *before* the
10 s ERROR threshold, so the user is warned something is wrong slightly ahead
of the badge turning red.

## Deploying free

Render or Railway, as a background worker:

- build `npm install && npm run build`, start `npm start`;
- set `DATABASE_URL`, `HOME_ID`, `TIMEZONE_OFFSET_MINUTES`;
- paste the service-account JSON into `GOOGLE_APPLICATION_CREDENTIALS_JSON`
  rather than committing a key file.

See `docs/adr/0003` for why this is a plain process, and
`docs/CLOUD_FUNCTIONS.md` for the migration path if the project ever moves to
the Blaze plan.
