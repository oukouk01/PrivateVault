package com.example.privatevault.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 用 SHA-256 + 随机盐 对 6 位数字密码做单向哈希。
 * 存储格式: base64(salt):base64(hash)
 *
 * 注意:6 位数字密码熵有限,仅用于"屏幕锁"用途;
 * 真正用于加密媒体文件的密钥通过 PBKDF2 在 CryptoManager 中派生。
 */
class PasswordHasher {

    fun hashPassword(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(password.toByteArray(Charsets.UTF_8))
        var hash = digest.digest()
        // 多次迭代,提升对暴力破解的成本
        repeat(10_000) {
            digest.reset()
            digest.update(hash)
            digest.update(salt)
            hash = digest.digest()
        }
        return Base64.encodeToString(salt, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verifyPassword(password: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = Base64.decode(parts[0], Base64.NO_WRAP)
        val expected = Base64.decode(parts[1], Base64.NO_WRAP)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(password.toByteArray(Charsets.UTF_8))
        var hash = digest.digest()
        repeat(10_000) {
            digest.reset()
            digest.update(hash)
            digest.update(salt)
            hash = digest.digest()
        }
        return constantTimeEquals(hash, expected)
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
        return r == 0
    }
}
