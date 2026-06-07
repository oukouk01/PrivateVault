package com.example.privatevault.data

import android.content.Context
import android.content.SharedPreferences
import com.example.privatevault.security.KeyDerivation
import com.example.privatevault.security.PasswordHasher
import javax.crypto.SecretKey

/**
 * SharedPreferences 存储:
 *  - pwd_hash   : SHA-256+salt 单向哈希,用于校验屏幕密码
 *  - kdf_salt   : PBKDF2 盐,用于派生加密文件用的 AES 密钥
 *  - fail_count / fail_first_time : 5 次失败锁定 30 秒
 *  - disguise_enabled : 是否启用计算器伪装
 *  - kdf_iters : PBKDF2 迭代次数(便于将来升级)
 *
 * 不使用 DataStore 因为这些是少量关键安全配置,
 * 且 SharedPreferences 在加密文件中同步读写更简单可靠。
 */
class PasswordRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val hasher = PasswordHasher()

    fun isPasswordSet(): Boolean = prefs.contains(KEY_PWD_HASH)

    /** 首次设置密码。返回派生的 AES 密钥。 */
    fun setPassword(password: String): SecretKey {
        require(password.length == 6 && password.all { it.isDigit() }) {
            "Password must be 6 digits"
        }
        prefs.edit()
            .putString(KEY_PWD_HASH, hasher.hashPassword(password))
            .putString(KEY_KDF_SALT, KeyDerivation.toBase64(KeyDerivation.generateSalt()))
            .apply()
        return KeyDerivation.deriveKey(password.toCharArray(), getKdfSalt())
    }

    /** 校验密码。返回派生 AES 密钥 或 null。 */
    fun verifyPassword(password: String): SecretKey? {
        val stored = prefs.getString(KEY_PWD_HASH, null) ?: return null
        if (!hasher.verifyPassword(password, stored)) return null
        return KeyDerivation.deriveKey(password.toCharArray(), getKdfSalt())
    }

    fun getKdfSalt(): ByteArray {
        val s = prefs.getString(KEY_KDF_SALT, null)
        if (s != null) return KeyDerivation.fromBase64(s)
        val newSalt = KeyDerivation.generateSalt()
        prefs.edit().putString(KEY_KDF_SALT, KeyDerivation.toBase64(newSalt)).apply()
        return newSalt
    }

    /** 距离可重试还需要等待的毫秒数;0 表示可以尝试。 */
    fun remainingLockMs(): Long {
        val count = prefs.getInt(KEY_FAIL_COUNT, 0)
        if (count < MAX_FAILS) return 0L
        val firstTime = prefs.getLong(KEY_FAIL_FIRST_TIME, 0L)
        val elapsed = System.currentTimeMillis() - firstTime
        return (LOCK_DURATION_MS - elapsed).coerceAtLeast(0L)
    }

    fun recordFailure() {
        val count = prefs.getInt(KEY_FAIL_COUNT, 0) + 1
        if (count == 1) {
            prefs.edit()
                .putInt(KEY_FAIL_COUNT, count)
                .putLong(KEY_FAIL_FIRST_TIME, System.currentTimeMillis())
                .apply()
        } else {
            prefs.edit().putInt(KEY_FAIL_COUNT, count).apply()
        }
    }

    fun resetFailures() {
        prefs.edit()
            .remove(KEY_FAIL_COUNT)
            .remove(KEY_FAIL_FIRST_TIME)
            .apply()
    }

    fun disguiseEnabled(): Boolean = prefs.getBoolean(KEY_DISGUISE, true)
    fun setDisguiseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DISGUISE, enabled).apply()
    }

    fun currentFailCount(): Int = prefs.getInt(KEY_FAIL_COUNT, 0)

    companion object {
        private const val PREFS = "vault_prefs"
        private const val KEY_PWD_HASH = "pwd_hash"
        private const val KEY_KDF_SALT = "kdf_salt"
        private const val KEY_FAIL_COUNT = "fail_count"
        private const val KEY_FAIL_FIRST_TIME = "fail_first_time"
        private const val KEY_DISGUISE = "disguise_enabled"
        const val MAX_FAILS = 5
        const val LOCK_DURATION_MS = 30_000L
    }
}
