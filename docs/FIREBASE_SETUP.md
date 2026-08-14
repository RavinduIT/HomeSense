# Firebase setup — from zero

The repository contains **no secrets**. Everything below is on the free Spark
plan; nothing here requires a card.

You only need this for the `live` flavour. The `demo` flavour runs entirely on a
fake in-memory backend and needs no Firebase project at all.

## 1. Create the project

1. <https://console.firebase.google.com> → **Create a project**.
2. Name it `homesense` (or anything). Google Analytics: **off** — not needed.

## 2. Create the Realtime Database

1. Left rail → **Build → Realtime Database → Create Database**.
2. Location: **Singapore (`asia-southeast1`)** — lowest latency from Sri Lanka.
   Whatever you pick, note the URL; it looks like
   `https://homesense-xxxx-default-rtdb.asia-southeast1.firebasedatabase.app`.
3. Start in **locked mode**. `database.rules.json` from this repo is deployed
   later and replaces the defaults.

## 3. Register the Android app

1. Project overview → the Android icon.
2. Package name — register **both** flavours, they have different IDs:
   - `lk.ac.ucsc.scs3311.smarthome`
   - `lk.ac.ucsc.scs3311.smarthome.demo`
3. Download `google-services.json` → put it at `app/google-services.json`.

   The file is gitignored. `app/build.gradle.kts` applies the google-services
   plugin **only when the file exists**, so a fresh clone still builds.

## 4. Enable Anonymous Auth

Build → **Authentication → Get started → Anonymous → Enable**.

The security rules key off `auth.uid`, so without this every write is rejected.

## 5. Service account for the worker

1. ⚙ → **Project settings → Service accounts → Generate new private key**.
2. Save as `worker/serviceAccountKey.json` (gitignored).
3. Copy `worker/.env.template` to `worker/.env` and set `DATABASE_URL` to the
   URL from step 2.

## 6. Simulator config

Copy `simulator/firebase-config.template.js` to `simulator/firebase-config.js`
and paste the web config from Project settings → **Your apps → Web app**. If no
web app exists, add one (no hosting needed).

## 7. Deploy the security rules

Either paste `database.rules.json` into the console's **Rules** tab, or:

```bash
npm install -g firebase-tools
firebase login
firebase deploy --only database
```

The rules are what stop a phone writing `status`. Without them the three-field
invariant is only a convention — deploy them before recording the demo.

## Checklist

- [ ] `app/google-services.json` exists
- [ ] Anonymous auth enabled
- [ ] `worker/serviceAccountKey.json` + `worker/.env` exist
- [ ] `simulator/firebase-config.js` exists
- [ ] `database.rules.json` deployed
