package com.example.messengerapp.ui.screen.chatroom.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.messengerapp.ui.screen.chatroom.CameraSettings
import com.example.messengerapp.util.ImageMessageCodec
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * カメラ撮影画面。
 * ImageCapture による撮影と、ズーム・フラッシュ・露光補正の操作を提供する。
 * 設定変更は都度 onSettingsChange で通知し、再表示時（撮影結果確認画面からの戻り等）は
 * cameraSettings の内容をバインド完了時に再適用する。
 */
@Composable
fun CameraCaptureScreen(
    cameraSettings: CameraSettings,
    onSettingsChange: (CameraSettings) -> Unit,
    onPhotoCaptured: (ByteArray) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler(onBack = onClose)

    if (hasCameraPermission) {
        CameraPreviewContent(
            cameraSettings = cameraSettings,
            onSettingsChange = onSettingsChange,
            onPhotoCaptured = onPhotoCaptured,
            onClose = onClose
        )
    } else {
        CameraPermissionRequestContent(
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onClose = onClose
        )
    }
}

@Composable
private fun CameraPermissionRequestContent(
    onRequestPermission: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopStart)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "閉じる", tint = Color.White)
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "写真を撮影するにはカメラの権限が必要です",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onRequestPermission) {
                Text("カメラを許可する")
            }
        }
    }
}

@Composable
private fun CameraPreviewContent(
    cameraSettings: CameraSettings,
    onSettingsChange: (CameraSettings) -> Unit,
    onPhotoCaptured: (ByteArray) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomRange by remember { mutableStateOf(1f..1f) }
    var exposureRange by remember { mutableStateOf(0..0) }
    var isCapturing by remember { mutableStateOf(false) }

    // カメラをバインドし、保持済みのカメラ設定を再適用する
    LaunchedEffect(Unit) {
        val provider = awaitCameraProvider(context)
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        provider.unbindAll()
        val boundCamera = provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture
        )
        camera = boundCamera

        boundCamera.cameraInfo.zoomState.value?.let { zoomState ->
            zoomRange = zoomState.minZoomRatio..zoomState.maxZoomRatio
        }
        val exposureState = boundCamera.cameraInfo.exposureState
        if (exposureState.isExposureCompensationSupported) {
            val range = exposureState.exposureCompensationRange
            exposureRange = range.lower..range.upper
        }

        boundCamera.cameraControl.setZoomRatio(
            cameraSettings.zoomRatio.coerceIn(zoomRange.start, zoomRange.endInclusive)
        )
        if (exposureState.isExposureCompensationSupported) {
            boundCamera.cameraControl.setExposureCompensationIndex(
                cameraSettings.exposureIndex.coerceIn(exposureRange.first, exposureRange.last)
            )
        }
        imageCapture.flashMode =
            if (cameraSettings.isFlashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    // 画面切替（撮影結果確認への遷移等）でコンポジションから外れたらカメラを解放する
    DisposableEffect(Unit) {
        onDispose {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // 上部バー: 閉じる / フラッシュ切替
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "閉じる", tint = Color.White)
            }
            IconButton(
                onClick = {
                    val turnedOn = !cameraSettings.isFlashOn
                    imageCapture.flashMode =
                        if (turnedOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                    onSettingsChange(cameraSettings.copy(isFlashOn = turnedOn))
                }
            ) {
                Icon(
                    imageVector = if (cameraSettings.isFlashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = if (cameraSettings.isFlashOn) "フラッシュ ON" else "フラッシュ OFF",
                    tint = if (cameraSettings.isFlashOn) Color.Yellow else Color.White
                )
            }
        }

        // 下部: ズーム / 露光 スライダーとシャッターボタン
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.4f))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CameraSettingSlider(
                icon = Icons.Filled.ZoomIn,
                label = "ズーム",
                value = cameraSettings.zoomRatio,
                valueRange = zoomRange,
                enabled = zoomRange.endInclusive > zoomRange.start,
                onValueChange = { value ->
                    camera?.cameraControl?.setZoomRatio(value)
                    onSettingsChange(cameraSettings.copy(zoomRatio = value))
                }
            )
            CameraSettingSlider(
                icon = Icons.Filled.Exposure,
                label = "露光補正",
                value = cameraSettings.exposureIndex.toFloat(),
                valueRange = exposureRange.first.toFloat()..exposureRange.last.toFloat(),
                enabled = exposureRange.last > exposureRange.first,
                onValueChange = { value ->
                    val index = value.toInt()
                    camera?.cameraControl?.setExposureCompensationIndex(index)
                    onSettingsChange(cameraSettings.copy(exposureIndex = index))
                }
            )
            Spacer(modifier = Modifier.size(8.dp))
            ShutterButton(
                enabled = !isCapturing,
                onClick = {
                    isCapturing = true
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val jpegBytes = image.use {
                                    ImageMessageCodec.imageProxyToJpegBytes(it)
                                }
                                isCapturing = false
                                onPhotoCaptured(jpegBytes)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                exception.printStackTrace()
                                isCapturing = false
                            }
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun CameraSettingSlider(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = Color.White)
        Spacer(modifier = Modifier.width(12.dp))
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ShutterButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(72.dp)
            .border(width = 4.dp, color = Color.White, shape = CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.Gray)
    ) {
        Icon(
            Icons.Filled.PhotoCamera,
            contentDescription = "撮影",
            tint = Color.Black
        )
    }
}

/** ProcessCameraProvider の初期化完了を待つ */
private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider {
    return suspendCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            { continuation.resume(future.get()) },
            ContextCompat.getMainExecutor(context)
        )
    }
}
