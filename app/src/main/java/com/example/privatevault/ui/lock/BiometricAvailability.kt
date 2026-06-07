package com.example.privatevault.ui.lock

import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity

object BiometricAvailability {
    fun status(activity: FragmentActivity): Int =
        BiometricManager.from(activity).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )

    fun isAvailable(activity: FragmentActivity): Boolean =
        status(activity) == BiometricManager.BIOMETRIC_SUCCESS
}
