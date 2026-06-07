package com.example.privatevault

import android.app.Application
import android.util.Log
import com.example.privatevault.data.PasswordRepository
import com.example.privatevault.data.VaultRepository
import com.example.privatevault.security.CryptoManager
import com.example.privatevault.security.GlobalExceptionHandler
import com.example.privatevault.security.SecureMediaStore
import com.example.privatevault.ui.DisguiseController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PrivateVaultApp : Application() {

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val cryptoManager: CryptoManager by lazy { CryptoManager() }
    val passwordRepository: PasswordRepository by lazy { PasswordRepository(this) }
    val vaultRepository: VaultRepository by lazy { VaultRepository(this) }
    val secureMediaStore: SecureMediaStore by lazy { SecureMediaStore(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 安装全局异常捕获,避免崩溃时泄露敏感信息
        GlobalExceptionHandler.install()
        // 同步桌面图标 alias 状态
        DisguiseController.setDisguise(this, passwordRepository.disguiseEnabled())
        // 在私有目录创建 .nomedia
        applicationScope.launch {
            runCatching { vaultRepository.ensureNoMedia() }
                .onFailure { Log.e(TAG, "ensureNoMedia failed", it) }
        }
    }

    companion object {
        private const val TAG = "PrivateVaultApp"
        lateinit var instance: PrivateVaultApp
            private set
    }
}
