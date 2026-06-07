package com.example.privatevault.ui.main

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.example.privatevault.security.AesCtr
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.crypto.SecretKey

/**
 * ExoPlayer 用的解密 DataSource。
 * - 在 open() 中跳过 12 字节 IV,后续用 CipherInputStream 实时解密。
 * - 不写任何明文到磁盘。
 */
class DecryptingDataSourceFactory(
    private val key: SecretKey,
    private val file: File
) : DataSource.Factory {
    override fun createDataSource(): DataSource = DecryptingDataSource(key, file)
}

class DecryptingDataSource(
    private val key: SecretKey,
    private val file: File
) : BaseDataSource(/* isNetwork = */ false) {

    private var raw: InputStream? = null
    private var decrypt: InputStream? = null
    private var remaining: Long = 0L
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val totalPlain = (file.length() - 12).coerceAtLeast(0L)
        // 跳过 IV
        val fs = FileInputStream(file)
        val iv = ByteArray(12)
        var read = 0
        while (read < 12) {
            val n = fs.read(iv, read, 12 - read)
            if (n < 0) error("Truncated file")
            read += n
        }
        raw = fs
        // 构造 CipherInputStream(它会自动从封装的 fs 中续读)
        val cipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE, key,
            javax.crypto.spec.IvParameterSpec(iv)
        )
        decrypt = javax.crypto.CipherInputStream(fs, cipher)
        remaining = totalPlain
        // ExoPlayer 会按 dataSpec.position 跳过;这里我们已经把流定位到 IV 之后,
        // 对它来说就是 plain 的字节流。
        transferStarted(dataSpec)
        return totalPlain
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val n = decrypt?.read(buffer, offset, length) ?: return C.RESULT_END_OF_INPUT
        if (n > 0) {
            remaining -= n
            bytesTransferred(n)
        }
        return n
    }

    override fun getUri(): Uri? = uri
    override fun close() {
        runCatching { raw?.close() }
        runCatching { decrypt?.close() }
        raw = null
        decrypt = null
        transferEnded()
    }
}
