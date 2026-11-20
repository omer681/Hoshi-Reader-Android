package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSelectionBridgeTest {
    @Test
    fun parsesTextSelectionPayloadLikeIosMessageBody() {
        val payload = """
            {
                "text": "食べる",
                "sentence": "私は食べる。",
                "rect": {
                    "x": 12.5,
                    "y": 24.25,
                    "width": 40.0,
                    "height": 18.0
                },
                "normalizedOffset": 42,
                "futureField": "ignored"
            }
        """.trimIndent()

        assertEquals(
            ReaderSelectionData(
                text = "食べる",
                sentence = "私は食べる。",
                rect = ReaderSelectionRect(
                    x = 12.5,
                    y = 24.25,
                    width = 40.0,
                    height = 18.0,
                ),
                normalizedOffset = 42,
            ),
            ReaderSelectionBridgePayload.fromJson(payload),
        )
    }

    @Test
    fun givesTheHighlightRectanglesThePageSentForEachLength() {
        val payload = """
            {
                "text": "食べる",
                "sentence": "私は食べる。",
                "rect": { "x": 1.0, "y": 2.0, "width": 3.0, "height": 4.0 },
                "rectsByLength": [
                    [{ "x": 1.0, "y": 2.0, "width": 3.0, "height": 4.0 }],
                    [{ "x": 1.0, "y": 2.0, "width": 3.0, "height": 8.0 }]
                ]
            }
        """.trimIndent()
        val selected = ReaderSelectionBridgePayload.parse(payload)!!

        assertEquals(emptyList<ReaderSelectionRect>(), selected.rects(0))
        assertEquals(listOf(ReaderSelectionRect(x = 1.0, y = 2.0, width = 3.0, height = 4.0)), selected.rects(1))
        assertEquals(listOf(ReaderSelectionRect(x = 1.0, y = 2.0, width = 3.0, height = 8.0)), selected.rects(2))
        assertEquals(selected.rects(2), selected.rects(5))
    }

    @Test
    fun givesNoHighlightRectanglesWhenThePageSentNone() {
        val payload = """{"text": "食", "sentence": "食", "rect": { "x": 1.0, "y": 2.0, "width": 3.0, "height": 4.0 }}"""

        assertEquals(emptyList<ReaderSelectionRect>(), ReaderSelectionBridgePayload.parse(payload)!!.rects(1))
        assertEquals(null, ReaderSelectionBridgePayload.parse(payload)!!.token)
    }

    @Test
    fun readsTheTouchAPreparedSelectionBelongsTo() {
        val payload = """{"text": "食", "sentence": "食", "rect": { "x": 1.0, "y": 2.0, "width": 3.0, "height": 4.0 }, "token": 12}"""

        assertEquals(12, ReaderSelectionBridgePayload.parse(payload)!!.token)
    }
}
