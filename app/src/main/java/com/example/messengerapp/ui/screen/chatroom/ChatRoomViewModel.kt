package com.example.messengerapp.ui.screen.chatroom

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messengerapp.domain.model.ChatMessage
import com.example.messengerapp.domain.repository.AuthRepository
import com.example.messengerapp.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isLoadingOlder = MutableStateFlow(false)
    val isLoadingOlder: StateFlow<Boolean> = _isLoadingOlder.asStateFlow()

    private val _myUserId = MutableStateFlow("")
    val myUserId: StateFlow<String> = _myUserId.asStateFlow()

    /** 既読日時（サーバがルーム×ユーザ単位で保有）。null は未取得 */
    private val _lastReadAt = MutableStateFlow<Long?>(null)

    /** 日付区切り・未読境界を含む表示用リスト */
    val listItems: StateFlow<List<ChatRoomListItem>> =
        combine(_messages, _lastReadAt, _myUserId) { messages, lastReadAt, myUserId ->
            buildListItems(messages, lastReadAt, myUserId)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        loadSession()
        loadReadState()
        loadMessages()
    }

    private fun loadSession() {
        viewModelScope.launch {
            _myUserId.value = authRepository.getSession().userId ?: ""
        }
    }

    private fun loadReadState() {
        viewModelScope.launch {
            _lastReadAt.value = chatRepository.getLastReadAt(roomId)
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            _messages.value = chatRepository.getMessages(roomId)
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
        if (_isLoadingOlder.value) return
        val oldest = _messages.value.firstOrNull() ?: return
        viewModelScope.launch {
            _isLoadingOlder.value = true
            val startedAt = System.currentTimeMillis()
            try {
                val older = chatRepository.getMessagesBefore(
                    roomId = roomId,
                    beforeMessageId = oldest.messageId,
                    beforeSentAt = oldest.sentAt
                )
                _messages.value = (older + _messages.value)
                    .distinctBy { it.messageId }
                    .sortedBy { it.sentAt }
            } finally {
                // 取得が一瞬で終わると UI 側が true への遷移を観測できず
                // インジケータが消えなくなるため、最低表示時間を確保する
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed < MIN_REFRESH_INDICATOR_MILLIS) {
                    delay(MIN_REFRESH_INDICATOR_MILLIS - elapsed)
                }
                _isLoadingOlder.value = false
            }
        }
    }

    fun sendMessage(body: String) {
        if (body.isBlank()) return
        viewModelScope.launch {
            _isSending.value = true
            val sent = chatRepository.sendMessage(roomId, body)
            _messages.value += sent
            _isSending.value = false
        }
    }

    companion object {
        private const val MIN_REFRESH_INDICATOR_MILLIS = 500L
    }
}
