# simulator — companion hardware dashboard

A single self-contained `index.html` standing in for the physical appliances:
Firebase Web SDK from a CDN, no build step, open it in a browser.

## Signing in

The simulator authenticates with the **same account as the mobile
application**, then discovers the households that account belongs to and
attaches to one of them.

This is a consequence of the security rules rather than a convenience. A
household is readable only by its members, so hardware belonging to a household
has to be a member of it. Signing in anonymously, as an earlier version did,
now produces a permission error on every read.

The household identifier is generated when the household is created in the
application, so it cannot be configured in advance. It is read from
`/users/{uid}/homes`, the index each account keeps of its own households, which
is what allows discovery without reading anything the account does not own.
When an account belongs to several, a selector appears in the header and the
choice is remembered.

## What it does

- Renders a card per device, grouped by floor. Lamps glow, a slot with an armed
  cut-off shows a bar filling toward `max_on_duration`, and cameras display a
  placeholder stream.
- Follows `desiredState` into `reportedState` after a short actuation delay,
  as a relay would.
- Writes `reportedState` and a `lastSeen` heartbeat every five seconds, and
  nothing else. It does not write `status`: hardware reports its condition, it
  does not decide how that condition is classified.
- Registers `onDisconnect()`, so closing the tab pins the heartbeat and the
  worker marks the node `DISCONNECTED` within its stale window.

Three chaos buttons drive the demo, and each is direct evidence for a
requirement:

| Button | What it does | Proves |
|---|---|---|
| **Simulate fault** | reports the opposite of `desiredState` | `ERROR` is a real observed state |
| **Unplug** | stops the heartbeat | `DISCONNECTED` is observed, not guessed |
| **Physical toggle** | a change originating at the hardware | external updates reach the phone with no manual refresh |
