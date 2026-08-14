package lk.ac.ucsc.scs3311.smarthome.domain.model

/**
 * A message raised by the server-side worker and pushed to every client.
 *
 * Alerts are written by the worker, and clients may only flip [acknowledged] —
 * enforced by the database security rules, so a phone cannot invent or silence
 * a safety alert.
 */
data class Alert(
    val id: String = "",
    val at: Long = 0L,
    val deviceId: String = "",
    val slotId: String = "",
    val severity: AlertSeverity = AlertSeverity.INFO,
    val message: String = "",
    val acknowledged: Boolean = false,
)

enum class AlertSeverity {
    INFO,
    WARNING,

    /** Safety cutoff fired, or a hazardous appliance faulted. */
    CRITICAL,
}
