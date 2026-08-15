import { Action, Clock, Device, LinkState, THRESHOLDS } from './types';

/**
 * A node is stale when it has not sent a heartbeat inside the window.
 *
 * `lastSeen <= 0` counts as stale on purpose: it is the value the simulator's
 * `onDisconnect()` handler writes when its tab closes, and the value a
 * freshly-created device carries before it has ever reported in. Both mean the
 * same thing — nobody has told us what this relay is doing.
 */
export function isStale(device: Device, now: number): boolean {
  const lastSeen = device.lastSeen ?? 0;
  return lastSeen <= 0 || now - lastSeen > THRESHOLDS.staleHeartbeatMs;
}

export function linkStateOf(device: Device, now: number): LinkState {
  return isStale(device, now) ? 'DISCONNECTED' : 'ONLINE';
}

/**
 * Keeps `link` in step with the heartbeat.
 *
 * Slot statuses are handled by `statusRule`, which consumes [isStale] so that
 * the two can never disagree about whether a node is reachable.
 */
export function staleHeartbeatRule(
  deviceId: string,
  device: Device,
  clock: Clock,
): Action[] {
  const link = linkStateOf(device, clock.now());
  if (device.link === link) return [];
  return [{ type: 'patchDevice', deviceId, patch: { link } }];
}
