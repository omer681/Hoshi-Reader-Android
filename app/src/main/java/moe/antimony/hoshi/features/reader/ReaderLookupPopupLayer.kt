package moe.antimony.hoshi.features.reader

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import moe.antimony.hoshi.features.dictionary.LookupPopupItem

/**
 * Safety net for a looked-up popup whose reveal the page never reports: the screen state is updated
 * anyway after this long. The exact value matters little, as the page's report normally comes first.
 */
internal const val pendingRootLookupSafetyMillis = 500L

/** Runs [action] on the main thread after the frame that draws the page's current content. */
internal fun WebView.afterCurrentContentDrawn(action: () -> Unit) {
    postVisualStateCallback(
        0L,
        object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                // The content is drawn in the next frame, so the action runs once that frame is done.
                postOnAnimation { post(action) }
            }
        },
    )
}

internal data class ReaderRootSelectionHighlight(
    val popupId: String?,
    val rects: List<ReaderSelectionRect>?,
)

internal data class ReaderPopupHistoryCounts(
    val backCount: Int = 0,
    val forwardCount: Int = 0,
)

internal fun readerLookupPopupFramePayloads(
    popups: List<LookupPopupItem>,
    histories: Map<String, ReaderPopupHistoryCounts>,
    viewport: ReaderLookupPopupViewport,
    sasayakiWasPaused: Boolean,
    sasayakiIsPlaying: Boolean,
    iframeUrl: String,
    rootSelectionHighlight: ReaderRootSelectionHighlight?,
    ttsAvailable: Boolean = false,
    ttsWasPaused: Boolean = false,
    ttsIsPlaying: Boolean = false,
    rootPopupIsNew: Boolean = false,
): List<ReaderLookupPopupFramePayload> =
    popups.mapIndexed { index, popup ->
        val history = histories[popup.id] ?: ReaderPopupHistoryCounts()
        ReaderLookupPopupFramePayload.fromPopup(
            popup = popup,
            popupIndex = index,
            viewport = viewport,
            backCount = history.backCount,
            forwardCount = history.forwardCount,
            ttsAvailable = ttsAvailable,
            ttsWasPaused = ttsWasPaused,
            ttsIsPlaying = ttsIsPlaying,
            sasayakiWasPaused = sasayakiWasPaused,
            sasayakiIsPlaying = sasayakiIsPlaying,
            iframeUrl = iframeUrl,
            includeInitialEntryJson = rootPopupIsNew || shouldSendInitialEntryJson(
                popup = popup,
                popupIndex = index,
                rootSelectionHighlight = rootSelectionHighlight,
            ),
        )
    }

private fun shouldSendInitialEntryJson(
    popup: LookupPopupItem,
    popupIndex: Int,
    rootSelectionHighlight: ReaderRootSelectionHighlight?,
    ttsAvailable: Boolean = false,
    ttsWasPaused: Boolean = false,
    ttsIsPlaying: Boolean = false,
): Boolean =
    popupIndex != 0 ||
        rootSelectionHighlight?.popupId != popup.id ||
        rootSelectionHighlight.rects == null

/** A looked-up root popup and its highlight, as last sent to the page. */
internal class PendingRootLookup(val popup: LookupPopupItem, val highlight: ReaderRootSelectionHighlight)

/** The popup placed at the first rectangle of its match, with those rectangles as its highlight. */
internal fun pendingRootLookup(popup: LookupPopupItem, rects: List<ReaderSelectionRect>): PendingRootLookup {
    val shown = rects.ifEmpty { listOf(popup.state.selection.rect) }
    return PendingRootLookup(
        popup = popup.copy(state = popup.state.copy(selection = popup.state.selection.copy(rect = shown.first()))),
        highlight = ReaderRootSelectionHighlight(popupId = popup.id, rects = shown),
    )
}

/**
 * A lookup made when a finger touched a word. The page builds its popup out of sight and shows it
 * once a tap on the same text is confirmed; any other end of the touch discards it.
 */
internal class PreparedRootLookup(
    val selection: ReaderSelectionData,
    val lookup: PendingRootLookup,
    val highlightCount: Int,
)

/** A prepared lookup that no tap claimed is discarded once the touch can no longer be a tap. */
internal const val preparedRootLookupLifetimeMillis = ReaderSwipeGestureTracker.MAX_TAP_DURATION_MS + 300L

/**
 * Sends a popup stack to the reader page as soon as it is known.
 *
 * The composed sync below only runs after the reader screen has recomposed, which takes tens of
 * milliseconds on slow devices. A lookup calls [send] instead of updating the popup state and keeps
 * the popup in [pending]; the state follows once the page has revealed the popup. The composed
 * sync skips a stack that is identical to the last one sent.
 */
internal class ReaderLookupPopupDirectSync {
    /**
     * Builds the stack JSON from the latest composition's settings. Set after every composition. The
     * third argument says that the page has not been sent the root popup yet, so its first entry goes
     * along even when the highlight is already known. The last one tells the page to build the root
     * popup without showing it.
     */
    var buildJson: ((List<LookupPopupItem>, ReaderRootSelectionHighlight?, Boolean, Boolean) -> String)? = null

    /** The root popup the page already shows while the screen state has not caught up. Main thread only. */
    var pending: PendingRootLookup? = null

    /** The root popup the page has built out of sight for a touch that may become a tap. Main thread only. */
    var prepared: PreparedRootLookup? = null

    /** Counts touches; a prepared selection from the page counts only for the touch it names. */
    var prepareToken = 0
    private var lastWebView: WebView? = null
    private var lastJson: String? = null

    fun send(
        webView: WebView?,
        popups: List<LookupPopupItem>,
        rootHighlight: ReaderRootSelectionHighlight?,
        rootPopupIsNew: Boolean = false,
        revealHeld: Boolean = false,
    ) {
        val json = buildJson?.invoke(popups, rootHighlight, rootPopupIsNew, revealHeld) ?: return
        sendJson(webView ?: return, json)
    }

    fun sendJson(webView: WebView, json: String) {
        if (lastWebView === webView && lastJson == json) return
        lastWebView = webView
        lastJson = json
        webView.evaluateJavascript(
            "(function(){var payload=$json;" +
                "if(window.hoshiReaderPopupHost){window.hoshiReaderPopupHost.renderStack(payload);}" +
                "else{window.__hoshiPendingReaderPopupStack=payload;}})();",
            null,
        )
    }

    /** Drops the reference to a WebView that is going away. */
    fun forget(webView: WebView?) {
        if (lastWebView === webView) {
            lastWebView = null
            lastJson = null
        }
    }
}

@Composable
internal fun ReaderLookupPopupIframeSync(
    webView: WebView?,
    payloads: List<ReaderLookupPopupFramePayload>,
    rootHighlight: ReaderLookupPopupRootHighlightPayload?,
    directSync: ReaderLookupPopupDirectSync = remember { ReaderLookupPopupDirectSync() },
) {
    val payloadJson = remember(payloads, rootHighlight) {
        ReaderLookupPopupStackPayload(
            popups = payloads,
            rootHighlight = rootHighlight,
        ).toJson()
    }
    LaunchedEffect(webView, payloadJson) {
        webView?.let { directSync.sendJson(it, payloadJson) }
    }
    DisposableEffect(webView) {
        onDispose { directSync.forget(webView) }
    }
}
