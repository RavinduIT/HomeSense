package lk.ac.ucsc.scs3311.smarthome.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Runs a write that the server is entitled to refuse.
 *
 * Every write in this application can be rejected. The security rules confine a
 * household to its members and reserve device management and safety
 * configuration to its owner, so a member tapping a control the rules do not
 * permit receives `PERMISSION_DENIED`. The Firebase call rethrows it from
 * `await()`, and an exception escaping `viewModelScope.launch` reaches the
 * default handler and terminates the process.
 *
 * Losing the application is the wrong response to a refusal that the rules
 * exist to produce. The failure is reported to [errors] instead, so the screen
 * can say what happened and the user can carry on.
 */
fun CoroutineScope.launchWrite(
    errors: MutableStateFlow<String?>,
    block: suspend () -> Unit,
): Job = launch {
    runCatching { block() }.onFailure { errors.value = it.asUserMessage() }
}

/**
 * Turns a failure into something worth showing.
 *
 * A refusal is by far the most likely cause and has a specific remedy, so it is
 * named rather than reported as a generic error. Firebase spells the condition
 * differently depending on the call, hence the two spellings.
 */
fun Throwable.asUserMessage(): String {
    val text = message.orEmpty()
    return when {
        text.contains("Permission denied", ignoreCase = true) ||
            text.contains("PERMISSION_DENIED", ignoreCase = true) ->
            "That change was refused. Renaming the household, managing who " +
                "belongs to it and arming an automatic cut-off are reserved " +
                "to its owner."

        text.contains("network", ignoreCase = true) ||
            text.contains("unavailable", ignoreCase = true) ->
            "No connection. The change was not saved."

        text.isBlank() -> "That did not work. Please try again."

        else -> text
    }
}
