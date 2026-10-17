package com.example.messengerapp.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming

/**
 * アプリ自身の更新用 API。
 *
 * 他の API と異なり認証を要求しない。サインイン前やトークン失効時こそ
 * アップデートが必要になりうるため、どの状態からでも叩けるようにしている。
 */
interface UpdateApi {

    /**
     * 最新 APK を含むレスポンス。
     *
     * 本文は `{"resultCode":"0","apkBase64":"..."}` で、apkBase64 が 100MB 級になる。
     * コンバータに渡すとメモリへ展開されてしまうため、[Streaming] で [ResponseBody] のまま受け取り、
     * [com.example.messengerapp.util.AppUpdateResponseReader] がストリームのまま解釈する。
     */
    @Streaming
    @GET("api/v1/app/latest")
    suspend fun downloadLatestApk(): ResponseBody
}
