package com.example.privatevault.security

import java.io.Closeable

/** 用于异常路径统一关闭已打开的解密流,避免明文残留。 */
object SecureStreamRegistry {
    private val streams = mutableListOf<Closeable>()

    @Synchronized
    fun register(c: Closeable) { streams.add(c) }

    @Synchronized
    fun unregister(c: Closeable) { streams.remove(c) }

    @Synchronized
    fun closeAll() {
        val snapshot = streams.toList()
        streams.clear()
        snapshot.forEach { runCatching { it.close() } }
    }
}
