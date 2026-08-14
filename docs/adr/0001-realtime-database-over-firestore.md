# ADR 0001 — Realtime Database, not Firestore

**Decision.** Use Firebase Realtime Database as the single synchronisation
backbone for app, worker and simulator.

**Why.** RTDB gives per-child listeners (a toggle grid updates one boolean, not
a document), sub-second fan-out, and `onDisconnect()` — which is what lets
`DISCONNECTED` be an *observed* fact rather than a guess. Firestore's
document-granularity listeners and higher write latency are the wrong shape for
a grid of independently toggled slots.

**Cost.** No queries or indexes worth the name, and a denormalised tree we have
to keep consistent by hand. Acceptable: the tree is small and fixed, and
`docs/SCHEMA.md` pins it down.
