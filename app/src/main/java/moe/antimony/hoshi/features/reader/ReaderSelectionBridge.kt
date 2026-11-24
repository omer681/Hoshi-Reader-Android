package moe.antimony.hoshi.features.reader

import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class ReaderSelectionBridge(
    private val webView: WebView,
    private val onTextPrepared: (ReaderSelectionMessage) -> Unit = {},
    private val onTextSelected: (ReaderSelectionMessage) -> Unit,
) {
    /** What a tap would select at the point a finger has just touched; nothing is selected yet. */
    @JavascriptInterface
    fun postPrepared(message: String) {
        val prepared = ReaderSelectionBridgePayload.parse(message) ?: return
        webView.post { onTextPrepared(prepared) }
    }

    @JavascriptInterface
    fun postMessage(message: String) {
        val selected = ReaderSelectionBridgePayload.parse(message) ?: return
        webView.post { onTextSelected(selected) }
    }
}

/** A selection as the page reports it, with the rectangles that cover each number of its characters. */
internal class ReaderSelectionMessage(
    val selection: ReaderSelectionData,
    private val rectsByLength: List<List<ReaderSelectionRect>>,
    /** Names the touch a prepared selection belongs to; null for a selection made by a tap. */
    val token: Int? = null,
) {
    /** The rectangles of the first [count] characters. */
    fun rects(count: Int): List<ReaderSelectionRect> =
        if (count <= 0 || rectsByLength.isEmpty()) emptyList() else rectsByLength[minOf(count, rectsByLength.size) - 1]
}

internal object ReaderSelectionBridgePayload {
    private val json = Json { ignoreUnknownKeys = true }

    fun fromJson(message: String): ReaderSelectionData? = parse(message)?.selection

    fun parse(message: String): ReaderSelectionMessage? {
        val payload = runCatching { json.decodeFromString<Payload>(message) }.getOrNull() ?: return null
        return ReaderSelectionMessage(
            selection = ReaderSelectionData(
                text = payload.text,
                sentence = payload.sentence,
                rect = payload.rect.toSelectionRect(),
                normalizedOffset = payload.normalizedOffset,
                sentenceOffset = payload.sentenceOffset,
                chapterIndex = payload.chapterIndex,
            ),
            rectsByLength = payload.rectsByLength.map { rects -> rects.map(Rect::toSelectionRect) },
            token = payload.token,
        )
    }

    @Serializable
    private data class Payload(
        val text: String,
        val sentence: String,
        val rect: Rect,
        val normalizedOffset: Int? = null,
        val sentenceOffset: Int? = null,
        val chapterIndex: Int? = null,
        val rectsByLength: List<List<Rect>> = emptyList(),
        val token: Int? = null,
    )

    @Serializable
    private data class Rect(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
    ) {
        fun toSelectionRect() = ReaderSelectionRect(x = x, y = y, width = width, height = height)
    }
}

@Serializable
internal data class ReaderNativeSelectionMenuRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
    val viewportWidth: Double,
) {
    companion object {
        fun fromJavascriptResult(result: String?): ReaderNativeSelectionMenuRect? =
            runCatching { Json.decodeFromString<ReaderNativeSelectionMenuRect>(result.orEmpty()) }
                .getOrNull()?.takeIf { rect ->
                    listOf(rect.left, rect.top, rect.right, rect.bottom, rect.viewportWidth).all { it.isFinite() } &&
                        rect.viewportWidth > 0 && rect.right > rect.left && rect.bottom > rect.top
                }
    }
}

@Serializable
internal data class ReaderNativeSelectionMenuPoint(val x: Double, val y: Double, val viewportWidth: Double) {
    companion object {
        fun fromJavascriptResult(result: String?): ReaderNativeSelectionMenuPoint? =
            runCatching { Json.decodeFromString<ReaderNativeSelectionMenuPoint>(result.orEmpty()) }
                .getOrNull()?.takeIf {
                    it.x.isFinite() && it.y.isFinite() && it.viewportWidth.isFinite() &&
                        it.viewportWidth > 0 && it.x >= 0 && it.x < it.viewportWidth && it.y >= 0
                }
    }
}
