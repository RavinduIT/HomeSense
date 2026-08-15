import {
  Action,
  Clock,
  Device,
  Slot,
  THRESHOLDS,
  applyActions,
  evaluateDevice,
  isMismatched,
  isStale,
  isSustainedMismatch,
  localTimeOf,
  maxOnDurationRule,
  scheduleRule,
  shouldBeOnAt,
  staleHeartbeatRule,
  statusRule,
} from './index';

/**
 * A clock the test drives by hand.
 *
 * Every rule takes one of these, which is what lets a 30-second cut-off be
 * verified in under a millisecond, and verified at exactly the boundary
 * rather than "somewhere around 30 seconds".
 */
class FakeClock implements Clock {
  constructor(private current = 1_700_000_000_000) {}
  now(): number {
    return this.current;
  }
  advanceSeconds(seconds: number): void {
    this.current += seconds * 1000;
  }
  advanceMs(ms: number): void {
    this.current += ms;
  }
  set(at: number): void {
    this.current = at;
  }
}

const T0 = 1_700_000_000_000;

function slot(overrides: Partial<Slot> = {}): Slot {
  return {
    label: 'Iron',
    appliance: { name: 'Steam iron', hazardous: true, watts: 1200 },
    desiredState: false,
    reportedState: false,
    status: 'OFF',
    onSince: null,
    mismatchSince: null,
    safety: { max_on_duration: 0, autoCutoffEnabled: false },
    schedule: { enabled: false, onAtMinuteOfDay: 0, offAtMinuteOfDay: 0, days: [] },
    ...overrides,
  };
}

function device(overrides: Partial<Device> = {}, slots: Record<string, Slot> = {}): Device {
  return {
    floorId: 'floor-ground',
    name: 'Utility outlet',
    gridX: 1,
    gridY: 1,
    kind: 'OUTLET',
    lastSeen: T0,
    link: 'ONLINE',
    slots: { s1: slot(), ...slots },
    ...overrides,
  };
}

/** A slot the hardware reports as running, with an armed cut-off. */
function runningIron(maxOnDuration = 30, onSince = T0): Record<string, Slot> {
  return {
    s1: slot({
      desiredState: true,
      reportedState: true,
      status: 'ON',
      onSince,
      safety: { max_on_duration: maxOnDuration, autoCutoffEnabled: true },
    }),
  };
}

const patchesFor = (actions: Action[], slotId = 's1') =>
  actions
    .filter((a) => a.type === 'patchSlot' && a.slotId === slotId)
    .flatMap((a) => (a.type === 'patchSlot' ? [a.patch] : []));

const eventsOf = (actions: Action[]) =>
  actions.flatMap((a) => (a.type === 'usage' ? [a] : []));

const alertsOf = (actions: Action[]) =>
  actions.flatMap((a) => (a.type === 'alert' ? [a] : []));

// ===========================================================================
// max_on_duration — the rule the project exists for
// ===========================================================================

