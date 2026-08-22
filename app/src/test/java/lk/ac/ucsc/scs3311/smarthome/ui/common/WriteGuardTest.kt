package lk.ac.ucsc.scs3311.smarthome.ui.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * A refused write must not end the process.
 *
 * The security rules reserve device management and safety configuration to a
 * household's owner, so a member operating those controls receives
 * `PERMISSION_DENIED`. Firebase rethrows it from `await()`, and an exception
 * escaping `viewModelScope.launch` reaches the default handler and terminates
 * the application. Before this guard existed, a member tapping "add device"
 * crashed it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WriteGuardTest {

    @Test
    fun `a refused write is reported rather than thrown`() = runTest {
        val errors = MutableStateFlow<String?>(null)

        // The whole point: this does not propagate out of the coroutine.
        backgroundScope.launchWrite(errors) {
            throw RuntimeException("Permission denied")
        }.join()

        assertEquals(
            "That change was refused. Renaming the household, managing who " +
                "belongs to it and arming an automatic cut-off are reserved " +
                "to its owner.",
            errors.value,
        )
    }

    @Test
    fun `a successful write reports nothing new`() = runTest {
        val errors = MutableStateFlow<String?>("stale message")
        var ran = false

        backgroundScope.launchWrite(errors) { ran = true }.join()

        assertTrue(ran)
        // Left as it was: clearing is the screen's business, not the guard's.
        assertEquals("stale message", errors.value)
    }

    @Test
    fun `a refusal is named rather than reported as a generic failure`() {
        val message = RuntimeException("PERMISSION_DENIED: Permission denied").asUserMessage()

        // Not the wording, which will change; that it says who may do this and
        // does not fall through to the generic case, which will not.
        assertTrue(message.contains("owner"))
        assertNotEquals("That did not work. Please try again.", message)
        assertFalse(message.contains("PERMISSION_DENIED"))
    }

    @Test
    fun `a connection failure says the change was not saved`() {
        assertEquals(
            "No connection. The change was not saved.",
            IOException("network unreachable").asUserMessage(),
        )
    }

    @Test
    fun `a failure with no message still says something useful`() {
        assertEquals("That did not work. Please try again.", RuntimeException().asUserMessage())
    }

    @Test
    fun `an unrecognised failure is passed through rather than hidden`() {
        assertEquals("index not defined", RuntimeException("index not defined").asUserMessage())
    }
}
