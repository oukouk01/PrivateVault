package com.example.privatevault.ui.settings

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.privatevault.data.VaultRepository
import com.example.privatevault.security.KeyDerivation
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/**
 * 把所有 .enc 文件打包为一个加密 zip:
 *   <Download>/PrivateVault_Backup_yyyyMMdd_HHmmss.zip
 *  zip 内的 .enc 文件整体作为 1 个文件,使用 PBKDF2 派生的 AES-256-CTR 密钥再次加密。
 *
 * 注意:本实现不依赖第三方 zip 加密库(易出错/兼容性差),
 * 而是采用 zip 内的"plaintext = single binary blob"模式,先打 zip,再对整个 zip 二进制加密。
 */
object EncryptedExporter {

    private const val TAG = "EncryptedExporter"
    private const val INNER_ZIP = "vault.bin"

    /**
     * 把加密 zip 写到 cacheDir,返回临时文件。
     * 成功后由调用方通过 [publishToDownloads] 拷到 Download/PrivateVault。
     */
    fun exportAllToCache(
        ctx: Context,
        password: String,
        vault: VaultRepository
    ): File? {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key: SecretKey = KeyDerivation.deriveKey(password.toCharArray(), salt)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))

        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val name = "PrivateVault_Backup_$ts.zip"
        val tmp = File(ctx.cacheDir, name)
        return runCatching {
            FileOutputStream(tmp).use { rawOut ->
                // 文件头:4 字节 magic + 16 字节 salt + 12 字节 iv + 加密体
                rawOut.write("PVLT".toByteArray(Charsets.US_ASCII))
                rawOut.write(salt)
                rawOut.write(iv)
                CipherOutputStream(rawOut, cipher).use { cout ->
                    ZipOutputStream(cout).use { zout ->
                        val items = vault.list(VaultRepository.MediaType.IMAGE) +
                                vault.list(VaultRepository.MediaType.VIDEO)
                        for (item in items) {
                            val entryName = "${item.type.subdir}/${item.file.name}"
                            zout.putNextEntry(ZipEntry(entryName))
                            FileInputStream(item.file).use { it.copyTo(zout) }
                            zout.closeEntry()
                        }
                    }
                }
            }
            tmp
        }.onFailure { Log.e(TAG, "export failed", it) }
            .getOrNull()
    }

    /**
     * 旧入口保留以备单步调用 — 实际不再使用,新版本走 [exportAllToCache] + [publishToDownloads]。
     */
    @Suppress("unused")
    fun exportAll(
        ctx: Context,
        password: String,
        vault: VaultRepository
    ): Boolean {
        val tmp = exportAllToCache(ctx, password, vault) ?: return false
        return publishToDownloads(ctx, tmp, tmp.name)
    }

    /**
     * Android 10+ : 把 cacheFile 通过 MediaStore 拷到 Download,然后删除 cacheFile。
     * 真正落地由 SettingsScreen 端的 exportAll 完成后调用。
     */
    fun publishToDownloads(ctx: Context, src: File, displayName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return src.exists()
        val resolver = ctx.contentResolver
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PrivateVault")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val v2 = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, v2, null, null)
            }
            src.delete()
            true
        }.getOrDefault(false)
    }
}