describe('maxOnDurationRule', () => {
  it('does not fire one second before the limit', () => {
    const clock = new FakeClock(T0);
    const dev = device({}, runningIron(30, T0));

    clock.advanceSeconds(29);
    expect(maxOnDurationRule('dev-iron', dev, clock)).toEqual([]);
  });

  it('fires at exactly max_on_duration, not a second later', () => {
    const clock = new FakeClock(T0);
    const dev = device({}, runningIron(30, T0));

    clock.advanceSeconds(30);
    const actions = maxOnDurationRule('dev-iron', dev, clock);

    expect(patchesFor(actions)).toEqual([{ desiredState: false }]);
  });

  it('logs a CUTOFF event with the duration actually run, attributed to the worker', () => {
    const clock = new FakeClock(T0);
    const dev = device({}, runningIron(30, T0));
    clock.advanceSeconds(47);

    const events = eventsOf(maxOnDurationRule('dev-iron', dev, clock));

    expect(events).toHaveLength(1);
    expect(events[0]).toMatchObject({
      event: 'CUTOFF',
      durationSec: 47,
      source: 'WORKER',
    });
  });

  it('raises a CRITICAL alert naming the appliance and the limit', () => {
    const clock = new FakeClock(T0);
    const dev = device({}, runningIron(30, T0));
    clock.advanceSeconds(30);

    const alerts = alertsOf(maxOnDurationRule('dev-iron', dev, clock));

    expect(alerts).toHaveLength(1);
    expect(alerts[0].severity).toBe('CRITICAL');
    expect(alerts[0].message).toContain('Iron');
    expect(alerts[0].message).toContain('30s');
  });

  it('survives a worker restart, because the timer is state not a setTimeout', () => {
    // The worker was down for a minute. Nothing was armed in memory; the very
    // first evaluation after restart must still cut off immediately.
    const clock = new FakeClock(T0);
    const dev = device({}, runningIron(30, T0));

    clock.advanceSeconds(90);
    const actions = maxOnDurationRule('dev-iron', dev, clock);

    expect(patchesFor(actions)).toEqual([{ desiredState: false }]);
    expect(eventsOf(actions)[0]).toMatchObject({ durationSec: 90 });
  });

  it('does not cut off twice while the relay is still opening', () => {
    const clock = new FakeClock(T0);
    // desiredState already false: the cut-off has fired and we are waiting.
    const dev = device(
      {},
      {
        s1: slot({
          desiredState: false,
          reportedState: true,
          status: 'ON',
          onSince: T0,
          safety: { max_on_duration: 30, autoCutoffEnabled: true },
        }),
      },
    );
    clock.advanceSeconds(60);

    expect(maxOnDurationRule('dev-iron', dev, clock)).toEqual([]);
  });

  it('ignores a slot with the cut-off disabled', () => {
    const clock = new FakeClock(T0);
    const dev = device(
      {},
      {
        s1: slot({
          desiredState: true,
          reportedState: true,
          status: 'ON',
          onSince: T0,
          safety: { max_on_duration: 30, autoCutoffEnabled: false },
        }),
      },
    );
    clock.advanceSeconds(600);

    expect(maxOnDurationRule('dev-iron', dev, clock)).toEqual([]);
  });

  it('ignores a limit of zero, which means no limit', () => {
    const clock = new FakeClock(T0);
    const dev = device({}, runningIron(0, T0));
    clock.advanceSeconds(600);

    expect(maxOnDurationRule('dev-iron', dev, clock)).toEqual([]);
  });

  it('measures from what the hardware reports, not from what the app asked for', () => {
    const clock = new FakeClock(T0);
    // The app asked for ON but the relay has not closed, so nothing is running
    // and nothing can be over its limit.
    const dev = device(
      {},
      {
        s1: slot({
          desiredState: true,
          reportedState: false,
          status: 'OFF',
          onSince: null,
          safety: { max_on_duration: 30, autoCutoffEnabled: true },
        }),
      },
    );
    clock.advanceSeconds(600);

    expect(maxOnDurationRule('dev-iron', dev, clock)).toEqual([]);
  });

  it('cuts off only the offending slot of a gang box', () => {
    const clock = new FakeClock(T0);
    const dev = device(
      { kind: 'MULTI_SWITCH', name: 'Hall gang box' },
      {
        s1: slot({
          label: 'Heater',
          desiredState: true,
          reportedState: true,
          status: 'ON',
          onSince: T0,
          safety: { max_on_duration: 30, autoCutoffEnabled: true },
        }),
        s2: slot({
          label: 'Ceiling light',
          desiredState: true,
          reportedState: true,
          status: 'ON',
          onSince: T0,
        }),
        s3: slot({ label: 'Fan' }),
      },
    );
    clock.advanceSeconds(45);

    const actions = maxOnDurationRule('dev-gang', dev, clock);
    const touched = actions
      .filter((a) => a.type === 'patchSlot')
      .map((a) => (a.type === 'patchSlot' ? a.slotId : ''));

    expect(touched).toEqual(['s1']);
  });
});

// ===========================================================================
// Heartbeat
// ===========================================================================

