package com.example.messengerapp.ui.screen.chatroom

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.messengerapp.util.MentionParser

/** メンションの表示色 */
object MentionColors {

    /** 他メンバーへのメンション（青） */
    val Member = Color(0xFF1E88E5)

    /** 自分へのメンション（オレンジ）。バブルの縁取りにも使用する */
    val Self = Color(0xFFFF8F00)
}

/**
 * メッセージ本文をメンション装飾付き AnnotatedString に変換する。
 * 自分の名前へのメンションはオレンジ、他メンバーへのメンションは青にする。
 *
 * @param isMyMessage 自分のメッセージか否か。現時点では装飾を変えないが、
 *                    将来メッセージの送信者ごとにメンションの機能を変える予定のため
 *                    判定として受け取る
 */
fun buildMentionAnnotatedString(
    body: String,
    memberNames: List<String>,
    myUserName: String,
    isMyMessage: Boolean
): AnnotatedString {
    val mentions = MentionParser.findMentions(body, memberNames)
    if (mentions.isEmpty()) return AnnotatedString(body)
    return buildAnnotatedString {
        append(body)
        mentions.forEach { mention ->
            val color = if (mention.memberName == myUserName) {
                MentionColors.Self
            } else {
                MentionColors.Member
            }
            addStyle(SpanStyle(color = color), mention.start, mention.end)
        }
    }
}

/** 自分へのメンションが含まれるか（バブル縁取りの判定用） */
fun containsSelfMention(
    body: String,
    memberNames: List<String>,
    myUserName: String
): Boolean {
    if (myUserName.isEmpty()) return false
    return MentionParser.findMentions(body, memberNames)
        .any { it.memberName == myUserName }
}

/**
 * テキストフィールド入力中の「@+メンバー名」を青色にする VisualTransformation。
 * 文字列自体は変換しないため OffsetMapping は Identity。
 */
class MentionVisualTransformation(
    private val memberNames: List<String>
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val mentions = MentionParser.findMentions(text.text, memberNames)
        if (mentions.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val annotated = buildAnnotatedString {
            append(text)
            mentions.forEach { mention ->
                addStyle(SpanStyle(color = MentionColors.Member), mention.start, mention.end)
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}
