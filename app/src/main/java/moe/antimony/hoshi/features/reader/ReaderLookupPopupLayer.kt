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
            includeInitialEntryJson = shouldSendInitialEntryJson(
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

/**
 * Sends a popup stack to the reader page as soon as it is known.
 *
 * The composed sync below only runs after the reader screen has recomposed, which takes tens of
 * milliseconds on slow devices. A lookup calls [send] right after it has updated the popup state,
 * and the composed sync then skips the stack it would otherwise send a second time.
 */
/** A looked-up root popup and its highlight, as last sent to the page. */
internal class PendingRootLookup(var popup: LookupPopupItem, var highlight: ReaderRootSelectionHighlight)

internal class ReaderLookupPopupDirectSync {
    /** Builds the stack JSON from the latest composition's settings. Set after every composition. */
    var buildJson: ((List<LookupPopupItem>, ReaderRootSelectionHighlight?) -> String)? = null

    /** The root popup the page already shows while the screen state has not caught up. Main thread only. */
    var pending: PendingRootLookup? = null
    private var lastWebView: WebView? = null
    private var lastJson: String? = null

    fun send(webView: WebView?, popups: List<LookupPopupItem>, rootHighlight: ReaderRootSelectionHighlight?) {
        val json = buildJson?.invoke(popups, rootHighlight) ?: return
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
