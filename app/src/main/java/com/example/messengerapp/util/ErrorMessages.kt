package com.example.messengerapp.util

import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Repository が返す [Result] の失敗を、画面に出せる日本語メッセージへ変換する。
 *
 * 通信失敗時にモックへ差し替える処理は撤去したため、失敗はここを通って必ずユーザーに見える。
 */
object ErrorMessages {

    fun from(throwable: Throwable): String = when (throwable) {
        is ConnectException,
        is UnknownHostException,
        is SocketTimeoutException ->
            "サーバに接続できません。ローカルサーバが起動しているか確認してください。"

        is HttpException -> when (throwable.code()) {
            401 -> "認証の有効期限が切れました。サインインし直してください。"
            404 -> "対象が見つかりませんでした。"
            in 500..599 -> "サーバでエラーが発生しました（${throwable.code()}）"
            else -> "リクエストが受け付けられませんでした（${throwable.code()}）"
        }

        // IOException の分岐より前に置く（AppUpdateResultCodeException は IOException のため）
        is AppUpdateResultCodeException ->
            "サーバがエラーを返しました（コード: ${throwable.resultCode}）"

        is IOException -> "通信に失敗しました。ネットワーク状態を確認してください。"

        else -> "予期しないエラーが発生しました。"
    }
}