describe('staleHeartbeatRule', () => {
  it('treats silence longer than the window as disconnected', () => {
    const clock = new FakeClock(T0);
    const dev = device({ lastSeen: T0, link: 'ONLINE' });

    clock.advanceMs(THRESHOLDS.staleHeartbeatMs);
    expect(isStale(dev, clock.now())).toBe(false);

    clock.advanceMs(1);
    expect(isStale(dev, clock.now())).toBe(true);
    expect(staleHeartbeatRule('d', dev, clock)).toEqual([
      { type: 'patchDevice', deviceId: 'd', patch: { link: 'DISCONNECTED' } },
    ]);
  });

  it('treats a never-seen node as disconnected rather than assuming it is fine', () => {
    const clock = new FakeClock(T0);
    expect(isStale(device({ lastSeen: 0 }), clock.now())).toBe(true);
  });

  it('writes nothing when the link state is already correct', () => {
    const clock = new FakeClock(T0);
    const dev = device({ lastSeen: T0, link: 'ONLINE' });
    expect(staleHeartbeatRule('d', dev, clock)).toEqual([]);
  });
});

// ===========================================================================
// Mismatch
// ===========================================================================

describe('mismatchRule', () => {
  it('does not call a command in flight a fault', () => {
    const inFlight = slot({ desiredState: true, reportedState: false, mismatchSince: T0 });
    expect(isMismatched(inFlight)).toBe(true);
    expect(isSustainedMismatch(inFlight, T0 + 9_999)).toBe(false);
  });

  it('calls it a fault once it has persisted', () => {
    const stuck = slot({ desiredState: true, reportedState: false, mismatchSince: T0 });
    expect(isSustainedMismatch(stuck, T0 + THRESHOLDS.mismatchMs)).toBe(true);
  });

  it('is not a mismatch when both agree', () => {
    expect(isMismatched(slot({ desiredState: true, reportedState: true }))).toBe(false);
  });
});

// ===========================================================================
// Status derivation
// ===========================================================================

