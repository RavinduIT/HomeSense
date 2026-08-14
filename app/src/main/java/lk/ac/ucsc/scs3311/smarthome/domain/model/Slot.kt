package lk.ac.ucsc.scs3311.smarthome.domain.model

/**
 * A slot is a single *controllable point* — the thing a relay actually switches.
 *
 * Scheduling and [Safety.maxOnDuration] are properties of the slot, not of a
 * device type, because an iron can be plugged into an outlet and a scheduled
 * bulb can hang off channel 2 of a gang box. Modelling "hazard device" and
 * "light device" as separate kinds would make both of those impossible.
 *
 * ### The three-field invariant
 * [desiredState], [reportedState] and [status] are deliberately three different
 * things, written by three different actors:
 *  - the **app** writes only [desiredState] ("I want this on");
 *  - the **simulator** writes only [reportedState] ("the relay is actually on");
 *  - the **worker** writes only [status], reconciling the two.
 *
 * That is what makes ERROR and DISCONNECTED observed facts rather than
 * decorative badges, and the Realtime Database security rules enforce it.
 */
data class Slot(
    val id: String = "",
    val label: String = "",
    val appliance: Appliance = Appliance(),
    /** Written by the app and by the worker (for cutoffs and schedules). */
    val desiredState: Boolean = false,
    /** Written by the simulator only. */
    val reportedState: Boolean = false,
    /** Written by the worker only. */
    val status: SlotStatus = SlotStatus.OFF,
    /** Server timestamp of the moment this slot last turned on; null when off. */
    val onSince: Long? = null,
    val safety: Safety = Safety(),
    val schedule: Schedule = Schedule(),
) {
    /** True when the app's intent and the hardware's report disagree. */
    val isMismatched: Boolean get() = desiredState != reportedState

    /**
     * Seconds this slot has been continuously on, at [nowMillis].
     * Returns 0 when the slot is off.
     */
    fun onDurationSeconds(nowMillis: Long): Long {
        val since = onSince ?: return 0L
        if (status != SlotStatus.ON) return 0L
        return ((nowMillis - since) / 1000L).coerceAtLeast(0L)
    }

    /**
     * Fraction of the permitted on-time already consumed, in 0f..1f, or null
     * when this slot has no cutoff armed. Drives the countdown ring.
     */
    fun cutoffProgress(nowMillis: Long): Float? {
        if (!safety.autoCutoffEnabled) return null
        val limit = safety.maxOnDuration
        if (limit <= 0L) return null
        if (status != SlotStatus.ON) return null
        return (onDurationSeconds(nowMillis).toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    }
}

/**
 * What is plugged into the slot. Free text on purpose — the user names it — plus
 * the two facts the system reasons about: whether it is a fire risk, and how
 * much it draws (for the kWh estimate on the reporting screen).
 */
data class Appliance(
    val name: String = "",
    val hazardous: Boolean = false,
    val watts: Int? = null,
)

/** The four states the specification requires every slot to be able to show. */
enum class SlotStatus {
    ON,
    OFF,

    /** Desired and reported disagreed for longer than the tolerance window. */
    ERROR,

    /** The node's heartbeat went stale; we no longer know what the relay is doing. */
    DISCONNECTED,
    ;

    val isActionable: Boolean get() = this == ON || this == OFF
}

/**
 * Automatic cutoff configuration.
 *
 * The field is called `max_on_duration` in the database. The spec names it that
 * way, so the wire format keeps the exact name and only the Kotlin property is
 * idiomatic — see the DTO mapping in the data layer.
 */
data class Safety(
    /** Maximum permissible continuous on-time, in **seconds**. 0 = no limit. */
    val maxOnDuration: Long = 0L,
    val autoCutoffEnabled: Boolean = false,
) {
    val isArmed: Boolean get() = autoCutoffEnabled && maxOnDuration > 0L
}

/**
 * A daily on/off window, expressed in minutes from midnight so it survives
 * timezone and locale formatting without any date arithmetic.
 */
data class Schedule(
    val enabled: Boolean = false,
    val onAtMinuteOfDay: Int = 0,
    val offAtMinuteOfDay: Int = 0,
    /** ISO-ish day indices, 0 = Sunday .. 6 = Saturday. Empty means every day. */
    val days: List<Int> = emptyList(),
) {
    /** True when the window wraps past midnight, e.g. on 18:00 → off 06:00. */
    val wrapsMidnight: Boolean get() = offAtMinuteOfDay < onAtMinuteOfDay

    fun appliesOn(dayOfWeek: Int): Boolean = days.isEmpty() || dayOfWeek in days

    /**
     * Whether the schedule says this slot should be on at [minuteOfDay].
     * Handles windows that cross midnight.
     */
    fun shouldBeOnAt(minuteOfDay: Int, dayOfWeek: Int): Boolean {
        if (!enabled) return false
        if (!appliesOn(dayOfWeek)) return false
        if (onAtMinuteOfDay == offAtMinuteOfDay) return false
        return if (wrapsMidnight) {
            minuteOfDay >= onAtMinuteOfDay || minuteOfDay < offAtMinuteOfDay
        } else {
            minuteOfDay in onAtMinuteOfDay until offAtMinuteOfDay
        }
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}
