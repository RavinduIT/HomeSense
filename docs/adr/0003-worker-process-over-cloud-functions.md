# ADR 0003 — A plain Node worker, not Cloud Functions

**Decision.** Run the safety rules in a standalone Node + TypeScript process
using `firebase-admin` (`/worker`), runnable locally or on a free Render/Railway
instance — not in Cloud Functions.

**Why.** Cloud Functions require the Blaze plan, which needs a card on file. The
project constraint is no paid services. The rules live in `worker/src/rules/` as
pure functions with an injected clock, so they lift into a Cloud Function
unchanged; `docs/CLOUD_FUNCTIONS.md` shows that path.

**Cost.** Someone must run the process. Timer state therefore lives in the
database, never in memory, and a 30-second sweep re-arms everything after a
restart — so a crashed worker loses no armed cutoff.
