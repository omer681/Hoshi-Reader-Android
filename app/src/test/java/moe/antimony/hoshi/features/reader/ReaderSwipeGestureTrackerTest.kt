package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSwipeGestureTrackerTest {
    @Test
    fun horizontalMovePastThresholdTriggersBeforePointerUp() {
        val tracker = ReaderSwipeGestureTracker(minDistance = 72f)

        tracker.onDown(240f, 100f, eventTime = 1_000L)

        val result = tracker.onMove(150f, 104f, eventTime = 1_120L)

        assertTrue(result == ReaderSwipeGestureTracker.Result.LeftSwipe)
    }

    @Test
    fun shortFastHorizontalMoveTriggersBeforePointerUp() {
        val tracker = ReaderSwipeGestureTracker(minDistance = 72f)

        tracker.onDown(240f, 100f, eventTime = 1_000L)

        val result = tracker.onMove(196f, 104f, eventTime = 1_030L)

        assertTrue(result == ReaderSwipeGestureTracker.Result.LeftSwipe)
    }

    @Test
    fun configuredSwipeDistanceScalesFastFlickDistanceProportionally() {
        val tracker = ReaderSwipeGestureTracker(minDistance = 120f)

        tracker.onDown(240f, 100f, eventTime = 1_000L)
        assertTrue(
            tracker.onMove(196f, 104f, eventTime = 1_030L) ==
                ReaderSwipeGestureTracker.Result.None,
        )

        assertTrue(
            tracker.onMove(180f, 104f, eventTime = 1_040L) ==
                ReaderSwipeGestureTracker.Result.LeftSwipe,
        )
    }

    @Test
    fun zeroSwipeDistanceDisablesSwipesWithoutDisablingTaps() {
        val tracker = ReaderSwipeGestureTracker(minDistance = 0f)

        tracker.onDown(240f, 100f, eventTime = 1_000L)
        assertTrue(
            tracker.onMove(120f, 104f, eventTime = 1_100L) ==
                ReaderSwipeGestureTracker.Result.None,
        )
        assertTrue(
            tracker.onUp(120f, 104f, eventTime = 1_120L) ==
                ReaderSwipeGestureTracker.Result.None,
        )

        tracker.onDown(240f, 100f, eventTime = 2_000L)
        assertTrue(
            tracker.onUp(242f, 103f, eventTime = 2_120L) is
                ReaderSwipeGestureTracker.Result.Tap,
        )
    }

    @Test
    fun additionalPointerCancelsGestureUntilEveryPointerIsReleased() {
        val tracker = ReaderSwipeGestureTracker(minDistance = 72f)

        tracker.onDown(80f, 100f, eventTime = 1_000L)
        tracker.onAdditionalPointerDown()

        assertTrue(
            tracker.onMove(240f, 100f, eventTime = 1_040L) ==
                ReaderSwipeGestureTracker.Result.None,
        )
        assertTrue(
            tracker.onUp(240f, 100f, eventTime = 1_080L) ==
                ReaderSwipeGestureTracker.Result.None,
        )
    }

    @Test
    fun tinyFastHorizontalMoveDoesNotTriggerPageSwipe() {
        val tracker = ReaderSwipeGestureTracker(minDistance = 72f)

        tracker.onDown(240f, 100f, eventTime = 1_000L)

        val result = tracker.onMove(220f, 104f, eventTime = 1_010L)

        assertTrue(result == ReaderSwipeGestureTracker.Result.None)
    }

    @Test
    fun completedSwipeDoesNotTriggerAgainOnLaterMoveOrUp() {
        val tracker = ReaderSwipeGestureTracker(minDistance = 72f)

        tracker.onDown(240f, 100f, eventTime = 1_000L)

        assertTrue(tracker.onMove(150f, 104f, eventTime = 1_120L) == ReaderSwipeGestureTracker.Result.LeftSwipe)
        assertTrue(tracker.onMove(120f, 104f, eventTime = 1_140L) == ReaderSwipeGestureTracker.Result.None)
        assertTrue(tracker.onUp(120f, 104f, eventTime = 1_160L) == ReaderSwipeGestureTracker.Result.None)
    }

    @Test
    fun verticalMovePastThresholdDoesNotTriggerPageSwipe() {
        val tracker = ReaderSwipeGestureTracker(minDistance = 72f)

        tracker.onDown(240f, 100f, eventTime = 1_000L)

        val result = tracker.onMove(232f, 190f, eventTime = 1_120L)

        assertTrue(result == ReaderSwipeGestureTracker.Result.None)
    }

    @Test
    fun slowHorizontalDragDoesNotTriggerPageSwipe() {
        val tracker = ReaderSwipeGestureTracker(minDistance = 72f)

        tracker.onDown(240f, 100f, eventTime = 1_000L)

        val result = tracker.onMove(150f, 104f, eventTime = 1_900L)

        assertTrue(result == ReaderSwipeGestureTracker.Result.None)
    }

    @Test
    fun nativeTextSelectionSuppressesCurrentReaderGesture() {
        val tracker = ReaderSwipeGestureTracker(minDistance = 72f)

        tracker.onDown(240f, 100f, eventTime = 1_000L)
        tracker.suppressCurrentGesture()

        assertTrue(tracker.onMove(150f, 104f, eventTime = 1_120L) == ReaderSwipeGestureTracker.Result.None)
        assertTrue(tracker.onUp(150f, 104f, eventTime = 1_160L) == ReaderSwipeGestureTracker.Result.None)
    }

    @Test
    fun horizontalDragPastThresholdCanPageEvenWhenDiagonal() {
        val tracker = ReaderSwipeGestureTracker(minDistance = 72f)

        tracker.onDown(240f, 100f, eventTime = 1_000L)

        val result = tracker.onMove(150f, 152f, eventTime = 1_120L)

        assertTrue(result == ReaderSwipeGestureTracker.Result.LeftSwipe)
    }

    @Test
    fun shortTapIsReportedOnPointerUp() {
        val tracker = ReaderSwipeGestureTracker(minDistance = 72f)

        tracker.onDown(240f, 100f, eventTime = 1_000L)

        val result = tracker.onUp(242f, 103f, eventTime = 1_120L)

        assertTrue(result is ReaderSwipeGestureTracker.Result.Tap)
        result as ReaderSwipeGestureTracker.Result.Tap
        assertEquals(242f, result.x)
        assertEquals(103f, result.y)
    }

    @Test
    fun longPressIsNotReportedAsTapOnPointerUp() {
        val tracker = ReaderSwipeGestureTracker(minDistance = 72f)

        tracker.onDown(240f, 100f, eventTime = 1_000L)

        val result = tracker.onUp(242f, 103f, eventTime = 1_800L)

        assertTrue(result == ReaderSwipeGestureTracker.Result.None)
    }

    @Test
    fun touchStopsBeingALikelyTapOnceItDriftsEvenIfATapIsStillAccepted() {
        val tracker = ReaderSwipeGestureTracker(minDistance = 72f)
        assertTrue(!tracker.tapStillLikely)

        tracker.onDown(240f, 100f, eventTime = 1_000L)
        assertTrue(tracker.tapStillLikely)
        tracker.onMove(250f, 110f, eventTime = 1_400L)
        assertTrue(tracker.tapStillLikely)
        tracker.onMove(250f, 130f, eventTime = 1_420L)
        assertTrue(!tracker.tapStillLikely)

        assertTrue(tracker.onUp(250f, 130f, eventTime = 1_450L) is ReaderSwipeGestureTracker.Result.Tap)
        assertTrue(!tracker.tapStillLikely)
    }

    @Test
    fun touchStopsBeingALikelyTapAsSoonAsItMovesAtSwipeSpeed() {
        val swiping = ReaderSwipeGestureTracker(minDistance = 72f)
        swiping.onDown(240f, 100f, eventTime = 1_000L)
        assertTrue(swiping.onMove(254f, 100f, eventTime = 1_008L) == ReaderSwipeGestureTracker.Result.None)
        assertTrue(!swiping.tapStillLikely)

        val settling = ReaderSwipeGestureTracker(minDistance = 72f)
        settling.onDown(240f, 100f, eventTime = 1_000L)
        settling.onMove(248f, 100f, eventTime = 1_008L)
        assertTrue(settling.tapStillLikely)
        settling.onMove(254f, 100f, eventTime = 1_040L)
        assertTrue(settling.tapStillLikely)
    }

    @Test
    fun touchStopsBeingALikelyTapWhenItSwipesIsCancelledOrGainsAPointer() {
        val swiped = ReaderSwipeGestureTracker(minDistance = 20f)
        swiped.onDown(240f, 100f, eventTime = 1_000L)
        assertTrue(swiped.onMove(225f, 100f, eventTime = 1_010L) == ReaderSwipeGestureTracker.Result.LeftSwipe)
        assertTrue(!swiped.tapStillLikely)

        val cancelled = ReaderSwipeGestureTracker(minDistance = 72f)
        cancelled.onDown(240f, 100f, eventTime = 1_000L)
        cancelled.onCancel()
        assertTrue(!cancelled.tapStillLikely)

        val pinched = ReaderSwipeGestureTracker(minDistance = 72f)
        pinched.onDown(240f, 100f, eventTime = 1_000L)
        pinched.onAdditionalPointerDown()
        assertTrue(!pinched.tapStillLikely)
    }
}
