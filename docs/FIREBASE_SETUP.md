# Firebase setup

The repository contains no credentials. Everything below is on the free Spark
plan; none of it requires a payment method.

This is needed only for the `live` flavour. The `demo` flavour runs against an
in-memory backend and needs no Firebase project at all.

## Project

| | |
|---|---|
| Project name | App-uni |
| Project ID | `nappp-cd3d2` |
| Project number | `738342783371` |

## 1. Register the Android applications

The `google-services` plugin matches on package name exactly. Both flavours must
be registered, or the build fails with *No matching client found*.

Firebase console → Project settings → **Your apps** → Add app → Android:

| Package name | Purpose |
|---|---|
| `lk.ac.ucsc.scs3311.smarthome` | `live` flavour |
| `lk.ac.ucsc.scs3311.smarthome.demo` | `demo` flavour |

Two applications within one project is the normal arrangement and costs
nothing. A nickname is optional. The SHA-1 fingerprint is only required for
Google Sign-In, which this project does not use.

## 2. Create the Realtime Database

Build → **Realtime Database** → Create Database.

- Location: **Singapore (`asia-southeast1`)** gives the lowest latency from Sri
  Lanka. Any location works; note the URL either way.
- Start in **locked mode**. The rules in `database.rules.json` replace the
  defaults at step 5.

Until the database exists, `google-services.json` contains no `firebase_url`
field and the application cannot connect.

## 3. Enable sign-in methods

Build → Authentication → Get started → **Sign-in method**:

| Provider | Why |
|---|---|
| **Email/Password** | The primary sign-in method |
| **Anonymous** | Allows the application to be tried before registering, and is upgraded in place to a permanent account without losing data |

## 4. Download the configuration

Project settings → Your apps → `google-services.json`.

Save it to **`app/google-services.json`**. The file is excluded by
`.gitignore`; `app/build.gradle.kts` applies the plugin only when it is present,
so a checkout without it still builds.

Confirm the downloaded file contains:

- `project_info.firebase_url` — present only after step 2
- two entries under `client`, one for each package name from step 1

## 5. Service account for the worker

Project settings → **Service accounts** → Generate new private key.

Save as `worker/serviceAccountKey.json` (excluded by `.gitignore`), then copy
`worker/.env.template` to `worker/.env` and set `DATABASE_URL` to the URL from
step 2.

## 6. Simulator configuration

Copy `simulator/firebase-config.template.js` to `simulator/firebase-config.js`
and paste the web configuration from Project settings → Your apps → Web app. Add
a web app if none exists; hosting is not required.

## 7. Deploy the security rules

Either paste `database.rules.json` into the console's **Rules** tab, or:

```bash
npm install -g firebase-tools
firebase login
firebase use nappp-cd3d2
firebase deploy --only database
```

The rules do two things that the application relies on: they confine each
household to its members, and they prevent any client from writing `status`.
Until they are deployed, the database is running on the console defaults and
neither guarantee holds. Deploy them before recording the demonstration.

## Checklist

- [ ] Both package names registered
- [ ] Realtime Database created
- [ ] Email/Password and Anonymous sign-in enabled
- [ ] `app/google-services.json` present, containing `firebase_url` and two clients
- [ ] `worker/serviceAccountKey.json` and `worker/.env` present
- [ ] `simulator/firebase-config.js` present
- [ ] `database.rules.json` deployed

## Troubleshooting

**`No matching client found for package name`** — the package is not registered,
or `google-services.json` predates the registration. Re-download it after step 1.

**`Can't determine type of DatabaseReference`, or a null database URL** — the
Realtime Database has not been created, so the configuration file has no
`firebase_url`. Complete step 2 and download the file again.

**`PERMISSION_DENIED` on every read** — either the rules have not been deployed,
or the signed-in account is not a member of the household being read. Membership
is written when a household is created or an invite is redeemed.

**Nothing appears although the worker is running** — confirm the household
identifier matches across the application, `worker/.env` and
`simulator/firebase-config.js`.
