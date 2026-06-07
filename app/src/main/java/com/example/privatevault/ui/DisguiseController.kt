package com.example.privatevault.ui

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import com.example.privatevault.PrivateVaultApp

/**
 * 用来在运行时切换"计算器 alias" / "真实 launcher" 是否启用。
 * - 默认在 Manifest 中 alias 启用,真实 launcher 也启用 — 让用户既能装上也能换。
 * - "关闭伪装" 时 disable alias + enable 真实 launcher,使桌面图标变成 vault 主题。
 *
 * Manifest 中 alias 的 android:enabled="true" 是默认状态;真正切换在代码中完成。
 */
object DisguiseController {

    private const val REAL_COMPONENT =
        "com.example.privatevault.ui.MainActivity"
    private const val ALIAS_COMPONENT =
        "com.example.privatevault.ui.CalculatorAliasActivity"

    fun setDisguise(context: android.content.Context, enabled: Boolean) {
        val pm = context.packageManager
        val real = ComponentName(context, REAL_COMPONENT)
        val alias = ComponentName(context, ALIAS_COMPONENT)
        if (enabled) {
            pm.setComponentEnabledSetting(
                real, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                alias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
            )
        } else {
            pm.setComponentEnabledSetting(
                alias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
        }
    }
}
