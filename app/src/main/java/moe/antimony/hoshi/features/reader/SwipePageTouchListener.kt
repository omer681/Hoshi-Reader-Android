package moe.antimony.hoshi.features.reader

import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

abstract class SwipePageTouchListener(
    swipeDistance: Float = DEFAULT_SWIPE_DISTANCE,
) : View.OnTouchListener {
    private val tracker = ReaderSwipeGestureTracker(minDistance = swipeDistance)

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val tapWasLikely = tracker.tapStillLikely
        var result: ReaderSwipeGestureTracker.Result = ReaderSwipeGestureTracker.Result.None
        if (shouldIgnoreReaderGesture(event)) {
            tracker.suppressCurrentGesture()
        } else {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tracker.onDown(event.x, event.y, event.eventTime)
                    onDown(event.x, event.y)
                }
                MotionEvent.ACTION_POINTER_DOWN -> tracker.onAdditionalPointerDown()
                MotionEvent.ACTION_MOVE -> result = tracker.onMove(event.x, event.y, event.eventTime)
                MotionEvent.ACTION_UP -> result = tracker.onUp(event.x, event.y, event.eventTime)
                MotionEvent.ACTION_CANCEL -> tracker.onCancel()
            }
        }
        if (tapWasLikely && !tracker.tapStillLikely && result !is ReaderSwipeGestureTracker.Result.Tap) onTapAbandoned()
        dispatch(result)
        return false
    }

    open fun onLeftSwipe() = Unit
    open fun onRightSwipe() = Unit
    open fun onTap(x: Float, y: Float) = Unit

    /** A finger touched down; it may become a tap at about this point. */
    open fun onDown(x: Float, y: Float) = Unit

    /** The touch that [onDown] reported will not end as a tap near where it started. */
    open fun onTapAbandoned() = Unit
    open fun shouldIgnoreReaderGesture(event: MotionEvent): Boolean = false

    private fun dispatch(result: ReaderSwipeGestureTracker.Result) {
        when (result) {
            ReaderSwipeGestureTracker.Result.LeftSwipe -> onLeftSwipe()
            ReaderSwipeGestureTracker.Result.RightSwipe -> onRightSwipe()
            is ReaderSwipeGestureTracker.Result.Tap -> onTap(result.x, result.y)
            ReaderSwipeGestureTracker.Result.None -> Unit
        }
    }

    private companion object {
        const val DEFAULT_SWIPE_DISTANCE = 72f
    }
}

internal class ReaderSwipeGestureTracker(
    private val minDistance: Float,
) {
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var hasDown = false
    private var swipeDispatched = false

    /**
     * Whether the touch that is down can still end as a tap close to where it began. This ends
     * sooner than [onUp] gives up on a tap: [TAP_SLOP] forgives a finger that drifts, while work
     * prepared for the tap belongs to the point that was touched.
     */
    var tapStillLikely = false
        private set

    fun onDown(x: Float, y: Float, eventTime: Long) {
        downX = x
        downY = y
        downTime = eventTime
        hasDown = true
        swipeDispatched = false
        tapStillLikely = true
    }

    fun onMove(x: Float, y: Float, eventTime: Long): Result {
        if (!hasDown) return Result.None
        val dx = x - downX
        val dy = y - downY
        if (abs(dx) > TAP_PREPARATION_SLOP || abs(dy) > TAP_PREPARATION_SLOP) tapStillLikely = false
        if (swipeDispatched || minDistance <= 0f) return Result.None
        val elapsedMs = (eventTime - downTime).coerceAtLeast(1L)
        val velocityX = abs(dx) * 1_000f / elapsedMs
        val hasPageDistance = abs(dx) >= minDistance
        val hasFastFlickDistance = abs(dx) >= minDistance / 2f &&
            velocityX >= MIN_FAST_FLICK_VELOCITY_PX_PER_SECOND
        if (
            !hasPageDistance && !hasFastFlickDistance ||
            elapsedMs > MAX_EARLY_SWIPE_DURATION_MS ||
            velocityX < MIN_EARLY_SWIPE_VELOCITY_PX_PER_SECOND
        ) {
            return Result.None
        }
        swipeDispatched = true
        tapStillLikely = false
        return if (dx < 0f) Result.LeftSwipe else Result.RightSwipe
    }

    fun onUp(x: Float, y: Float, eventTime: Long): Result {
        if (!hasDown) return Result.None
        val dx = x - downX
        val dy = y - downY
        val elapsedMs = eventTime - downTime
        val wasSwipeDispatched = swipeDispatched
        onCancel()
        return if (
            !wasSwipeDispatched &&
            elapsedMs <= MAX_TAP_DURATION_MS &&
            abs(dx) < TAP_SLOP &&
            abs(dy) < TAP_SLOP
        ) {
            Result.Tap(x, y)
        } else {
            Result.None
        }
    }

    fun onCancel() {
        hasDown = false
        swipeDispatched = false
        tapStillLikely = false
    }

    fun suppressCurrentGesture() {
        onCancel()
    }

    fun onAdditionalPointerDown() {
        suppressCurrentGesture()
    }

    sealed class Result {
        data object None : Result()
        data object LeftSwipe : Result()
        data object RightSwipe : Result()
        data class Tap(val x: Float, val y: Float) : Result()
    }

    companion object {
        const val MAX_TAP_DURATION_MS = 500L
        private const val TAP_SLOP = 72f
        private const val TAP_PREPARATION_SLOP = 24f
        private const val MIN_FAST_FLICK_VELOCITY_PX_PER_SECOND = 900f
        private const val MAX_EARLY_SWIPE_DURATION_MS = 300L
        private const val MIN_EARLY_SWIPE_VELOCITY_PX_PER_SECOND = 360f
    }
}
