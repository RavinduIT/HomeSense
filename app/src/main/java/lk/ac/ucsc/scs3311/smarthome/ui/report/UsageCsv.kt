package lk.ac.ucsc.scs3311.smarthome.ui.report

import lk.ac.ucsc.scs3311.smarthome.data.repository.UsageRow
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * CSV serialisation for the usage export.
 *
 * Pure and separate from the share Intent so that the escaping — which is the
 * only part that can actually be wrong — is unit-testable. A device named
 * `Kitchen, back wall` or a label containing a quote must not corrupt the file.
 */
object UsageCsv {

    val HEADER = listOf("timestamp", "device", "slot", "event", "duration_seconds", "source")

    fun build(rows: List<UsageRow>, locale: Locale = Locale.UK): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale)
        return buildString {
            appendLine(HEADER.joinToString(","))
            rows.forEach { row ->
                appendLine(
                    listOf(
                        format.format(row.at),
                        row.deviceName,
                        row.slotLabel,
                        row.event,
                        row.durationSec?.toString().orEmpty(),
                        row.source,
                    ).joinToString(",") { escape(it) },
                )
            }
        }
    }

    /**
     * RFC 4180 escaping: quote a field that contains a comma, a quote or a
     * newline, and double any quote inside it.
     */
    fun escape(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
