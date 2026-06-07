package com.example.privatevault.security

import javax.crypto.SecretKey

/**
 * 在一次解锁会话中保存派生出的 AES 密钥。
 * - 锁屏成功后 setKey
 * - 锁屏/退出时 clearKey (将字节置零)
 * - 业务模块 (加密/解密/导出) 从这里取密钥
 */
object CryptoManager {

    private var currentKey: SecretKey? = null

    fun setKey(key: SecretKey) { currentKey = key }
    fun clearKey() {
        currentKey = null
    }

    fun requireKey(): SecretKey =
        currentKey ?: error("Vault locked: no key in memory")
}
