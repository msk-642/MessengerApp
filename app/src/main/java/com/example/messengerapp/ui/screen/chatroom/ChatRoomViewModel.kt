package com.example.messengerapp.ui.screen.chatroom

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messengerapp.domain.model.ChatMessage
import com.example.messengerapp.domain.model.MessageType
import com.example.messengerapp.domain.repository.AuthRepository
import com.example.messengerapp.domain.repository.ChatRepository
import com.example.messengerapp.util.ImageMessageCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val roomId: String = checkNotNull(savedStateHandle["roomId"])

    private val _uiState = MutableStateFlow(ChatRoomUiState())
    val uiState: StateFlow<ChatRoomUiState> = _uiState.asStateFlow()

    /** メッセージ一覧の生データ（昇順）。listItems の導出元 */
    private var messages: List<ChatMessage> = emptyList()

    /** 既読日時（サーバがルーム×ユーザ単位で保有）。null は未取得 */
    private var lastReadAt: Long? = null

    init {
        loadSession()
        loadReadState()
        loadMessages()
        loadMembers()
    }

    private fun loadSession() {
        viewModelScope.launch {
            val session = authRepository.getSession()
            _uiState.update {
                it.copy(
                    myUserId = session.userId ?: "",
                    myUserName = session.userName ?: ""
                )
            }
            rebuildListItems()
        }
    }

    /** メンション判定用にルーム参加メンバーの名前一覧を取得する */
    private fun loadMembers() {
        viewModelScope.launch {
            val members = chatRepository.getRoomMembers(roomId)
            _uiState.update { it.copy(memberNames = members.map { member -> member.userName }) }
        }
    }

    private fun loadReadState() {
        viewModelScope.launch {
            lastReadAt = chatRepository.getLastReadAt(roomId)
            rebuildListItems()
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            messages = chatRepository.getMessages(roomId)
            rebuildListItems()
        }
    }

    /** messages / lastReadAt / myUserId の変更を表示用リストへ反映する */
    private fun rebuildListItems() {
        _uiState.update {
            it.copy(listItems = buildListItems(messages, lastReadAt, it.myUserId))
        }
    }

    /**
     * 昇順のメッセージ一覧から表示用リストを組み立てる。
     * 同一位置での順序は 日付区切り → 未読境界 → 通常メッセージ。
     * 毎回全件から導出するため、過去メッセージの先頭追加時も区切り位置が正しく再配置される。
     */
    private fun buildListItems(
        messages: List<ChatMessage>,
        lastReadAt: Long?,
        myUserId: String
    ): List<ChatRoomListItem> {
        val dateKeyFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val dateLabelFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val items = mutableListOf<ChatRoomListItem>()
        var prevDateKey: String? = null
        var unreadBoundaryInserted = false

        for (message in messages) {
            val dateKey = dateKeyFormat.format(Date(message.sentAt))
            if (dateKey != prevDateKey) {
                items += ChatRoomListItem.DateSeparator(
                    label = dateLabelFormat.format(Date(message.sentAt)),
                    key = "date_$dateKey"
                )
                prevDateKey = dateKey
            }
            // 自分の送信メッセージは未読境界の判定から除外する
            if (!unreadBoundaryInserted && lastReadAt != null &&
                message.senderId != myUserId && message.sentAt > lastReadAt
            ) {
                items += ChatRoomListItem.UnreadBoundary
                unreadBoundaryInserted = true
            }
            items += ChatRoomListItem.Message(message)
        }
        return items
    }

    fun loadOlderMessages() {
        if (_uiState.value.isLoadingOlder) return
        val oldest = messages.firstOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOlder = true) }
            val startedAt = System.currentTimeMillis()
            try {
                val older = chatRepository.getMessagesBefore(
                    roomId = roomId,
                    beforeMessageId = oldest.messageId,
                    beforeSentAt = oldest.sentAt
                )
                messages = (older + messages)
                    .distinctBy { it.messageId }
                    .sortedBy { it.sentAt }
                rebuildListItems()
            } finally {
                // 取得が一瞬で終わると UI 側が true への遷移を観測できず
                // インジケータが消えなくなるため、最低表示時間を確保する
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed < MIN_REFRESH_INDICATOR_MILLIS) {
                    delay(MIN_REFRESH_INDICATOR_MILLIS - elapsed)
                }
                _uiState.update { it.copy(isLoadingOlder = false) }
            }
        }
    }

    fun sendMessage(body: String) {
        if (body.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val sent = chatRepository.sendMessage(roomId, body)
            messages = messages + sent
            rebuildListItems()
            _uiState.update { it.copy(isSending = false) }
        }
    }

    // --- カメラ・写真メッセージ ---

    /** チャット画面からカメラ画面を開く */
    fun openCamera() {
        _uiState.update { it.copy(displayState = ChatRoomDisplayState.Camera) }
    }

    /** カメラ画面を閉じてチャット画面へ戻る */
    fun closeCamera() {
        _uiState.update {
            it.copy(displayState = ChatRoomDisplayState.Chat, capturedPhotoJpeg = null)
        }
    }

    /** 撮影完了。撮影データをメモリ保持して撮影結果確認画面へ */
    fun onPhotoCaptured(jpegBytes: ByteArray) {
        _uiState.update {
            it.copy(
                displayState = ChatRoomDisplayState.PhotoPreview,
                capturedPhotoJpeg = jpegBytes
            )
        }
    }

    /** 撮影結果確認画面から、カメラ設定を維持したままカメラ画面へ戻る */
    fun backToCamera() {
        _uiState.update {
            it.copy(displayState = ChatRoomDisplayState.Camera, capturedPhotoJpeg = null)
        }
    }

    /** カメラ画面での設定変更（ズーム・フラッシュ・露光）を保持する */
    fun updateCameraSettings(settings: CameraSettings) {
        _uiState.update { it.copy(cameraSettings = settings) }
    }

    /** 写真メッセージをタップして拡大表示を開く */
    fun openImageViewer(message: ChatMessage) {
        val imageBase64 = message.imageBase64
        if (message.messageType != MessageType.IMAGE || imageBase64 == null) return
        _uiState.update {
            it.copy(displayState = ChatRoomDisplayState.ImageViewer(imageBase64))
        }
    }

    /** 拡大表示を閉じてチャット画面へ戻る */
    fun closeImageViewer() {
        _uiState.update { it.copy(displayState = ChatRoomDisplayState.Chat) }
    }

    /** 撮影画像を送信形式(Base64)へ変換して写真メッセージとして送信する */
    fun sendPhotoMessage() {
        val jpeg = _uiState.value.capturedPhotoJpeg ?: return
        if (_uiState.value.isSending) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val imageBase64 = withContext(Dispatchers.Default) {
                ImageMessageCodec.encodeJpegBytesToBase64(jpeg)
            }
            val sent = chatRepository.sendImageMessage(roomId, imageBase64)
            messages = messages + sent
            rebuildListItems()
            _uiState.update {
                it.copy(
                    isSending = false,
                    displayState = ChatRoomDisplayState.Chat,
                    capturedPhotoJpeg = null
                )
            }
        }
    }

    companion object {
        private const val MIN_REFRESH_INDICATOR_MILLIS = 500L
    }
}
