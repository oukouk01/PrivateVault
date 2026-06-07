package com.example.privatevault.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 用 PBKDF2-HMAC-SHA256 从用户密码派生 AES 密钥。
 * salt 与 iteration 与 hash 一起持久化,保证不同用户的密文互不兼容。
 */
object KeyDerivation {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun generateSalt(): ByteArray {
        val s = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(s)
        return s
    }

    fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    fun toBase64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    fun fromBase64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