describe('statusRule', () => {
  it('mirrors what the hardware reports, not what the app asked for', () => {
    const clock = new FakeClock(T0);
    const dev = device(
      {},
      { s1: slot({ desiredState: true, reportedState: false, status: 'OFF' }) },
    );

    const actions = statusRule('d', dev, clock);
    const patches = patchesFor(actions);

    // Status stays OFF; only the mismatch clock starts.
    expect(patches.some((p) => p.status === 'ON')).toBe(false);
    expect(patches[0].mismatchSince).toBe(T0);
  });

  it('publishes ON and stamps onSince when the relay actually closes', () => {
    const clock = new FakeClock(T0);
    const dev = device(
      {},
      { s1: slot({ desiredState: true, reportedState: true, status: 'OFF' }) },
    );

    const patch = patchesFor(statusRule('d', dev, clock))[0];

    expect(patch.status).toBe('ON');
    expect(patch.onSince).toBe(T0);
  });

  it('publishes ERROR once a mismatch is sustained, and alerts', () => {
    const clock = new FakeClock(T0);
    let dev = device(
      {},
      { s1: slot({ desiredState: true, reportedState: false, status: 'OFF' }) },
    );

    dev = applyActions(dev, statusRule('d', dev, clock)); // starts the clock
    clock.advanceMs(THRESHOLDS.mismatchMs);

    const actions = statusRule('d', dev, clock);
    expect(patchesFor(actions)[0].status).toBe('ERROR');

    const alerts = alertsOf(actions);
    expect(alerts).toHaveLength(1);
    expect(alerts[0].severity).toBe('CRITICAL'); // hazardous appliance
  });

  it('DISCONNECTED outranks a mismatch, because silence is not evidence', () => {
    const clock = new FakeClock(T0);
    const dev = device(
      { lastSeen: T0 - 60_000 },
      { s1: slot({ desiredState: true, reportedState: false, status: 'OFF', mismatchSince: T0 - 60_000 }) },
    );

    const patch = patchesFor(statusRule('d', dev, clock))[0];
    expect(patch.status).toBe('DISCONNECTED');
  });

  it('clears the mismatch clock when the node goes quiet', () => {
    const clock = new FakeClock(T0);
    const dev = device(
      { lastSeen: 0 },
      { s1: slot({ desiredState: true, reportedState: false, mismatchSince: T0 - 5_000 }) },
    );

    expect(patchesFor(statusRule('d', dev, clock))[0].mismatchSince).toBeNull();
  });

  it('logs an OFF with the real duration when a slot stops while nobody was watching', () => {
    const clock = new FakeClock(T0 + 300_000);
    const dev = device(
      { lastSeen: T0 + 300_000 },
      { s1: slot({ desiredState: false, reportedState: false, status: 'ON', onSince: T0 }) },
    );

    const events = eventsOf(statusRule('d', dev, clock));
    expect(events).toHaveLength(1);
    expect(events[0]).toMatchObject({ event: 'OFF', durationSec: 300, source: 'WORKER' });
  });

  it('logs a RECONNECT when a node comes back', () => {
    const clock = new FakeClock(T0);
    const dev = device(
      { lastSeen: T0 },
      { s1: slot({ status: 'DISCONNECTED', desiredState: false, reportedState: false }) },
    );

    expect(eventsOf(statusRule('d', dev, clock))[0]).toMatchObject({ event: 'RECONNECT' });
  });

  it('keeps onSince across a disconnection so the cut-off does not restart its count', () => {
    const clock = new FakeClock(T0 + 20_000);
    const dev = device(
      { lastSeen: 0 },
      {
        s1: slot({
          desiredState: true,
          reportedState: true,
          status: 'ON',
          onSince: T0,
          safety: { max_on_duration: 30, autoCutoffEnabled: true },
        }),
      },
    );

    const patch = patchesFor(statusRule('d', dev, clock))[0];
    expect(patch.status).toBe('DISCONNECTED');
    expect(patch.onSince).toBeUndefined(); // untouched
  });

  it('writes nothing at all when the world is already consistent', () => {
    const clock = new FakeClock(T0);
    const dev = device(
      { lastSeen: T0, link: 'ONLINE' },
      { s1: slot({ desiredState: false, reportedState: false, status: 'OFF' }) },
    );

    expect(statusRule('d', dev, clock)).toEqual([]);
  });
});

// ===========================================================================
// Schedules
// ===========================================================================

