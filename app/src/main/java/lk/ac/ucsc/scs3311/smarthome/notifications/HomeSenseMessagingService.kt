package lk.ac.ucsc.scs3311.smarthome.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives the push the safety worker sends when a cut-off fires.
 *
 * The push is a *courtesy*, not the mechanism. The appliance was already
 * switched off in the database before this message was queued, and the alert
 * is already in `/alerts` where the in-app alert centre will find it. If the
 * notification never arrives — no network, permission refused, phone off —
 * nothing about the safety guarantee changes. Saying that out loud matters,
 * because it is the difference between a system that protects the house and
 * one that merely tells you about the fire.
 */
class HomeSenseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        SafetyNotifications.ensureChannels(this)

        val kind = message.data["kind"]
        val title = message.notification?.title ?: "HomeSense"
        val body = message.notification?.body ?: message.data["message"].orEmpty()
        if (body.isBlank()) return

        // A stable-ish id per device, so repeated alerts about the same iron
        // replace one another instead of stacking up into a wall.
        val id = message.data["deviceId"]?.hashCode() ?: body.hashCode()

        if (kind == KIND_SAFETY_CUTOFF) {
            SafetyNotifications.postSafetyCutoff(this, body, id)
        } else {
            SafetyNotifications.postDeviceProblem(this, title, body, id)
        }
    }

    // onNewToken is deliberately not overridden. The worker publishes to a topic
    // named after the household rather than to individual device tokens, so
    // there is no token registry to keep in sync and nothing to do when one is
    // reissued. Overriding it only to log would leave a deprecated member in
    // the class for no benefit.

    private companion object {
        const val KIND_SAFETY_CUTOFF = "SAFETY_CUTOFF"
    }
}
