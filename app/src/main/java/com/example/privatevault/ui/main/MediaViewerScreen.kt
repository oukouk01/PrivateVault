package com.example.privatevault.ui.main

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.privatevault.PrivateVaultApp
import com.example.privatevault.data.VaultRepository
import com.example.privatevault.security.CryptoManager
import java.io.File

@Composable
fun MediaViewerScreen(
    type: String,
    fileName: String,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as PrivateVaultApp
    val vault = app.vaultRepository
    val mediaType = if (type == "video") VaultRepository.MediaType.VIDEO
    else VaultRepository.MediaType.IMAGE
    val all = remember(type) { vault.list(mediaType) }
    val startIndex = all.indexOfFirst { it.file.name == fileName }.coerceAtLeast(0)

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)) {
        if (type == "video") {
            VideoPlayer(file = all[startIndex].file)
        } else {
            // 图片支持横向滑动切换 + 双指缩放
            val pagerState = rememberPagerState(initialPage = startIndex) { all.size }
            HorizontalPager(state = pagerState) { page ->
                val item = all[page]
                ZoomableImage(file = item.file)
            }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "back", tint = Color.White)
        }
    }
}

@Composable
private fun ZoomableImage(file: File) {
    val ctx = LocalContext.current
    val key = remember(file) { runCatching { CryptoManager.requireKey() }.getOrNull() }
    val bmp = remember(file, key) {
        key ?: return@remember null
        runCatching {
            ThumbnailDecoder.decode(
                file = file, key = key, isVideo = false
            )
        }.getOrNull()
    }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(file) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            coil.compose.AsyncImage(
                model = bmp,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )
        }
    }
}

@Composable
private fun VideoPlayer(file: File) {
    val ctx = LocalContext.current
    val key = remember(file) { runCatching { CryptoManager.requireKey() }.getOrNull() }
    // Player -> 自定义 DataSource.Factory -> 解密 CipherInputStream
    val player = remember(file, key) {
        ExoPlayer.Builder(ctx).build().apply {
            if (key != null) {
                val factory = DecryptingDataSourceFactory(key, file)
                val src = androidx.media3.datasource.ProgressiveMediaSource.Factory(factory)
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse("privatevault://encrypted/${file.name}"))
                    .setMimeType(guessMime(file.name))
                    .build()
                setMediaSource(src.createMediaSource(mediaItem))
                prepare()
                playWhenReady = true
            }
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                this.player = player
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun guessMime(name: String): String = when {
    name.endsWith(".mp4", true) -> "video/mp4"
    name.endsWith(".mov", true) -> "video/quicktime"
    name.endsWith(".mkv", true) -> "video/x-matroska"
    name.endsWith(".webm", true) -> "video/webm"
    else -> "video/mp4"
}