describe('scheduleRule', () => {
  /** Builds a moment at a given UTC minute of day. */
  function at(minuteOfDay: number, dayOffset = 0): number {
    const base = Date.UTC(2026, 7, 10); // Monday 2026-08-10
    return base + dayOffset * 86_400_000 + minuteOfDay * 60_000;
  }

  const lamp = (schedule: Slot['schedule'], desiredState = false) => ({
    s1: slot({ label: 'Porch light', appliance: { name: 'Bulb' }, desiredState, schedule }),
  });

  it('switches on at the boundary minute', () => {
    const clock = new FakeClock(at(18 * 60));
    const dev = device({}, lamp({ enabled: true, onAtMinuteOfDay: 1080, offAtMinuteOfDay: 1380, days: [] }));

    const actions = scheduleRule('d', dev, clock);
    expect(patchesFor(actions)).toEqual([{ desiredState: true }]);
    expect(eventsOf(actions)[0]).toMatchObject({ event: 'ON', source: 'SCHEDULE' });
  });

  it('switches off at the boundary minute', () => {
    const clock = new FakeClock(at(23 * 60));
    const dev = device({}, lamp({ enabled: true, onAtMinuteOfDay: 1080, offAtMinuteOfDay: 1380, days: [] }, true));

    expect(patchesFor(scheduleRule('d', dev, clock))).toEqual([{ desiredState: false }]);
  });

  it('does nothing in the middle of the window, so a manual override holds', () => {
    // The user switched the light off at 19:00. The rule must not fight them.
    const clock = new FakeClock(at(19 * 60));
    const dev = device({}, lamp({ enabled: true, onAtMinuteOfDay: 1080, offAtMinuteOfDay: 1380, days: [] }, false));

    expect(scheduleRule('d', dev, clock)).toEqual([]);
  });

  it('is idempotent when the sweep runs twice in the same minute', () => {
    const clock = new FakeClock(at(18 * 60));
    let dev = device({}, lamp({ enabled: true, onAtMinuteOfDay: 1080, offAtMinuteOfDay: 1380, days: [] }));

    const first = scheduleRule('d', dev, clock);
    expect(first).not.toEqual([]);

    dev = applyActions(dev, first);
    clock.advanceMs(30_000); // the next sweep, same minute
    expect(scheduleRule('d', dev, clock)).toEqual([]);
  });

  it('respects the selected days', () => {
    // Monday 2026-08-10 is day 1; the schedule is weekend-only.
    const clock = new FakeClock(at(18 * 60));
    const dev = device({}, lamp({ enabled: true, onAtMinuteOfDay: 1080, offAtMinuteOfDay: 1380, days: [0, 6] }));

    expect(scheduleRule('d', dev, clock)).toEqual([]);
  });

  it('accepts days that RTDB collapsed into an index-keyed map', () => {
    const clock = new FakeClock(at(18 * 60));
    const dev = device(
      {},
      lamp({ enabled: true, onAtMinuteOfDay: 1080, offAtMinuteOfDay: 1380, days: { '0': 1, '1': 2 } }),
    );

    expect(patchesFor(scheduleRule('d', dev, clock))).toEqual([{ desiredState: true }]);
  });

  it('ignores a disabled schedule and a zero-length window', () => {
    const clock = new FakeClock(at(18 * 60));
    expect(
      scheduleRule('d', device({}, lamp({ enabled: false, onAtMinuteOfDay: 1080, offAtMinuteOfDay: 1380 })), clock),
    ).toEqual([]);
    expect(
      scheduleRule('d', device({}, lamp({ enabled: true, onAtMinuteOfDay: 1080, offAtMinuteOfDay: 1080 })), clock),
    ).toEqual([]);
  });

  it('applies the timezone offset', () => {
    // 12:30 UTC is 18:00 in Sri Lanka (UTC+5:30).
    const clock = new FakeClock(at(12 * 60 + 30));
    const dev = device({}, lamp({ enabled: true, onAtMinuteOfDay: 1080, offAtMinuteOfDay: 1380, days: [] }));

    expect(scheduleRule('d', dev, clock, 0)).toEqual([]);
    expect(patchesFor(scheduleRule('d', dev, clock, 330))).toEqual([{ desiredState: true }]);
  });

  it('localTimeOf reports the shifted wall clock', () => {
    expect(localTimeOf(at(12 * 60 + 30), 330)).toEqual({ minuteOfDay: 1080, dayOfWeek: 1 });
  });

  describe('shouldBeOnAt', () => {
    const evening = { enabled: true, onAtMinuteOfDay: 1080, offAtMinuteOfDay: 1380, days: [] };
    const overnight = { enabled: true, onAtMinuteOfDay: 1110, offAtMinuteOfDay: 360, days: [] };

    it('handles an ordinary window', () => {
      expect(shouldBeOnAt(evening, 1079, 1)).toBe(false);
      expect(shouldBeOnAt(evening, 1080, 1)).toBe(true);
      expect(shouldBeOnAt(evening, 1379, 1)).toBe(true);
      expect(shouldBeOnAt(evening, 1380, 1)).toBe(false);
    });

    it('handles a window that wraps past midnight', () => {
      expect(shouldBeOnAt(overnight, 1400, 1)).toBe(true);
      expect(shouldBeOnAt(overnight, 1, 1)).toBe(true);
      expect(shouldBeOnAt(overnight, 359, 1)).toBe(true);
      expect(shouldBeOnAt(overnight, 360, 1)).toBe(false);
      expect(shouldBeOnAt(overnight, 720, 1)).toBe(false);
    });
  });
});

// ===========================================================================
// The whole pipeline
// ===========================================================================

