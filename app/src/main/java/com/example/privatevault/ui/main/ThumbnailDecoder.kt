package com.example.privatevault.ui.main

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.example.privatevault.security.AesCtr
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/**
 * 从加密文件解密出 缩略图,全程在内存中:
 *  - 图片:解密到 ByteArray,再用 BitmapFactory 解析
 *  - 视频:解密到 ByteArray,再用 MediaMetadataRetriever 抽帧
 * 绝不写明文到磁盘。
 */
object ThumbnailDecoder {

    private const val MAX_DIM = 512 // 缩略图最大边

    fun decode(file: File, key: SecretKey, isVideo: Boolean): Bitmap? {
        val plain = decryptToMemory(file, key)
        return try {
            if (isVideo) {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(ByteArrayMediaDataSource(plain))
                    mmr.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    mmr.release()
                }
            } else {
                // 二次采样防止 OOM
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(plain, 0, plain.size, opts)
                opts.inSampleSize = calcInSampleSize(opts.outWidth, opts.outHeight, MAX_DIM)
                opts.inJustDecodeBounds = false
                BitmapFactory.decodeByteArray(plain, 0, plain.size, opts)
            }
        } finally {
            // 清零内存
            plain.fill(0)
        }
    }

    private fun decryptToMemory(file: File, key: SecretKey): ByteArray {
        file.inputStream().use { input ->
            val iv = input.readNBytes(12)
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            val out = ByteArrayOutputStream(file.length().toInt().coerceAtLeast(1024))
            CipherInputStream(input, cipher).use { cis ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = cis.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
            }
            return out.toByteArray()
        }
    }

    private fun calcInSampleSize(w: Int, h: Int, maxDim: Int): Int {
        var sample = 1
        val max = maxOf(w, h)
        while (max / sample > maxDim) sample *= 2
        return sample
    }
}
