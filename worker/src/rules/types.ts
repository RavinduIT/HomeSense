/**
 * The wire shapes, mirroring docs/SCHEMA.md exactly.
 *
 * These are deliberately hand-written rather than generated: the app is Kotlin,
 * the simulator is JavaScript and this is TypeScript, so the schema document is
 * the contract and each runtime states its half of it explicitly. A field
 * renamed here without renaming it there is a bug the tests are meant to catch.
 */

export type SlotStatus = 'ON' | 'OFF' | 'ERROR' | 'DISCONNECTED';
export type LinkState = 'ONLINE' | 'DISCONNECTED';
export type DeviceKind = 'OUTLET' | 'MULTI_SWITCH' | 'CAMERA';
export type UsageEventType = 'ON' | 'OFF' | 'CUTOFF' | 'ERROR' | 'RECONNECT';
export type EventSource = 'APP' | 'SIMULATOR' | 'WORKER' | 'SCHEDULE';
export type AlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL';

export interface Appliance {
  name?: string;
  hazardous?: boolean;
  watts?: number | null;
}

export interface Safety {
  /** Seconds. The spec's exact spelling is kept on the wire. */
  max_on_duration?: number;
  autoCutoffEnabled?: boolean;
}

export interface Schedule {
  enabled?: boolean;
  onAtMinuteOfDay?: number;
  offAtMinuteOfDay?: number;
  /** 0 = Sunday .. 6 = Saturday. Empty or absent means every day. */
  days?: number[] | Record<string, number>;
}

export interface Slot {
  label?: string;
  appliance?: Appliance;
  /** Written by the app, and by this worker for cut-offs and schedules. */
  desiredState?: boolean;
  /** Written by the simulator only. */
  reportedState?: boolean;
  /** Written by this worker only. */
  status?: SlotStatus;
  /** Server timestamp of the last off->on transition. Worker-owned. */
  onSince?: number | null;
  /**
   * When the desired/reported disagreement started. Worker-owned, and stored
   * in the database rather than in this process's memory so that restarting
   * the worker cannot reset a fault that has been building for nine seconds.
   */
  mismatchSince?: number | null;
  safety?: Safety;
  schedule?: Schedule;
}

export interface Camera {
  snapshotUrl?: string;
  streamUrl?: string;
  lastFrameAt?: number;
}

export interface Device {
  floorId?: string;
  name?: string;
  gridX?: number;
  gridY?: number;
  kind?: DeviceKind;
  /** Simulator heartbeat. */
  lastSeen?: number;
  /** Derived from lastSeen by this worker. */
  link?: LinkState;
  camera?: Camera;
  slots?: Record<string, Slot>;
}

/**
 * A rule's output.
 *
 * Rules never touch the network. They read a device and return the writes that
 * *should* happen; a separate executor performs them. That is what makes every
 * rule a pure function testable with a fake clock, and what would let the same
 * files run inside a Cloud Function unchanged.
 */
export type Action =
  | { type: 'patchSlot'; deviceId: string; slotId: string; patch: Partial<Slot> }
  | { type: 'patchDevice'; deviceId: string; patch: Partial<Device> }
  | {
      type: 'usage';
      deviceId: string;
      slotId: string;
      event: UsageEventType;
      durationSec?: number;
      source: EventSource;
    }
  | {
      type: 'alert';
      deviceId: string;
      slotId: string;
      severity: AlertSeverity;
      message: string;
    };

/** Injected so tests can drive time instead of waiting for it. */
export interface Clock {
  now(): number;
}

export const systemClock: Clock = { now: () => Date.now() };

/** Tuning constants, in one place because the report quotes them. */
export const THRESHOLDS = {
  /** A node silent for longer than this is DISCONNECTED. */
  staleHeartbeatMs: 15_000,
  /** desired != reported for longer than this is an ERROR. */
  mismatchMs: 10_000,
  /** How often the sweep re-evaluates every device. */
  sweepMs: 30_000,
} as const;

/** RTDB collapses sparse arrays into index-keyed maps; normalise both. */
export function normaliseDays(days: Schedule['days']): number[] {
  if (!days) return [];
  if (Array.isArray(days)) return days.filter((d) => typeof d === 'number');
  return Object.values(days).filter((d) => typeof d === 'number');
}

export function slotsOf(device: Device): Array<[string, Slot]> {
  return Object.entries(device.slots ?? {});
}
