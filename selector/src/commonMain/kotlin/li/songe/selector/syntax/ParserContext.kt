package li.songe.selector.syntax

import li.songe.selector.SelectorPositionKind

internal class ParserContext(
    source: String,
    private val positionRecorder: PositionRecorder?,
) {
    val cursor = ParserCursor(source)

    inline fun <T : Any> positioned(
        kind: SelectorPositionKind,
        block: () -> T,
    ): T {
        val start = cursor.index
        return block().also { value ->
            record(value, kind, start)
        }
    }

    fun record(
        value: Any,
        kind: SelectorPositionKind,
        start: Int,
        end: Int = cursor.index,
    ) {
        positionRecorder?.record(value, kind, start, end)
    }

    fun recordPosition(
        kind: SelectorPositionKind,
        start: Int,
        end: Int = cursor.index,
    ) {
        positionRecorder?.recordPosition(kind, start, end)
    }
}
