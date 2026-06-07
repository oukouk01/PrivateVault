package com.example.privatevault.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.privatevault.ui.calculator.CalculatorScreen
import com.example.privatevault.ui.lock.LockViewModel
import com.example.privatevault.ui.theme.PrivateVaultTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 整个 App 只有 1 个 MainActivity:
 *  - 启动时根据 SharedPreferences 中的"伪装开关 + 入口"决定显示计算器还是密码锁。
 *  - 计算器中输入"123+456=" 跳到密码锁。
 *  - 用户在设置里关闭伪装后,直接进入密码锁。
 *  - App 进入后台(onStop)即上锁;恢复时如果已锁,强制回到锁屏/计算器界面。
 */
class MainActivity : FragmentActivity() {

    private val lockViewModel: LockViewModel by viewModels()
    private val resumeTick = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 锁屏期间禁止截屏与多任务预览 — 由 Compose 端根据 showCalculator 动态切换

        // 监听前台恢复
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                resumeTick.collect { /* 仅作 recomposition 触发器 */ }
            }
        }

        setContent {
            PrivateVaultTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val tick by resumeTick.collectAsState()
                    val app = application as com.example.privatevault.PrivateVaultApp
                    val prefs = app.passwordRepository
                    // tick 每次 resume 变化时,重新读 disguise 配置
                    var showCalculator by remember(tick) {
                        mutableStateOf(prefs.disguiseEnabled())
                    }
                    // 在 tick 变化时同步 lockViewModel
                    androidx.compose.runtime.LaunchedEffect(tick) {
                        if (tick > 0) lockViewModel.lock()
                    }
                    // 计算器不设 FLAG_SECURE(看起来像正常应用);锁屏/主界面才阻止截屏
                    androidx.compose.runtime.LaunchedEffect(showCalculator) {
                        if (showCalculator) {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            window.setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE
                            )
                        }
                    }

                    if (showCalculator) {
                        CalculatorScreen(
                            onSecretUnlocked = {
                                showCalculator = false
                            }
                        )
                    } else {
                        AppNavGraph(lockViewModel = lockViewModel)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 进入后台即上锁
        lockViewModel.lock()
    }

    override fun onStart() {
        super.onStart()
        // 触发 recomposition + lock
        resumeTick.value = resumeTick.value + 1
    }
}
