package com.example.privatevault.security

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.InputStream

/**
 * 把系统相册中已导入的文件彻底删除。
 * - Android 11+ : 使用 createDeleteRequest,必须用户授权;
 *   调用方传入 (uris, onGranted) -> 申请权限。
 * - Android 10-: 直接通过 MediaStore 删除。
 * - 完全无权限/失败时返回 false,调用方决定是否提示用户。
 */
class SecureMediaStore(private val context: Context) {

    fun canDeleteDirectly(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    /**
     * Android 11+ : 生成删除 IntentSender,UI 层需通过 ActivityResultLauncher 启动。
     * Android 10- : 直接同步删除。
     */
    fun deleteRequestSender(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createDeleteRequest(
                context.contentResolver, uris
            ).intentSender
        }
        // 旧版本直接删
        deleteUris(uris)
        return null
    }

    /** 同步删除 (Android 10- 或已经用户授权过 MediaStore)。 */
    fun deleteUris(uris: List<Uri>): Int {
        if (uris.isEmpty()) return 0
        var ok = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ : 受 MediaStore.createDeleteRequest 控制。
            // 这里仅处理"用户已经在系统对话框中同意"的情况,通过 pendingIntent
            // 不在库内主动弹出。多数情况下交由 UI 层调 requestDelete。
            return 0
        }
        val resolver = context.contentResolver
        for (u in uris) {
            runCatching { resolver.delete(u, null, null) }.getOrNull()?.let { ok++ }
        }
        return ok
    }

    /** 通过 ContentResolver 打开 Uri 的 InputStream,失败返回 null。 */
    fun openInput(uri: Uri): InputStream? =
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

    /** 通过 MediaStore 找到原图/视频在磁盘上的 File (用于某些 API 下直拷)。 */
    fun resolveFileFromUri(uri: Uri): File? {
        if (uri.scheme != "content") return null
        return runCatching {
            val proj = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()?.let { File(it) }
    }

    fun uriFromId(id: Long, isVideo: Boolean): Uri {
        val collection = if (isVideo)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return ContentUris.withAppendedId(collection, id)
    }
}
