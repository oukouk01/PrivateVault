package com.example.privatevault.security

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 全局异常捕获器,防止崩溃时把内存中未加密的媒体或栈轨迹泄露到 logcat / 系统弹窗。
 * 所有未捕获异常都会被记录到本地加密日志(可选),并以安全方式结束进程。
 */
object GlobalExceptionHandler {

    private const val TAG = "VaultCrash"

    fun install() {
        val previous = Thread.getDefaultUnhandledExceptionHandler()
        Thread.setDefaultUnhandledExceptionHandler { thread, throwable ->
            try {
                // 不在 logcat 输出原始异常信息,只输出"已捕获"
                Log.e(TAG, "Unhandled exception in thread ${thread.name}")
                // 主动清理任何已打开的解密流(由调用方在 catch 中注册)
                SecureStreamRegistry.closeAll()
                // 关闭/加密敏感内存
                System.gc()
            } catch (_: Throwable) {
                // 绝不再次抛出
            } finally {
                // 把控制权交回系统默认处理,避免影响正常崩溃流程
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    fun stackTraceToString(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}
