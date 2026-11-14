package app.custo.feature.overlays

enum class Press { SINGLE, DOUBLE, LONG }

/**
 * Tells single, double and long presses of one hardware key apart. Pure: the caller feeds key
 * down/up events and timeouts with timestamps, and schedules a timeout at [deadline].
 *
 * Latency is only added when needed: with no double or long action, a press counts as
 * [Press.SINGLE] right on key down. With a long action only, single fires on key up. With a
 * double action, single fires once [doubleMs] pass without a second press.
 */
class PressPatternDetector(val longMs: Long = 450, val doubleMs: Long = 280) {

    private enum class State { IDLE, DOWN, WAIT_SECOND, HELD }

    private var state = State.IDLE
    private var downAt = 0L
    private var upAt = 0L
    private var canDouble = false
    private var canLong = false

    val isIdle get() = state == State.IDLE

    /** When the caller should call [onTimeout] next, or null if no timeout is pending. */
    val deadline: Long?
        get() = when (state) {
            State.DOWN -> if (canLong) downAt + longMs else null
            State.WAIT_SECOND -> upAt + doubleMs
            else -> null
        }

    /** Key down. [repeat] is true for auto-repeat events while the key is held. */
    fun onDown(now: Long, canDouble: Boolean, canLong: Boolean, repeat: Boolean = false): Press? {
        if (repeat) return null
        return when (state) {
            State.IDLE -> {
                this.canDouble = canDouble
                this.canLong = canLong
                if (!canDouble && !canLong) { state = State.HELD; Press.SINGLE }
                else { state = State.DOWN; downAt = now; null }
            }
            State.WAIT_SECOND -> { state = State.HELD; Press.DOUBLE }
            // A second down without an up (lost event): restart cleanly.
            State.DOWN, State.HELD -> { state = State.IDLE; onDown(now, canDouble, canLong) }
        }
    }

    fun onUp(now: Long): Press? = when (state) {
        State.DOWN -> when {
            canLong && now - downAt >= longMs -> { state = State.IDLE; Press.LONG }
            canDouble -> { state = State.WAIT_SECOND; upAt = now; null }
            else -> { state = State.IDLE; Press.SINGLE }
        }
        State.HELD -> { state = State.IDLE; null }
        State.WAIT_SECOND, State.IDLE -> null
    }

    fun onTimeout(now: Long): Press? = when (state) {
        State.DOWN -> if (canLong && now - downAt >= longMs) { state = State.HELD; Press.LONG } else null
        State.WAIT_SECOND -> if (now - upAt >= doubleMs) { state = State.IDLE; Press.SINGLE } else null
        else -> null
    }

    fun reset() { state = State.IDLE }
}

/** What to do with one volume key press, decided when it goes down. */
data class KeyPlan(val consume: Boolean, val double: OverlayAction, val long: OverlayAction)

/** Pure rules for volume keys: when to take them over and which actions apply. */
object VolumeKeyPolicy {
    val PASS = KeyPlan(false, OverlayAction.NONE, OverlayAction.NONE)

    fun plan(
        isUp: Boolean,
        inCall: Boolean,
        panelOn: Boolean,
        remapOn: Boolean,
        doubleAction: OverlayAction,
        longAction: OverlayAction,
        longSkip: Boolean,
        musicActive: Boolean,
        screenOn: Boolean,
        alarmActive: Boolean = false,
    ): KeyPlan {
        // Never interfere with calls or a ringing alarm (volume keys silence / snooze it).
        if (inCall || alarmActive) return PASS
        val dbl = if (remapOn) doubleAction.takeIf { it.forKeys } ?: OverlayAction.NONE else OverlayAction.NONE
        var lng = if (remapOn) longAction.takeIf { it.forKeys } ?: OverlayAction.NONE else OverlayAction.NONE
        if (lng == OverlayAction.NONE && panelOn && longSkip && musicActive && screenOn) {
            lng = if (isUp) OverlayAction.NEXT_TRACK else OverlayAction.PREV_TRACK
        }
        val consume = panelOn || dbl != OverlayAction.NONE || lng != OverlayAction.NONE
        return if (consume) KeyPlan(true, dbl, lng) else PASS
    }
}