describe('evaluateDevice', () => {
  it('runs the full life of an iron: on, counting, cut off, logged, alerted', () => {
    const clock = new FakeClock(T0);
    let dev = device(
      { name: 'Utility outlet', lastSeen: T0 },
      {
        s1: slot({
          desiredState: true,
          reportedState: true,
          status: 'OFF',
          onSince: null,
          safety: { max_on_duration: 30, autoCutoffEnabled: true },
        }),
      },
    );

    /** The node is alive throughout, so it keeps sending heartbeats. */
    const beat = (seconds: number) => {
      clock.advanceSeconds(seconds);
      dev = { ...dev, lastSeen: clock.now() };
    };

    // Sweep 1: the worker notices the relay is closed and stamps onSince.
    let actions = evaluateDevice('dev-iron', dev, clock);
    dev = applyActions(dev, actions);
    expect(dev.slots!.s1.status).toBe('ON');
    expect(dev.slots!.s1.onSince).toBe(T0);

    // Sweep 2, 29 seconds later: still inside the limit.
    beat(29);
    actions = evaluateDevice('dev-iron', dev, clock);
    expect(actions.filter((a) => a.type === 'usage')).toHaveLength(0);

    // Sweep 3, at the limit: cut off.
    beat(1);
    actions = evaluateDevice('dev-iron', dev, clock);
    dev = applyActions(dev, actions);

    expect(dev.slots!.s1.desiredState).toBe(false);
    expect(eventsOf(actions).map((e) => e.event)).toContain('CUTOFF');
    expect(alertsOf(actions)[0].severity).toBe('CRITICAL');

    // Sweep 4: the relay obeys and the status settles to OFF.
    dev.slots!.s1.reportedState = false;
    beat(1);
    actions = evaluateDevice('dev-iron', dev, clock);
    dev = applyActions(dev, actions);

    expect(dev.slots!.s1.status).toBe('OFF');
    expect(dev.slots!.s1.onSince).toBeNull();
  });

  it('a scheduled switch-on cannot undo a cut-off fired in the same pass', () => {
    // 18:00 exactly, and the iron is simultaneously over its limit.
    const at1800 = Date.UTC(2026, 7, 10) + 1080 * 60_000;
    const clock = new FakeClock(at1800);
    const dev = device(
      { lastSeen: at1800 },
      {
        s1: slot({
          desiredState: true,
          reportedState: true,
          status: 'ON',
          onSince: at1800 - 60_000,
          safety: { max_on_duration: 30, autoCutoffEnabled: true },
          schedule: { enabled: true, onAtMinuteOfDay: 1080, offAtMinuteOfDay: 1380, days: [] },
        }),
      },
    );

    const actions = evaluateDevice('dev-iron', dev, clock);
    const final = applyActions(dev, actions);

    // The schedule ran, saw desiredState already true, and did nothing; the
    // cut-off's write is the last word.
    expect(final.slots!.s1.desiredState).toBe(false);
  });

  it('an unplugged node produces DISCONNECTED and no cut-off', () => {
    const clock = new FakeClock(T0 + 120_000);
    const dev = device(
      { lastSeen: 0 },
      {
        s1: slot({
          desiredState: true,
          reportedState: true,
          status: 'ON',
          onSince: T0,
          safety: { max_on_duration: 30, autoCutoffEnabled: true },
        }),
      },
    );

    const actions = evaluateDevice('dev-iron', dev, clock);
    const final = applyActions(dev, actions);

    expect(final.slots!.s1.status).toBe('DISCONNECTED');
    // We cannot switch off something we cannot reach; the cut-off does not
    // pretend otherwise. It fires the moment the node comes back still running.
    expect(final.link).toBe('DISCONNECTED');
  });

  it('a camera has no slots and produces only link bookkeeping', () => {
    const clock = new FakeClock(T0);
    const cam: Device = {
      name: 'Front door camera',
      kind: 'CAMERA',
      lastSeen: T0,
      link: 'DISCONNECTED',
      camera: { snapshotUrl: 'mock://s', streamUrl: 'mock://t', lastFrameAt: T0 },
    };

    expect(evaluateDevice('cam', cam, clock)).toEqual([
      { type: 'patchDevice', deviceId: 'cam', patch: { link: 'ONLINE' } },
    ]);
  });
});
