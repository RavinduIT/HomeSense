package lk.ac.ucsc.scs3311.smarthome.ui.report

import lk.ac.ucsc.scs3311.smarthome.data.repository.UsageRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * CSV escaping is the one part of the export that can silently corrupt a file,
 * and a device the user named "Kitchen, back wall" is not an exotic case.
 */
class UsageCsvTest {

    private val at = 1_786_000_000_000L

    private fun row(
        device: String = "Utility outlet",
        slot: String = "Iron",
        event: String = "CUTOFF",
        durationSec: Long? = 30,
        source: String = "WORKER",
    ) = UsageRow(at, device, slot, event, durationSec, source)

    @Test
    fun `header names every column`() {
        val csv = UsageCsv.build(emptyList())
        assertEquals("timestamp,device,slot,event,duration_seconds,source", csv.trim())
    }

    @Test
    fun `an ordinary row needs no quoting`() {
        val line = UsageCsv.build(listOf(row())).lines()[1]
        assertTrue(line, line.endsWith("Utility outlet,Iron,CUTOFF,30,WORKER"))
    }

    @Test
    fun `a comma in a name is quoted rather than splitting the row`() {
        val line = UsageCsv.build(listOf(row(device = "Kitchen, back wall"))).lines()[1]
        assertTrue(line, line.contains("\"Kitchen, back wall\""))
        // Six fields, not seven.
        assertEquals(6, splitCsv(line).size)
    }

    @Test
    fun `a quote is doubled, per RFC 4180`() {
        assertEquals("\"the \"\"good\"\" iron\"", UsageCsv.escape("the \"good\" iron"))
    }

    @Test
    fun `a newline inside a field is quoted`() {
        assertEquals("\"first\nsecond\"", UsageCsv.escape("first\nsecond"))
    }

    @Test
    fun `a missing duration becomes an empty field, not the word null`() {
        val line = UsageCsv.build(listOf(row(event = "ON", durationSec = null))).lines()[1]
        assertTrue(line, line.endsWith("Iron,ON,,APP") || line.endsWith("Iron,ON,,WORKER"))
        assertTrue(line, !line.contains("null"))
    }

    @Test
    fun `timestamps are written in a sortable format`() {
        val line = UsageCsv.build(listOf(row()), Locale.UK).lines()[1]
        assertTrue(line, Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},").containsMatchIn(line))
    }

    @Test
    fun `every row is written`() {
        val csv = UsageCsv.build(List(25) { row() })
        // 1 header + 25 rows + trailing newline.
        assertEquals(26, csv.trim().lines().size)
    }

    /** A minimal RFC 4180 reader, used only to prove the writer round-trips. */
    private fun splitCsv(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                inQuotes && char == '"' && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        fields += current.toString()
        return fields
    }
}
