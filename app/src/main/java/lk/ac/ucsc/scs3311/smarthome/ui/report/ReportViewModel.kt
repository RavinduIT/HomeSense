package lk.ac.ucsc.scs3311.smarthome.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import lk.ac.ucsc.scs3311.smarthome.HomeSenseApp
import lk.ac.ucsc.scs3311.smarthome.data.local.SlotUsageTotal
import lk.ac.ucsc.scs3311.smarthome.data.repository.HomeRepository
import lk.ac.ucsc.scs3311.smarthome.data.repository.UsageRow
import java.util.Calendar
import java.util.TimeZone

/** The window the report covers. */
enum class ReportRange(val label: String) {
    TODAY("Today"),
    WEEK("This week"),
    ALL("All time"),
    ;

    /**
     * Boundaries in the device's own timezone.
     *
     * "Today" means local midnight, not 24 hours ago — a user looking at the
     * report at 09:00 expects this morning, not yesterday morning onwards.
     */
    fun boundsAt(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): LongRange {
        val calendar = Calendar.getInstance(zone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = calendar.timeInMillis

        return when (this) {
            TODAY -> startOfToday..nowMillis
            WEEK -> {
                calendar.add(Calendar.DAY_OF_YEAR, -6)
                calendar.timeInMillis..nowMillis
            }
            ALL -> 0L..nowMillis
        }
    }
}

data class ReportUiState(
    val range: ReportRange = ReportRange.TODAY,
    val totals: List<SlotUsageTotal> = emptyList(),
    val totalOnSeconds: Long = 0L,
    val cutoffCount: Int = 0,
    val isLoading: Boolean = true,
) {
    /** Energy across everything with a known wattage. Always labelled an estimate. */
    val estimatedKwh: Double get() = totals.sumOf { it.estimatedKwh }

    /** The busiest slots — the leaderboard. */
    val leaderboard: List<SlotUsageTotal> get() = totals.take(LEADERBOARD_SIZE)

    val hasData: Boolean get() = totals.any { it.totalOnSeconds > 0 || it.sessions > 0 }

    companion object {
        const val LEADERBOARD_SIZE = 5
    }
}

/**
 * Usage reporting.
 *
 * The numbers are aggregated in **SQL over the Room mirror**, not in Kotlin
 * over a Firebase snapshot. Two reasons, and both are worth defending: the
 * report works with no network, and summing thousands of events does not mean
 * holding the whole log in memory and recomputing on every emit.
 *
 * Everything shown is derived from *logged transitions* — never from
 * reconstructing state by diffing snapshots, which would silently lose every
 * change that happened while the app was closed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModel(private val repository: HomeRepository) : ViewModel() {

    private val range = MutableStateFlow(ReportRange.TODAY)

    /**
     * Recomputed when the range changes. The "now" used for the bounds is
     * captured at that moment rather than ticking, so the numbers do not
     * shuffle under the user's eye while they are reading them.
     */
    private val bounds = MutableStateFlow(ReportRange.TODAY.boundsAt(System.currentTimeMillis()))

    val uiState: StateFlow<ReportUiState> =
        combine(range, bounds) { selected, window -> selected to window }
            .flatMapLatest { (selected, window) ->
                combine(
                    repository.usageTotals(window.first, window.last),
                    repository.totalOnSeconds(window.first, window.last),
                    repository.cutoffCount(window.first, window.last),
                ) { totals, onSeconds, cutoffs ->
                    ReportUiState(
                        range = selected,
                        totals = totals,
                        totalOnSeconds = onSeconds,
                        cutoffCount = cutoffs,
                        isLoading = false,
                    )
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = ReportUiState(),
            )

    fun selectRange(selected: ReportRange) {
        range.value = selected
        bounds.value = selected.boundsAt(System.currentTimeMillis())
    }

    /** Rows for the CSV export, for whatever range is on screen. */
    suspend fun exportRows(): List<UsageRow> {
        val window = bounds.value
        return repository.usageRows(window.first, window.last)
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as HomeSenseApp
                ReportViewModel(app.container.repository)
            }
        }
    }
}
