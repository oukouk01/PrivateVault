package com.example.privatevault.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.privatevault.PrivateVaultApp
import com.example.privatevault.R
import com.example.privatevault.ui.DisguiseController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onDisguiseChanged: (Boolean) -> Unit = {}
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as PrivateVaultApp
    val repo = app.passwordRepository
    val scope = rememberCoroutineScope()

    var disguise by remember { mutableStateOf(repo.disguiseEnabled()) }
    var showBackupWarning by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportPwd by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(16.dp))

        // 备份提醒卡
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E0))
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.backup_warning_title),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.backup_warning_msg), color = Color(0xFF5F6368))
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { showBackupWarning = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("了解备份说明") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { showExportDialog = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !exporting
        ) { Text(stringResource(R.string.action_export)) }

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        // 伪装开关
        SettingRow(
            title = stringResource(R.string.disguise_title),
            subtitle = stringResource(R.string.disguise_summary),
            checked = disguise,
            onChange = { v ->
                disguise = v
                repo.setDisguiseEnabled(v)
                DisguiseController.setDisguise(ctx, v)
                onDisguiseChanged(v)
            }
        )
    }

    if (showBackupWarning) {
        AlertDialog(
            onDismissRequest = { showBackupWarning = false },
            title = { Text(stringResource(R.string.backup_warning_title)) },
            text = {
                Text(
                    "由于所有私密文件均加密保存在本应用私有目录中," +
                            "一旦卸载 App 或在系统设置中清除数据,所有文件将永久丢失," +
                            "无法找回。\n\n强烈建议您定期使用下方"加密导出"功能," +
                            "将所有文件打包为受密码保护的 .zip 文件并拷贝到电脑长期保存。\n\n" +
                            "本应用不提供云备份,也不应使用云备份功能,以确保隐私。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showBackupWarning = false }) { Text("我已知晓") }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { if (!exporting) showExportDialog = false },
            title = { Text("加密导出") },
            text = {
                Column {
                    Text("请输入 6 位数字密码,系统将打包并加密所有 .enc 文件。")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportPwd,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) exportPwd = it },
                        label = { Text("密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = exportPwd.length == 6 && !exporting,
                    onClick = {
                        exporting = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                runCatching {
                                    val tmp = EncryptedExporter.exportAllToCache(
                                        ctx = ctx,
                                        password = exportPwd,
                                        vault = app.vaultRepository
                                    )
                                    if (tmp != null) {
                                        EncryptedExporter.publishToDownloads(
                                            ctx, tmp, tmp.name
                                        )
                                    } else false
                                }.getOrElse { false }
                            }
                            exporting = false
                            showExportDialog = false
                            exportPwd = ""
                            Toast.makeText(
                                ctx,
                                if (ok) ctx.getString(R.string.export_success) else ctx.getString(R.string.export_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) { Text("开始导出") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }, enabled = !exporting) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFF5F6368), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
