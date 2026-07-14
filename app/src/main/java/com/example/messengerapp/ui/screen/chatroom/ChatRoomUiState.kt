package com.example.messengerapp.ui.screen.chatroom

/** チャットルーム配下で表示中の画面状態 */
sealed interface ChatRoomDisplayState {

    /** メッセージ一覧（チャット） */
    data object Chat : ChatRoomDisplayState

    /** カメラ撮影画面 */
    data object Camera : ChatRoomDisplayState

    /** 撮影結果確認画面 */
    data object PhotoPreview : ChatRoomDisplayState
}

/** カメラ画面の設定状態（撮影 → 確認 → 戻る のあいだ保持される） */
data class CameraSettings(
    val zoomRatio: Float = 1f,
    val isFlashOn: Boolean = false,
    val exposureIndex: Int = 0
)

/** チャットルーム画面の UI 状態 */
@Suppress("ArrayInDataClass")
data class ChatRoomUiState(
    /** 日付区切り・未読境界を含む表示用リスト */
    val listItems: List<ChatRoomListItem> = emptyList(),
    val myUserId: String = "",
    val myUserName: String = "",
    /** ルーム参加メンバーの名前一覧（メンション判定用） */
    val memberNames: List<String> = emptyList(),
    val isSending: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val displayState: ChatRoomDisplayState = ChatRoomDisplayState.Chat,
    val cameraSettings: CameraSettings = CameraSettings(),
    /** 撮影した JPEG（メモリ保持のみ。ストレージには保存しない。参照同一性での比較で十分） */
    val capturedPhotoJpeg: ByteArray? = null
)
