package com.example.privatevault.ui.main

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.privatevault.PrivateVaultApp
import com.example.privatevault.data.VaultRepository
import com.example.privatevault.security.AesCtr
import com.example.privatevault.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 主界面 + 导入/移出/导出 共享 ViewModel。
 *
 * 设计要点:
 *  - 所有写盘都通过 AesCtr 在文件流中加解密,绝不产生明文临时文件。
 *  - 删除系统相册原图通过 MediaStore.createDeleteRequest 走官方流程,
 *    IntentSender 交回 UI 层用 IntentSenderRequest 启动。
 */
class MediaViewModel(app: Application) : AndroidViewModel(app) {

    private val vault = (app as PrivateVaultApp).vaultRepository
    private val media = (app as PrivateVaultApp).secureMediaStore
    private val passwordRepo = (app as PrivateVaultApp).passwordRepository

    private val _images = MutableStateFlow<List<VaultRepository.MediaItem>>(emptyList())
    val images: StateFlow<List<VaultRepository.MediaItem>> = _images.asStateFlow()

    private val _videos = MutableStateFlow<List<VaultRepository.MediaItem>>(emptyList())
    val videos: StateFlow<List<VaultRepository.MediaItem>> = _videos.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _pendingDelete = MutableStateFlow<List<Uri>>(emptyList())
    /** 待删除的 MediaStore Uri,等待用户授权后由 UI 触发 startIntentSenderForResult。 */
    val pendingDelete: StateFlow<List<Uri>> = _pendingDelete.asStateFlow()

    fun refresh() {
        _images.value = vault.list(VaultRepository.MediaType.IMAGE)
        _videos.value = vault.list(VaultRepository.MediaType.VIDEO)
    }

    /**
     * 导入并(可选)删除系统原文件。
     * @param uris 来自 PhotoPicker / OpenDocument
     * @param deleteOriginals 是否在拷贝成功后请求删除原文件
     */
    fun importUris(
        uris: List<Uri>,
        deleteOriginals: Boolean,
        onNeedDeletePermission: (IntentSender) -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val key = CryptoManager.requireKey()
                val importedType = VaultRepository.MediaType.IMAGE // 未知时默认,逐个判断
                val pendingDeletes = mutableListOf<Uri>()
                var okCount = 0
                var failCount = 0
                for (u in uris) {
                    val type = guessType(u) ?: importedType
                    val target = vault.newEncryptedFile(type, ".enc")
                    val copied = runCatching {
                        withContext(Dispatchers.IO) { copyEncrypt(u, key, target) }
                    }
                    if (copied.isSuccess) {
                        okCount++
                        if (deleteOriginals) pendingDeletes.add(u)
                    } else {
                        failCount++
                        // 清理半成品
                        target.delete()
                    }
                }
                refresh()
                if (deleteOriginals && pendingDeletes.isNotEmpty()) {
                    requestDeleteOriginals(pendingDeletes, onNeedDeletePermission)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "import failed", t)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun requestDeleteOriginals(
        uris: List<Uri>,
        onNeedDeletePermission: (IntentSender) -> Unit
    ) {
        try {
            val pi = media.deleteRequestSender(uris)
            if (pi != null) onNeedDeletePermission(pi)
        } catch (_: Throwable) {
            // Android 10- 直接同步删
            val n = media.deleteUris(uris)
            Log.d(TAG, "pre-11 deleted $n items directly")
        }
    }

    fun onDeleteResult(granted: Boolean) {
        val uris = _pendingDelete.value
        if (!granted) {
            Log.w(TAG, "User denied delete for ${uris.size} items")
            _pendingDelete.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            for (u in uris) {
                runCatching {
                    getApplication<Application>().contentResolver.delete(u, null, null)
                }
            }
            _pendingDelete.value = emptyList()
        }
    }

    /** 移出私密空间 -> 解密并通过 MediaStore 写回系统相册(流式,避免大文件 OOM)。 */
    fun exportToGallery(item: VaultRepository.MediaItem, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val key = CryptoManager.requireKey()
                    writeBackStreamed(item, key)
                }
            }
            _busy.value = false
            onResult(ok.isSuccess)
            if (ok.isSuccess) {
                vault.deleteItem(item)
                refresh()
            }
        }
    }

    private fun writeBackStreamed(item: VaultRepository.MediaItem, key: javax.crypto.SecretKey) {
        val resolver = getApplication<Application>().contentResolver
        val (collection, mimeGuess) = when (item.type) {
            VaultRepository.MediaType.IMAGE ->
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "image/*"
            VaultRepository.MediaType.VIDEO ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "video/*"
        }
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "restored_${item.file.nameWithoutExtension}.bin")
            put(MediaStore.MediaColumns.MIME_TYPE, mimeGuess)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/PrivateVault")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val itemUri = resolver.insert(collection, values) ?: error("insert failed")

        item.file.inputStream().use { input ->
            val iv = input.readNBytes(12)
            val cipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(
                javax.crypto.Cipher.DECRYPT_MODE, key,
                javax.crypto.spec.IvParameterSpec(iv)
            )
            javax.crypto.CipherInputStream(input, cipher).use { cins ->
                resolver.openOutputStream(itemUri)?.use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = cins.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                    }
                } ?: error("openOutputStream null")
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val v2 = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(itemUri, v2, null, null)
        }
    }

    private fun copyEncrypt(uri: Uri, key: javax.crypto.SecretKey, target: File) {
        val resolver = getApplication<Application>().contentResolver
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "openInputStream null for $uri" }
            FileOutputStream(target).use { out -> AesCtr.encryptStream(key, input, out) }
        }
    }

    private fun guessType(uri: Uri): VaultRepository.MediaType? {
        val mime = getApplication<Application>().contentResolver.getType(uri) ?: return null
        return when {
            mime.startsWith("video/") -> VaultRepository.MediaType.VIDEO
            mime.startsWith("image/") -> VaultRepository.MediaType.IMAGE
            else -> null
        }
    }

    companion object { private const val TAG = "MediaViewModel" }
}
