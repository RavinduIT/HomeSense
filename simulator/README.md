# simulator — companion hardware dashboard

A single self-contained `index.html` standing in for the physical appliances:
Firebase Web SDK from a CDN, no build step, open it in a browser.

**Built in Phase 5.** It will:

- render a card per device, grouped by floor — lamps glow, the iron shows a heat
  bar, the camera cycles placeholder frames;
- write `reportedState` and a `lastSeen` heartbeat every 5 seconds;
- register `onDisconnect()`, so closing the tab turns devices `DISCONNECTED` on
  the phone within seconds.

Three chaos buttons drive the demo, and each is direct evidence for a
requirement:

| Button | What it does | Proves |
|---|---|---|
| **Simulate fault** | reports the opposite of `desiredState` | `ERROR` is a real observed state |
| **Unplug** | stops the heartbeat | `DISCONNECTED` is observed, not guessed |
| **Physical toggle** | a change originating at the hardware | external updates reach the phone with no manual refresh |
