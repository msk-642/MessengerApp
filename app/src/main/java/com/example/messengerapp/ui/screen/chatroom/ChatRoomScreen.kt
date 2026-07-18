package com.example.messengerapp.ui.screen.chatroom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.messengerapp.domain.model.ChatMessage
import com.example.messengerapp.domain.model.MessageType
import com.example.messengerapp.ui.screen.chatroom.camera.CameraCaptureScreen
import com.example.messengerapp.ui.screen.chatroom.camera.PhotoPreviewScreen
import com.example.messengerapp.ui.screen.chatroom.imageviewer.ImageViewerScreen
import com.example.messengerapp.util.ImageMessageCodec
import com.example.messengerapp.util.MentionParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * チャットルーム配下のルート Composable。
 * 表示中画面状態(ChatRoomDisplayState)に応じて
 * チャット / カメラ / 撮影結果確認 の各画面を切り替える。
 */
@Composable
fun ChatRoomScreen(
    roomId: String,
    onNavigateBack: () -> Unit,
    viewModel: ChatRoomViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val displayState = uiState.displayState) {
        ChatRoomDisplayState.Chat -> ChatRoomContent(
            uiState = uiState,
            onNavigateBack = onNavigateBack,
            onSendMessage = viewModel::sendMessage,
            onLoadOlderMessages = viewModel::loadOlderMessages,
            onOpenCamera = viewModel::openCamera,
            onImageClick = viewModel::openImageViewer
        )

        ChatRoomDisplayState.Camera -> CameraCaptureScreen(
            cameraSettings = uiState.cameraSettings,
            onSettingsChange = viewModel::updateCameraSettings,
            onPhotoCaptured = viewModel::onPhotoCaptured,
            onClose = viewModel::closeCamera
        )

        ChatRoomDisplayState.PhotoPreview -> PhotoPreviewScreen(
            photoJpeg = uiState.capturedPhotoJpeg,
            isSending = uiState.isSending,
            onBack = viewModel::backToCamera,
            onClose = viewModel::closeCamera,
            onSend = viewModel::sendPhotoMessage
        )

        is ChatRoomDisplayState.ImageViewer -> ImageViewerScreen(
            imageBase64 = displayState.imageBase64,
            onClose = viewModel::closeImageViewer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoomContent(
    uiState: ChatRoomUiState,
    onNavigateBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onLoadOlderMessages: () -> Unit,
    onOpenCamera: () -> Unit,
    onImageClick: (ChatMessage) -> Unit
) {
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val listItems = uiState.listItems

    // 最後尾のアイテム(常に通常メッセージ)が変わった時のみ最下部へスクロールする
    // (過去メッセージの先頭追加ではスクロール位置を維持する)
    LaunchedEffect(listItems.lastOrNull()?.key) {
        if (listItems.isNotEmpty()) {
            listState.animateScrollToItem(listItems.lastIndex)
        }
    }

    Scaffold(
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { focusManager.clearFocus() })
        },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                    Text(
                        text = "MessengerApp.Chat_Room",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                HorizontalDivider()
            }
        },
        bottomBar = {
            // 入力途中のメンションクエリと、それに前方一致するサジェスト候補
            val mentionQuery = MentionParser.findActiveMentionQuery(
                text = inputText.text,
                cursorPosition = inputText.selection.end
            )
            val mentionSuggestions = if (mentionQuery != null) {
                uiState.memberNames.filter { it.startsWith(mentionQuery.query) }
            } else {
                emptyList()
            }

            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    if (mentionQuery != null && mentionSuggestions.isNotEmpty()) {
                        MentionSuggestionList(
                            suggestions = mentionSuggestions,
                            onSelect = { memberName ->
                                val (newText, newCursor) = MentionParser.completeMention(
                                    text = inputText.text,
                                    query = mentionQuery,
                                    memberName = memberName
                                )
                                inputText = TextFieldValue(newText, TextRange(newCursor))
                            }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onOpenCamera) {
                            Icon(
                                Icons.Outlined.PhotoCamera,
                                contentDescription = "カメラを起動",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("メッセージを入力") },
                            shape = RoundedCornerShape(28.dp),
                            maxLines = 4,
                            visualTransformation = remember(uiState.memberNames) {
                                MentionVisualTransformation(uiState.memberNames)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        FloatingActionButton(
                            onClick = {
                                if (inputText.text.isNotBlank()) {
                                    focusManager.clearFocus()
                                    onSendMessage(inputText.text)
                                    inputText = TextFieldValue("")
                                }
                            },
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            containerColor = if (inputText.text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (inputText.text.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "送信",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.05f)
                        )
                    )
                )
        ) {
            PullToRefreshBox(
                isRefreshing = uiState.isLoadingOlder,
                onRefresh = onLoadOlderMessages,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(
                        items = listItems,
                        key = { it.key }
                    ) { item ->
                        when (item) {
                            is ChatRoomListItem.DateSeparator -> DateSeparatorItem(item.label)
                            is ChatRoomListItem.UnreadBoundary -> UnreadBoundaryItem()
                            is ChatRoomListItem.Message -> MessageBubble(
                                message = item.message,
                                isMyMessage = item.message.senderId == uiState.myUserId,
                                memberNames = uiState.memberNames,
                                myUserName = uiState.myUserName,
                                onImageClick = onImageClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSeparatorItem(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UnreadBoundaryItem() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Text(
            text = "ここから未読",
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    }
}

/** メンションサジェストの候補一覧（入力欄の上に表示） */
@Composable
private fun MentionSuggestionList(
    suggestions: List<String>,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        suggestions.forEach { memberName ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(memberName) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "@$memberName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MentionColors.Member
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMyMessage: Boolean,
    memberNames: List<String>,
    myUserName: String,
    onImageClick: (ChatMessage) -> Unit
) {
    val horizontalArrangement = if (isMyMessage) Arrangement.End else Arrangement.Start
    val bubbleColor = if (isMyMessage)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMyMessage)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant
    val bubbleShape = if (isMyMessage)
        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
    else
        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)

    // 自分がメンションされているメッセージはバブルを別色で縁取る
    val isSelfMentioned = message.messageType == MessageType.TEXT &&
        containsSelfMention(message.body, memberNames, myUserName)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement
    ) {
        Column(
            horizontalAlignment = if (isMyMessage) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (!isMyMessage) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }
            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                border = if (isSelfMentioned) BorderStroke(2.dp, MentionColors.Self) else null
            ) {
                when (message.messageType) {
                    MessageType.IMAGE -> ImageMessageContent(
                        message = message,
                        textColor = textColor,
                        onClick = { onImageClick(message) }
                    )
                    MessageType.TEXT -> Text(
                        text = buildMentionAnnotatedString(
                            body = message.body,
                            memberNames = memberNames,
                            myUserName = myUserName,
                            isMyMessage = isMyMessage
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.sentAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

/**
 * 写真メッセージの表示。
 * メッセージが保持する Base64 データをメモリ上でデコードして表示する（ディスクキャッシュなし）。
 */
@Composable
private fun ImageMessageContent(
    message: ChatMessage,
    textColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val imageBitmap = remember(message.messageId) {
        message.imageBase64?.let { ImageMessageCodec.decodeBase64ToImageBitmap(it) }
    }
    if (imageBitmap != null) {
        val aspectRatio = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()
        Image(
            bitmap = imageBitmap,
            contentDescription = "写真メッセージ",
            modifier = Modifier
                .width(220.dp)
                .aspectRatio(aspectRatio)
                .clickable(onClick = onClick),
            contentScale = ContentScale.Fit
        )
    } else {
        Text(
            text = "画像を表示できません",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = textColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
