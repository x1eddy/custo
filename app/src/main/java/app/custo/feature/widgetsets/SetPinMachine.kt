package app.custo.feature.widgetsets

/** Pure state of a "pin these widgets one at a time" run (unit-tested). */
data class PinRun(
    val total: Int,
    val index: Int = 0,
    val added: Int = 0,
    val skipped: Int = 0,
    val phase: PinPhase = if (total > 0) PinPhase.READY else PinPhase.DONE,
    val stopped: Boolean = false,
    val unsupported: Boolean = false,
) {
    val done get() = phase == PinPhase.DONE
    /** Widgets never asked for because the run stopped early. */
    val left get() = (total - added - skipped).coerceAtLeast(0)

    fun on(e: PinEvent): PinRun = when (e) {
        PinEvent.Asked -> if (phase == PinPhase.READY) copy(phase = PinPhase.WAITING) else this
        PinEvent.Added -> if (phase == PinPhase.WAITING) next(added = added + 1) else this
        PinEvent.Skipped -> if (phase == PinPhase.WAITING || phase == PinPhase.READY) next(skipped = skipped + 1) else this
        PinEvent.Stop -> if (done) this else copy(phase = PinPhase.DONE, stopped = true)
        PinEvent.Unsupported -> copy(phase = PinPhase.DONE, unsupported = true)
    }

    private fun next(added: Int = this.added, skipped: Int = this.skipped): PinRun {
        val i = index + 1
        return copy(index = i, added = added, skipped = skipped, phase = if (i >= total) PinPhase.DONE else PinPhase.READY)
    }

    /** Short, friendly summary for the end dialog. */
    fun summary(): String = when {
        unsupported -> "Your launcher doesn't let apps add widgets. Long-press the home screen → Widgets → Custo instead."
        else -> buildList {
            add("$added added")
            if (skipped > 0) add("$skipped skipped")
            if (stopped && left > 0) add("$left not asked")
        }.joinToString(", ")
    }
}

enum class PinPhase { READY, WAITING, DONE }

sealed interface PinEvent {
    /** The launcher was asked to add the current widget. */
    data object Asked : PinEvent
    /** The launcher confirmed the widget is on the home screen. */
    data object Added : PinEvent
    /** Declined, timed out, or skipped by the user. */
    data object Skipped : PinEvent
    data object Stop : PinEvent
    data object Unsupported : PinEvent
}
