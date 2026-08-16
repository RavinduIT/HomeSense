import { getMessaging } from 'firebase-admin/messaging';

/**
 * Push notification for a safety cut-off.
 *
 * Sent to a topic rather than to device tokens: the app subscribes to
 * `safety-alerts` on first run, so there is no token registry to keep in sync
 * and every phone in the household is reached. For a coursework build that is
 * the right trade; a product would want per-user targeting.
 *
 * A failure here is logged and swallowed. The cut-off itself has already
 * happened in the database — the appliance is off. Failing the whole
 * evaluation because a notification could not be delivered would be the wrong
 * priority.
 */
/**
 * The topic for a household's safety alerts.
 *
 * Per household rather than global. A single shared topic would deliver every
 * family's cut-off alerts to every installation, which is a disclosure of when
 * other people are and are not at home. Must match `AppContainer.topicFor`.
 */
export function safetyTopicFor(homeId: string): string {
  return `home-${homeId}`;
}

export async function sendSafetyAlert(
  homeId: string,
  message: string,
  deviceId: string,
): Promise<void> {
  try {
    await getMessaging().send({
      topic: safetyTopicFor(homeId),
      notification: {
        title: 'Safety cut-off',
        body: message,
      },
      data: {
        deviceId,
        kind: 'SAFETY_CUTOFF',
      },
      android: {
        priority: 'high',
        notification: {
          channelId: 'safety_alerts',
          priority: 'max',
        },
      },
    });
    console.log(`[fcm] safety alert pushed: ${message}`);
  } catch (error) {
    console.warn('[fcm] could not push the alert (the cut-off still happened):', error);
  }
}
