package com.example.privatevault.ui.lock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.privatevault.PrivateVaultApp
import com.example.privatevault.data.PasswordRepository
import com.example.privatevault.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LockViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: PasswordRepository =
        (app as PrivateVaultApp).passwordRepository

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlockedFlow: StateFlow<Boolean> = _isUnlocked.asStateFlow()
    val isUnlocked: Boolean get() = _isUnlocked.value

    fun requestUnlock() { _isUnlocked.value = true }

    fun lock() {
        CryptoManager.clearKey()
        _isUnlocked.value = false
    }

    enum class Mode { SET, CONFIRM, VERIFY }

    data class State(
        val mode: Mode = Mode.VERIFY,
        val firstInput: String = "",
        val password: String = "",
        val error: String? = null,
        val remainingLockMs: Long = 0L,
        val attemptsLeft: Int = PasswordRepository.MAX_FAILS
    )

    private val _state = MutableStateFlow(
        if (repo.isPasswordSet()) State(Mode.VERIFY) else State(Mode.SET)
    )
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        refreshLockInfo()
    }

    fun refreshLockInfo() {
        val remain = repo.remainingLockMs()
        val attempts = if (remain > 0) 0 else PasswordRepository.MAX_FAILS - repo.currentFailCount()
        _state.value = _state.value.copy(
            remainingLockMs = remain,
            attemptsLeft = attempts
        )
    }

    /** 输入一位数字。 */
    fun onDigit(d: Char) {
        val s = _state.value
        if (s.remainingLockMs > 0) return
        if (s.password.length >= 6) return
        val next = s.password + d
        _state.value = s.copy(password = next, error = null)
        if (next.length == 6) submit()
    }

    fun onBackspace() {
        val s = _state.value
        if (s.password.isEmpty()) return
        _state.value = s.copy(password = s.password.dropLast(1), error = null)
    }

    fun onClear() {
        _state.value = _state.value.copy(password = "", error = null)
    }

    private fun submit() {
        val s = _state.value
        viewModelScope.launch {
            when (s.mode) {
                Mode.SET -> {
                    _state.value = s.copy(
                        mode = Mode.CONFIRM,
                        firstInput = s.password,
                        password = "",
                        error = null
                    )
                }
                Mode.CONFIRM -> {
                    if (s.password == s.firstInput) {
                        withContext(Dispatchers.IO) {
                            val key = repo.setPassword(s.password)
                            CryptoManager.setKey(key)
                        }
                        _state.value = s.copy(error = null)
                        requestUnlock()
                    } else {
                        _state.value = State(
                            mode = Mode.SET,
                            error = "两次输入不一致,请重新设置"
                        )
                    }
                }
                Mode.VERIFY -> {
                    val key = withContext(Dispatchers.IO) {
                        repo.verifyPassword(s.password)
                    }
                    if (key != null) {
                        repo.resetFailures()
                        CryptoManager.setKey(key)
                        requestUnlock()
                    } else {
                        repo.recordFailure()
                        val remain = repo.remainingLockMs()
                        val attempts = if (remain > 0) 0
                        else PasswordRepository.MAX_FAILS - repo.currentFailCount()
                        _state.value = _state.value.copy(
                            password = "",
                            error = if (remain > 0) "错误,请稍后再试" else "密码错误",
                            remainingLockMs = remain,
                            attemptsLeft = attempts
                        )
                        if (remain > 0) startCountdown()
                    }
                }
            }
        }
    }

    private fun startCountdown() {
        viewModelScope.launch {
            while (true) {
                val remain = repo.remainingLockMs()
                if (remain <= 0) {
                    _state.value = _state.value.copy(
                        remainingLockMs = 0L,
                        attemptsLeft = PasswordRepository.MAX_FAILS,
                        error = null
                    )
                    return@launch
                }
                _state.value = _state.value.copy(remainingLockMs = remain)
                delay(250)
            }
        }
    }
}
