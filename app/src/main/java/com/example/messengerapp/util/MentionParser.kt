package com.example.messengerapp.util

/** text 中の「@+メンバー名」1件分の出現範囲 */
data class MentionRange(
    /** '@' の位置（含む） */
    val start: Int,
    /** メンション終端（排他） */
    val end: Int,
    /** マッチしたメンバー名 */
    val memberName: String
)

/** 入力途中のメンションクエリ（サジェスト表示用） */
data class MentionQuery(
    /** '@' の位置 */
    val start: Int,
    /** '@' からカーソルまでの入力途中文字列（'@' を除く） */
    val query: String
)

/**
 * メンション文字列の解析ロジック。
 * Compose 非依存の純 Kotlin として分離し、ユニットテスト可能にする。
 */
object MentionParser {

    /**
     * text 中の「@+メンバー名」の出現範囲をすべて返す。
     * ・複数メンション対応（左から走査、非重複）
     * ・名前が前方一致で重複する場合（例:「相手」「相手A」）は最長一致を優先
     */
    fun findMentions(text: String, memberNames: List<String>): List<MentionRange> {
        if (text.isEmpty() || memberNames.isEmpty()) return emptyList()
        val namesByLengthDesc = memberNames
            .filter { it.isNotEmpty() }
            .sortedByDescending { it.length }

        val result = mutableListOf<MentionRange>()
        var index = 0
        while (index < text.length) {
            if (text[index] == '@') {
                val matched = namesByLengthDesc.firstOrNull { name ->
                    text.startsWith(name, index + 1)
                }
                if (matched != null) {
                    val end = index + 1 + matched.length
                    result += MentionRange(start = index, end = end, memberName = matched)
                    index = end
                    continue
                }
            }
            index++
        }
        return result
    }

    /**
     * カーソル位置で入力途中のメンションクエリを返す。
     * カーソルから左へ走査して '@' を探し、間に空白・改行があれば null（メンション入力中ではない）。
     */
    fun findActiveMentionQuery(text: String, cursorPosition: Int): MentionQuery? {
        if (cursorPosition <= 0 || cursorPosition > text.length) return null
        var index = cursorPosition - 1
        while (index >= 0) {
            val char = text[index]
            when {
                char == '@' -> return MentionQuery(
                    start = index,
                    query = text.substring(index + 1, cursorPosition)
                )
                char.isWhitespace() -> return null
            }
            index--
        }
        return null
    }

    /**
     * サジェスト選択時の補完。
     * クエリ部分（'@'〜カーソル）を「@メンバー名␣」に置換した新しいテキストと
     * 新しいカーソル位置を返す。
     */
    fun completeMention(
        text: String,
        query: MentionQuery,
        memberName: String
    ): Pair<String, Int> {
        val replacement = "@$memberName "
        val queryEnd = query.start + 1 + query.query.length
        val newText = text.substring(0, query.start) + replacement + text.substring(queryEnd)
        val newCursor = query.start + replacement.length
        return newText to newCursor
    }
}
