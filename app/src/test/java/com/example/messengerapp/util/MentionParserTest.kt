package com.example.messengerapp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MentionParserTest {

    private val memberNames = listOf("自分", "相手", "相手A")

    // --- findMentions ---

    @Test
    fun `単一のメンションを検出できる`() {
        val text = "こんにちは @相手 さん"
        val result = MentionParser.findMentions(text, memberNames)

        assertEquals(1, result.size)
        val expectedStart = text.indexOf("@相手")
        assertEquals(MentionRange(expectedStart, expectedStart + 3, "相手"), result[0])
    }

    @Test
    fun `複数のメンションを検出できる`() {
        val text = "@自分 @相手 テスト"
        val result = MentionParser.findMentions(text, memberNames)

        assertEquals(2, result.size)
        assertEquals(MentionRange(0, 3, "自分"), result[0])
        assertEquals(MentionRange(4, 7, "相手"), result[1])
    }

    @Test
    fun `メンションがなければ空リストを返す`() {
        assertTrue(MentionParser.findMentions("メンションなし", memberNames).isEmpty())
    }

    @Test
    fun `自分の名前へのメンションを検出できる`() {
        val result = MentionParser.findMentions("@自分", memberNames)

        assertEquals(1, result.size)
        assertEquals("自分", result[0].memberName)
    }

    @Test
    fun `メンバーに存在しない名前は検出しない`() {
        assertTrue(MentionParser.findMentions("@unknown さん", memberNames).isEmpty())
    }

    @Test
    fun `前方一致で重複する名前は最長一致を優先する`() {
        val result = MentionParser.findMentions("@相手A こんにちは", memberNames)

        assertEquals(1, result.size)
        assertEquals("相手A", result[0].memberName)
        assertEquals(MentionRange(0, 4, "相手A"), result[0])
    }

    @Test
    fun `文末のアットマーク単体や空文字では検出しない`() {
        assertTrue(MentionParser.findMentions("テスト@", memberNames).isEmpty())
        assertTrue(MentionParser.findMentions("", memberNames).isEmpty())
    }

    @Test
    fun `メンバー一覧が空なら検出しない`() {
        assertTrue(MentionParser.findMentions("@相手", emptyList()).isEmpty())
    }

    // --- findActiveMentionQuery ---

    @Test
    fun `アットマーク直後は空クエリを返す`() {
        val result = MentionParser.findActiveMentionQuery("@", cursorPosition = 1)

        assertEquals(MentionQuery(start = 0, query = ""), result)
    }

    @Test
    fun `入力途中のクエリを返す`() {
        val text = "こんにちは @相"
        val result = MentionParser.findActiveMentionQuery(text, cursorPosition = text.length)

        assertEquals(MentionQuery(start = 6, query = "相"), result)
    }

    @Test
    fun `アットマークとカーソルの間に空白があれば null`() {
        val text = "@相手 "
        assertNull(MentionParser.findActiveMentionQuery(text, cursorPosition = text.length))
    }

    @Test
    fun `アットマークがなければ null`() {
        assertNull(MentionParser.findActiveMentionQuery("abc", cursorPosition = 3))
    }

    @Test
    fun `カーソルが先頭なら null`() {
        assertNull(MentionParser.findActiveMentionQuery("@相手", cursorPosition = 0))
    }

    // --- completeMention ---

    @Test
    fun `クエリ部分をメンションと末尾空白に置換しカーソルを直後に移す`() {
        val text = "こんにちは @相"
        val query = MentionQuery(start = 6, query = "相")

        val (newText, newCursor) = MentionParser.completeMention(text, query, "相手")

        assertEquals("こんにちは @相手 ", newText)
        assertEquals(10, newCursor)
    }

    @Test
    fun `文中のクエリを補完しても後続テキストが保持される`() {
        val text = "@相 です"
        val query = MentionQuery(start = 0, query = "相")

        val (newText, newCursor) = MentionParser.completeMention(text, query, "相手A")

        assertEquals("@相手A  です", newText)
        assertEquals(5, newCursor)
    }
}
