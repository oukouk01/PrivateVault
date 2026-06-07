package com.example.privatevault.security

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/**
 * AES-256-CTR 加解密。
 * 文件格式: 12 字节 IV 后接密文
 * 出于"不落盘"安全考虑,所有操作都在流式内存/磁盘流中完成。
 */
object AesCtr {

    private const val IV_BYTES = 12
    private val rng = SecureRandom()

    fun encryptStream(key: SecretKey, plainIn: InputStream, out: OutputStream) {
        val iv = ByteArray(IV_BYTES).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        out.write(iv)
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = plainIn.read(buf)
            if (n <= 0) break
            out.write(cipher.update(buf, 0, n))
        }
        out.write(cipher.doFinal())
    }

    fun decryptStream(key: SecretKey, encIn: InputStream, out: OutputStream) {
        val iv = ByteArray(IV_BYTES)
        var read = 0
        while (read < IV_BYTES) {
            val n = encIn.read(iv, read, IV_BYTES - read)
            if (n < 0) error("Truncated file: missing IV")
            read += n
        }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = encIn.read(buf)
            if (n <= 0) break
            out.write(cipher.update(buf, 0, n))
        }
        out.write(cipher.doFinal())
    }

    /** 返回带注册表的 CipherInputStream,用于视频播放等长生命周期解密流。 */
    fun openDecryptStream(key: SecretKey, encIn: InputStream): CipherInputStream {
        val iv = ByteArray(IV_BYTES)
        var read = 0
        while (read < IV_BYTES) {
            val n = encIn.read(iv, read, IV_BYTES - read)
            if (n < 0) error("Truncated file: missing IV")
            read += n
        }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        val cis = CipherInputStream(encIn, cipher)
        SecureStreamRegistry.register(cis)
        return cis
    }
}
