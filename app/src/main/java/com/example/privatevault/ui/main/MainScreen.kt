package com.example.privatevault.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.privatevault.R
import com.example.privatevault.data.VaultRepository
import com.example.privatevault.security.CryptoManager
import com.example.privatevault.ui.settings.SettingsScreen
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenViewer: (type: String, fileName: String) -> Unit,
    onLocked: () -> Unit,
    mediaViewModel: MediaViewModel = viewModel()
) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { mediaViewModel.refresh() }

    var tab by remember { mutableStateOf(MainTab.IMAGES) }
    val images by mediaViewModel.images.collectAsState()
    val videos by mediaViewModel.videos.collectAsState()
    val busy by mediaViewModel.busy.collectAsState()

    var pendingDeleteUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pickedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var longPressItem by remember { mutableStateOf<VaultRepository.MediaItem?>(null) }

    // Photo Picker:不需任何运行时权限,Android 13+ 强制使用
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            pickedUris = uris
            showDeleteConfirm = true
        }
    }
    val pickVideos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            pickedUris = uris
            showDeleteConfirm = true
        }
    }

    // Android 11+ 删除原图授权回调
    val deleteSenderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { _ ->
        // MediaStore 文档:被系统弹窗允许后会自动删除;
        // 但有些 OEM 需要我们手动再 delete 一次。这里二者都做。
        mediaViewModel.onDeleteResult(granted = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_placeholder)) },
                actions = {
                    IconButton(onClick = onLocked) {
                        Icon(Icons.Default.Lock, contentDescription = "锁定")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == MainTab.IMAGES,
                    onClick = { tab = MainTab.IMAGES },
                    icon = { Icon(Icons.Default.Image, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_images)) }
                )
                NavigationBarItem(
                    selected = tab == MainTab.VIDEOS,
                    onClick = { tab = MainTab.VIDEOS },
                    icon = { Icon(Icons.Default.Videocam, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_videos)) }
                )
                NavigationBarItem(
                    selected = tab == MainTab.SETTINGS,
                    onClick = { tab = MainTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_settings)) }
                )
            }
        },
        floatingActionButton = {
            if (tab != MainTab.SETTINGS) {
                FloatingActionButton(onClick = {
                    if (tab == MainTab.IMAGES) {
                        pickImages.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    } else {
                        pickVideos.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_import))
                }
            }
        }
    ) { pad ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(pad)
        ) {
            when (tab) {
                MainTab.IMAGES -> MediaGrid(
                    items = images,
                    type = VaultRepository.MediaType.IMAGE,
                    onClick = { onOpenViewer("image", it.file.name) },
                    onLongClick = { longPressItem = it }
                )
                MainTab.VIDEOS -> MediaGrid(
                    items = videos,
                    type = VaultRepository.MediaType.VIDEO,
                    onClick = { onOpenViewer("video", it.file.name) },
                    onLongClick = { longPressItem = it }
                )
                MainTab.SETTINGS -> SettingsScreen(
                    onDisguiseChanged = { /* 切换时同步到 DisguiseController */ }
                )
            }
            if (busy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
        }
    }

    // 二次确认弹窗
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.dialog_delete_confirm_title)) },
            text = { Text(stringResource(R.string.dialog_delete_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    val uris = pickedUris
                    pickedUris = emptyList()
                    mediaViewModel.importUris(
                        uris = uris,
                        deleteOriginals = true,
                        onNeedDeletePermission = { sender ->
                            deleteSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
                        }
                    )
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    val uris = pickedUris
                    pickedUris = emptyList()
                    mediaViewModel.importUris(uris, deleteOriginals = false) {}
                }) { Text("仅导入") }
            }
        )
    }

    // 长按 -> 移出私密空间
    longPressItem?.let { item ->
        AlertDialog(
            onDismissRequest = { longPressItem = null },
            title = { Text("移出私密空间?") },
            text = { Text("文件将被解密并恢复到系统相册,不再受密码保护。") },
            confirmButton = {
                TextButton(onClick = {
                    val target = item
                    longPressItem = null
                    mediaViewModel.exportToGallery(target) { /* 简单处理,失败可提示 */ }
                }) { Text("移出") }
            },
            dismissButton = { TextButton(onClick = { longPressItem = null }) { Text("取消") } }
        )
    }
}

private enum class MainTab { IMAGES, VIDEOS, SETTINGS }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(
    items: List<VaultRepository.MediaItem>,
    type: VaultRepository.MediaType,
    onClick: (VaultRepository.MediaItem) -> Unit,
    onLongClick: (VaultRepository.MediaItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无${if (type == VaultRepository.MediaType.IMAGE) "图片" else "视频"},点击右下角 + 导入",
                textAlign = TextAlign.Center,
                color = Color(0xFF5F6368)
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(items, key = { it.file.absolutePath }) { item ->
            Thumbnail(
                file = item.file,
                type = type,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { onClick(item) },
                        onLongClick = { onLongClick(item) }
                    )
            )
        }
    }
}

@Composable
private fun Thumbnail(
    file: File,
    type: VaultRepository.MediaType,
    modifier: Modifier = Modifier
) {
    // 解密缩略图用临时 ByteArray(在内存中),不落盘
    val ctx = LocalContext.current
    val key = remember(file) { runCatching { CryptoManager.requireKey() }.getOrNull() }
    val bmp = remember(file, key) {
        key ?: return@remember null
        runCatching {
            ThumbnailDecoder.decode(
                file = file,
                key = key,
                isVideo = type == VaultRepository.MediaType.VIDEO
            )
        }.getOrNull()
    }
    if (bmp != null) {
        coil.compose.AsyncImage(
            model = bmp,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier.background(Color(0xFFE0E3E7)))
    }
}
