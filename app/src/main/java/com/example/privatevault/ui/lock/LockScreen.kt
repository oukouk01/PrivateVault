package com.example.privatevault.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.privatevault.PrivateVaultApp
import com.example.privatevault.R

@Composable
fun LockScreen(
    viewModel: LockViewModel,
    onUnlocked: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isUnlockedFlow.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val activity = ctx as FragmentActivity

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) onUnlocked()
    }

    LaunchedEffect(Unit) { viewModel.refreshLockInfo() }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF7F8FA)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            Text(
                text = stringResource(R.string.lock_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF202124)
            )
            Spacer(Modifier.height(8.dp))
            val sub = when (state.mode) {
                LockViewModel.Mode.SET -> stringResource(R.string.hint_set_password)
                LockViewModel.Mode.CONFIRM -> stringResource(R.string.hint_confirm_password)
                LockViewModel.Mode.VERIFY -> stringResource(R.string.hint_enter_password)
            }
            Text(sub, fontSize = 14.sp, color = Color(0xFF5F6368))
            Spacer(Modifier.height(24.dp))

            // 6 位指示点
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(6) { i ->
                    val filled = i < state.password.length
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (filled) Color(0xFF202124) else Color(0xFFE0E3E7))
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // 错误/锁定信息
            when {
                state.remainingLockMs > 0 -> Text(
                    text = stringResource(R.string.locked_too_many_times),
                    color = Color(0xFFD93025), fontSize = 14.sp
                )
                state.error != null -> Text(
                    text = state.error ?: "", color = Color(0xFFD93025), fontSize = 14.sp
                )
                state.mode == LockViewModel.Mode.VERIFY && state.attemptsLeft < 5 -> Text(
                    text = "剩余尝试次数:${state.attemptsLeft}",
                    color = Color(0xFF5F6368), fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(24.dp))
            Keypad(
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace
            )

            // 指纹按钮 (仅当密码已设置 & 设备支持指纹 & 当前不在锁定冷却中)
            if (state.mode == LockViewModel.Mode.VERIFY &&
                state.remainingLockMs == 0L &&
                BiometricAvailability.isAvailable(activity)
            ) {
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A73E8))
                        .clickable {
                            promptBiometric(activity) { ok ->
                                if (ok) {
                                    // 指纹成功后,从仓库取出派生密钥并设置
                                    val app = ctx.applicationContext as PrivateVaultApp
                                    val key = app.passwordRepository
                                        .verifyPassword(deriveFallbackFromFp(activity))
                                    if (key != null) {
                                        com.example.privatevault.security.CryptoManager.setKey(key)
                                        app.passwordRepository.resetFailures()
                                        viewModel.requestUnlock()
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = stringResource(R.string.action_use_fingerprint),
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

/**
 * 指纹的 fallback:仅在用户已设置密码且密码能用同一个 key 派生。
 * 安全权衡:此处为了演示简洁,在指纹成功后让用户输入一次密码。
 * 生产代码应使用 Android Keystore 将解锁 token 与指纹绑定。
 */
private fun deriveFallbackFromFp(activity: FragmentActivity): String {
    val sp = activity.getSharedPreferences("vault_prefs", android.content.Context.MODE_PRIVATE)
    // Keystore 绑定的生产做法不在本 demo 范围;此处返回空,让上层要求手动输密码。
    return sp.getString("fp_fallback", "") ?: ""
}

private fun promptBiometric(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(true)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(false)
            }
        })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.action_use_fingerprint))
        .setNegativeButtonText("使用密码")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(info)
}

@Composable
private fun Keypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(" ", "0", "⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { k ->
                    when (k) {
                        " " -> Spacer(Modifier.weight(1f).aspectRatio(1.4f))
                        "⌫" -> KeyButton(
                            text = "", icon = Icons.Default.Backspace,
                            modifier = Modifier.weight(1f).aspectRatio(1.4f),
                            onClick = onBackspace
                        )
                        else -> KeyButton(
                            text = k,
                            modifier = Modifier.weight(1f).aspectRatio(1.4f),
                            onClick = { onDigit(k[0]) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(20.dp)),
        color = Color.White,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color(0xFF202124), modifier = Modifier.size(28.dp))
            } else {
                Text(text, fontSize = 28.sp, color = Color(0xFF202124), fontWeight = FontWeight.Medium)
            }
        }
    }
}
