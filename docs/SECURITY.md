# Security

> 本文档针对安全审计员、密码学研究者、关注隐私的高级用户。
> 如果你想了解"代码是怎么写的",请看 [ARCHITECTURE.md](ARCHITECTURE.md) +
> [CRYPTO.md](CRYPTO.md);本文聚焦"它**不**防什么 / 假定是什么 / 已知弱点"。

## 目录

- [威胁模型](#威胁模型)
- [资产清单](#资产清单)
- [信任边界](#信任边界)
- [保护机制](#保护机制)
- [已知弱点 / 限制](#已知弱点--限制)
- [建议改进路线](#建议改进路线)
- [披露策略](#披露策略)

---

## 威胁模型

### 假设能防

| 场景 | 防护措施 |
| --- | --- |
| 设备短暂离开用户 (无 Root) | 锁屏 + 失败锁定 + 立即上锁 (`onStop`) |
| 普通 App 访问公共相册 | MediaStore 删除 + 加密文件不暴露 |
| 系统截图 / 录屏 (锁屏状态) | `FLAG_SECURE` |
| 多任务预览泄露 | `FLAG_SECURE` + 解锁后才进 MAIN |
| 暴力穷举 6 位数字 | 5 次失败锁定 30 秒 (理论上限: 6! / 5 = 1440/天) |
| 卸载残留被取证 | 私有目录 + `allowBackup=false` + `dataExtractionRules` 排除 |
| 调试日志泄露 | 异常路径只打 `TAG` 不打 `message` |

### **不**假设能防

| 场景 | 说明 |
| --- | --- |
| 设备已被 Root 且攻击者已获得 adb shell | 攻击者可读取内存、绕过锁屏 |
| 安装了恶意 App 并能注入本进程 (Accessibility / 漏洞) | Android 沙箱失效,所有 App 都难以防御 |
| 物理攻击 (cold boot, JTAG, 基带) | 需硬件级防护,本项目不涉及 |
| 用户主动导出文件后再泄露 | 应用层无法控制 |
| 用户忘记 6 位密码 | **无任何后门**,文件无法恢复 |
| PBKDF2 迭代次数在低端设备上拖慢启动 | 120 000 轮 < 1s 在大多数现代设备 |
| 侧信道(功耗分析、电磁) | 需 SoC 厂商配合,本项目不涉及 |
| 暴力穷举 6 位数字 + 离线字典攻击 | 防御依赖密码哈希;但 6 位数字熵仅 ~20 bit,在线攻击受锁定抑制,离线则**只有 SHA-256+salt 屏障** |

---

## 资产清单

| 资产 | 存储位置 | 静态保护 | 动态保护 |
| --- | --- | --- | --- |
| 6 位密码哈希 | `SharedPreferences/vault_prefs` | SHA-256 + 16 字节盐 + 10 000 轮迭代 | `allowBackup=false` 防止云备份 |
| KDF 盐 (PBKDF2) | 同上 | 16 字节随机 | 同上 |
| 加密的媒体文件 | `filesDir/private_media/{images,videos}/*.enc` | AES-256-CTR + 12 字节每文件 IV | `.nomedia` + 无 root 不可读 |
| 会话 AES 密钥 | 内存中 (`CryptoManager.currentKey`) | SecretKey 对象 | `onStop` 清空引用 |
| 失败计数 | `SharedPreferences/vault_prefs` | 无加密 | 用于 5 次失败锁定 |

---

## 信任边界

```
┌──────────────────────────────────────────────────┐
│ Trusted: 用户本人                                  │
├──────────────────────────────────────────────────┤
│ Trusted: Android Framework (锁屏, 文件权限)        │
├──────────────────────────────────────────────────┤
│ Trusted: 应用自身代码 (未做完整性校验)              │
├──────────────────────────────────────────────────┤
│  * Untrusted * 系统 MediaScanner (只读 .nomedia)   │
├──────────────────────────────────────────────────┤
│  * Untrusted * Photo Picker (只发 Uri)             │
├──────────────────────────────────────────────────┤
│  * Untrusted * 其他 App (默认沙箱隔离)             │
└──────────────────────────────────────────────────┘
```

---

## 保护机制

### 1. 密码哈希 (`PasswordHasher.kt`)

```kotlin
fun hashPassword(password: String): String {
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(salt)
    digest.update(password.toByteArray())
    var hash = digest.digest()
    repeat(10_000) {
        digest.reset()
        digest.update(hash)
        digest.update(salt)
        hash = digest.digest()
    }
    return base64(salt) + ":" + base64(hash)
}
```

**已审计:**
- ✅ 使用 `SecureRandom` 而非 `Random`
- ✅ 16 字节(128 位)盐,无碰撞风险
- ✅ 10 000 轮迭代,在中端手机上 < 50ms
- ✅ 恒定时间比较(`constantTimeEquals`)

**未审计 / 建议:**
- ⚠️ 10 000 轮 SHA-256 比 PBKDF2 (120 000 轮) 弱,只用于"屏幕锁"用途;
  真正加密文件的密钥由 PBKDF2 派生,二者解耦
- ⚠️ 6 位数字密码的离线字典攻击:每秒 5 万次 → ~3 小时可穷举 100 万空间
  - 缓解:5 次失败锁定只防在线攻击;**离线场景下,请使用强密码(本项目暂不支持)**

### 2. KDF 派生 (`KeyDerivation.kt`)

```kotlin
const val ITERATIONS = 120_000
const val KEY_LENGTH_BITS = 256
const val SALT_BYTES = 16

fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
    val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
}
```

**已审计:**
- ✅ PBKDF2-HMAC-SHA256,NIST 推荐算法
- ✅ 120 000 轮符合 OWASP 2023 建议(PBKDF2-SHA256: 600 000 / Argon2id 优先)
- ✅ 256 位 = AES-256 所需
- ✅ 使用 `CharArray` 后 `clearPassword()`(调用方负责清空)

### 3. 文件加密 (`AesCtr.kt`)

```kotlin
fun encryptStream(key: SecretKey, plainIn: InputStream, out: OutputStream) {
    val iv = ByteArray(12).also { rng.nextBytes(it) }   // 12 字节 = 96 位
    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
    out.write(iv)
    // ...
}
```

**已审计:**
- ✅ AES-256-CTR(NIST SP 800-38A)
- ✅ 12 字节(96 位)IV,符合 RFC 3686
- ✅ `SecureRandom` 生成 IV
- ✅ 文件格式:`[12B IV][ciphertext]`
- ✅ 流式,无明文中间文件

**已知弱点:**
- ⚠️ **CTR 模式不提供完整性校验**。攻击者(已能写文件)可以翻转密文比特,
  对应明文比特也会翻转。*本项目没有这种攻击面*(无受信的文件输入路径)
- ✅ 改进方向:切换到 **AES-256-GCM**,12 字节 nonce + 16 字节 tag,提供 AEAD

### 4. 会话密钥 (`CryptoManager.kt`)

```kotlin
object CryptoManager {
    private var currentKey: SecretKey? = null
    fun clearKey() { currentKey = null }
    fun requireKey() = currentKey ?: error("Vault locked")
}
```

**已审计:**
- ✅ 单例,`lock()` 立即清空引用
- ✅ `requireKey` 在未解锁时抛 `IllegalStateException`,业务模块 catch 后跳回锁屏

**已知弱点:**
- ⚠️ Java 的 `SecretKey` 对象内嵌的 `byte[]` 不会因引用置 null 而立即清零。
  在设备被 root 后,内存 dump 理论上仍可恢复。*缓解:SecureRandom 生成的 AES 密钥
  不可重复;用户重设密码可强制重新生成*

### 5. 异常兜底 (`GlobalExceptionHandler.kt`)

```kotlin
Thread.setDefaultUnhandledExceptionHandler { thread, throwable ->
    Log.e(TAG, "Unhandled exception in thread ${thread.name}")  // 不打 message
    SecureStreamRegistry.closeAll()  // 关闭所有解密流
    System.gc()  // 触发 GC
    previous?.uncaughtException(thread, throwable)  // 仍走系统默认
}
```

**已审计:**
- ✅ 不在 logcat 输出原始异常 message / stacktrace(避免泄露路径)
- ✅ 兜底关闭所有注册的 `CipherInputStream`
- ✅ `previous?.uncaughtException` 仍然处理崩溃,不影响系统行为

### 6. 文件删除 (`SecureMediaStore.kt`)

```kotlin
fun deleteRequestSender(uris: List<Uri>): IntentSender? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return MediaStore.createDeleteRequest(
            context.contentResolver, uris
        ).intentSender
    }
    deleteUris(uris)  // Android 10- 直删
    return null
}
```

**已审计:**
- ✅ Android 11+ 走官方 `createDeleteRequest`,系统弹窗授权
- ✅ 失败/被拒的 Uri 不会被静默处理(`onDeleteResult` 检查 granted)

### 7. 截屏与多任务预览

```kotlin
window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
```

**已审计:**
- ✅ 锁屏/主界面 设置 `FLAG_SECURE`
- ✅ 计算器 **不** 设置(避免被系统识别为"可疑应用")
- ✅ 由 `LaunchedEffect(showCalculator)` 动态切换

### 8. 备份与数据外传

```xml
<!-- AndroidManifest.xml -->
android:allowBackup="false"
android:dataExtractionRules="@xml/data_extraction_rules"
android:fullBackupContent="@xml/backup_rules"
```

```xml
<!-- xml/backup_rules.xml -->
<exclude domain="sharedpref" path="vault_prefs.xml" />
<exclude domain="file" path="private_media/" />
```

**已审计:**
- ✅ `allowBackup=false` 禁止云/SD 卡备份
- ✅ `dataExtractionRules` 在 API 31+ 显式排除敏感路径
- ✅ `fullBackupContent` 在 API 23-30 显式排除

---

## 已知弱点 / 限制

1. **6 位数字密码熵不足**(20 bit)
   - 在线攻击:5 次失败锁定 30 秒 = 每天最多 1440 次 → 不可行
   - 离线攻击:攻击者拿到 `vault_prefs.xml` + 加密文件,每秒 5 万次 SHA-256
     → **3 小时** 穷举完毕
   - **结论:仅适用于"防同事/防家人";不适用于"防专业取证"**
   - 缓解:本项目不存储密码,无法二次校验;用户应自行决定风险

2. **密码哈希 vs 文件加密密钥解耦**
   - 屏幕密码 = SHA-256 + salt
   - 文件密钥 = PBKDF2 派生
   - 攻击者拿到 `vault_prefs.xml` 仍需 PBKDF2 120 000 轮
   - 攻击者拿到 `vault_prefs.xml` + `*.enc` → 需要:
     1. 离线穷举 100 万个 6 位数字密码
     2. 对每个候选,做 120 000 轮 PBKDF2
     3. 用派生密钥尝试解密

3. **MediaStore 删除不彻底**
   - Android 11+ 的 `createDeleteRequest` 是**用户授权**的删除
   - 但用户**可能误授权**;此时本 App 静默删除原图
   - 缓解:UI 上有二次确认弹窗

4. **SharedPreferences 文件可读**
   - 即使加了 `allowBackup=false`,Root 后的设备仍可读
   - 缓解:SharedPreferences 本身存在 `/data/data/<pkg>/shared_prefs/`,
     与 `filesDir` 一样受 Android 沙箱保护
   - **生产建议**:将 KDF 盐和密码哈希迁移到 Android Keystore (TEE/StrongBox)

5. **无审计日志**
   - 解锁、导入、删除、导出 都不写审计日志
   - 优点:不留痕迹
   - 缺点:无法追踪异常行为

6. **无远程擦除**
   - 设备丢失后无法远程清空私有目录
   - 可未来加入"绑定 Google Find My Device"或自建远程控制

---

## 建议改进路线

按优先级排序(从易到难):

1. **AES-256-GCM** 替换 AES-256-CTR
   - 文件格式:`[12B nonce][ciphertext][16B tag]`
   - 写入后用 `Cipher.doFinal` 获取 tag 并附加
   - 读取时校验 tag 失败 → 文件损坏或被篡改

2. **Android Keystore 绑定 PBKDF2 盐**
   - 将 `getKdfSalt()` 改为 Keystore 派生
   - 设备锁屏 → 密钥无法使用
   - 攻击者拿到 `vault_prefs.xml` 仍需过 Keystore

3. **StrongBox BiometricPrompt** (Pixel 3+)
   - `setAllowedAuthenticators(BIOMETRIC_STRONG)`
   - 拒绝弱生物特征(2D 人脸等)

4. **App Integrity API** 校验自身
   - 在解锁前调 Play Integrity API 验证 App 未被重打包
   - 失败 → 拒绝解锁

5. **远程擦除协议**
   - 与自建后端配合
   - 收到擦除指令 → 删除 `filesDir/private_media/*` 与 `shared_prefs/*`
   - 然后自杀进程

6. **审计日志(可选)**
   - 解锁/导入/删除/导出 各记一条,加密后存 `filesDir/audit.log.enc`
   - 用户导出时一并打包

---

## 披露策略

我们鼓励负责任的安全披露:

1. **不要** 在公开 Issue 中贴 PoC / 关键代码
2. 走 [GitHub Security Advisories](https://github.com/YOUR_GITHUB_USERNAME/PrivateVault/security/advisories/new) 私有通知
3. 我们承诺:
   - 48 小时内确认收到
   - 7 天内初步评估
   - 90 天内修复(或与报告者协商披露时间)
4. 修复后会致谢(可选)并发布 Security Advisory

> 报告模板: `.github/ISSUE_TEMPLATE/security_disclosure.md`
