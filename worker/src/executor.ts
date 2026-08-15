import { Database, Reference, ServerValue } from 'firebase-admin/database';
import { Action } from './rules';

/**
 * Turns the actions a rule produced into database writes.
 *
 * This is the only file in the worker that talks to Firebase. Keeping it
 * separate from `src/rules/` is what makes every rule a pure function — and
 * what would let those rules be lifted into a Cloud Function unchanged, with
 * only this executor replaced.
 */
export class ActionExecutor {
  constructor(
    private readonly db: Database,
    private readonly homeId: string,
    private readonly onCriticalAlert?: (message: string, deviceId: string) => Promise<void>,
  ) {}

  private home(): Reference {
    return this.db.ref(`homes/${this.homeId}`);
  }

  async apply(actions: Action[]): Promise<void> {
    for (const action of actions) {
      switch (action.type) {
        case 'patchDevice':
          await this.home().child(`devices/${action.deviceId}`).update(action.patch);
          break;

        case 'patchSlot':
          await this.home()
            .child(`devices/${action.deviceId}/slots/${action.slotId}`)
            .update(action.patch);
          break;

        case 'usage': {
          // Append-only. The timestamp is the server's, so a skewed clock
          // anywhere in the system cannot reorder the log.
          const payload: Record<string, unknown> = {
            at: ServerValue.TIMESTAMP,
            event: action.event,
            source: action.source,
          };
          if (action.durationSec !== undefined) payload.durationSec = action.durationSec;
          await this.home().child(`usage/${action.deviceId}/${action.slotId}`).push(payload);
          break;
        }

        case 'alert': {
          await this.home().child('alerts').push({
            at: ServerValue.TIMESTAMP,
            deviceId: action.deviceId,
            slotId: action.slotId,
            severity: action.severity,
            message: action.message,
            acknowledged: false,
          });
          if (action.severity === 'CRITICAL' && this.onCriticalAlert) {
            await this.onCriticalAlert(action.message, action.deviceId);
          }
          break;
        }
      }
    }
  }
}
