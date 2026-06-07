# API Reference

> 本文档给"想扩展 PrivateVault 功能的开发者"用。
> 列出主要的可复用 API,以及"加新功能时调用什么"。

## 目录

- [加密原语](#加密原语)
- [仓库 (Repository)](#仓库-repository)
- [UI 扩展点](#ui-扩展点)
- [添加新文件类型](#添加新文件类型)
- [添加新伪装模式](#添加新伪装模式)
- [添加新导出格式](#添加新导出格式)

---

## 加密原语

### `KeyDerivation`

```kotlin
// 一次性从密码派生 AES 密钥
val salt = KeyDerivation.generateSalt()  // 16 字节
val key: SecretKey = KeyDerivation.deriveKey(
    password = "123456".toCharArray(),
    salt = salt
)
// key.encoded = 32 字节 AES-256 密钥
```

### `AesCtr`

```kotlin
// 文件加密
AesCtr.encryptStream(key, inputStream, outputStream)

// 文件解密
AesCtr.decryptStream(key, encryptedInputStream, outputStream)

// 流式解密(用于 ExoPlayer)
val cipherStream = AesCtr.openDecryptStream(key, encryptedInputStream)
try {
    // 把 cipherStream 喂给播放器
} finally {
    cipherStream.close()  // 触发 SecureStreamRegistry.unregister
}
```

### `PasswordHasher`

```kotlin
val hasher = PasswordHasher()
val hash: String = hasher.hashPassword("123456")  // base64(salt):base64(hash)
val ok: Boolean = hasher.verifyPassword("123456", hash)
```

### `CryptoManager` (单例)

```kotlin
import com.example.privatevault.security.CryptoManager

// 解锁后(锁屏成功):
CryptoManager.setKey(derivedKey)

// 加锁时(用户离开 / 切回锁屏):
CryptoManager.clearKey()

// 业务模块:
val key = CryptoManager.requireKey()  // 未解锁时抛 IllegalStateException
```

### `SecureStreamRegistry`

```kotlin
val cis = AesCtr.openDecryptStream(key, input)  // 自动 register
try {
    // 使用 cis
} finally {
    cis.close()  // 自动 unregister
}
// 崩溃路径 GlobalExceptionHandler 会调 SecureStreamRegistry.closeAll()
```

---

## 仓库 (Repository)

### `PasswordRepository`

```kotlin
val repo = (context.applicationContext as PrivateVaultApp).passwordRepository

if (repo.isPasswordSet()) { /* 已设置过 */ }

// 设置密码(首次)
val key = repo.setPassword("123456")
CryptoManager.setKey(key)

// 验证密码
val key = repo.verifyPassword("123456")
if (key != null) {
    CryptoManager.setKey(key)
    repo.resetFailures()
}

// 失败管理
repo.recordFailure()                   // 计数 +1
val remainMs: Long = repo.remainingLockMs()  // 距离可重试的毫秒
val count: Int = repo.currentFailCount()

// 伪装开关
val enabled: Boolean = repo.disguiseEnabled()
repo.setDisguiseEnabled(false)
```

### `VaultRepository`

```kotlin
val vault = (context.applicationContext as PrivateVaultApp).vaultRepository

// 列出某类型文件
val images: List<MediaItem> = vault.list(MediaType.IMAGE)
val videos: List<MediaItem> = vault.list(MediaType.VIDEO)

data class MediaItem(
    val file: File,
    val type: MediaType,
    val sizeBytes: Long,
    val modifiedAt: Long
)

// 创建新加密文件
val target: File = vault.newEncryptedFile(MediaType.IMAGE, suffix = ".enc")
// target 路径: filesDir/private_media/images/1718..._abcd.enc

// 删除
vault.deleteItem(item)

// 子目录 .nomedia
vault.ensureNoMedia()
```

### `SecureMediaStore`

```kotlin
val sms = (context.applicationContext as PrivateVaultApp).secureMediaStore

// 打开输入流
val input: InputStream? = sms.openInput(uri)

// Android 11+ 删除
val sender: IntentSender? = sms.deleteRequestSender(listOf(uri1, uri2))
// 旧版本返回 null (已直接删完)
// Android 11+ 调用方需用 ActivityResultLauncher 启动 sender

// Android 10- 直接删
val n: Int = sms.deleteUris(listOf(uri1, uri2))
```

---

## UI 扩展点

### `AppNavGraph` 加新路由

```kotlin
// ui/AppNavGraph.kt

object Routes {
    const val LOCK = "lock"
    const val MAIN = "main"
    const val VIEWER = "viewer/{type}/{file}"
    const val SETTINGS = "settings"  // 新增
    fun viewer(type: String, file: String) = "viewer/$type/${URLEncoder.encode(file, "UTF-8")}"
    fun settings() = "settings"
}

NavHost(...) {
    composable(Routes.SETTINGS) {
        SettingsScreen(
            onDisguiseChanged = { /* 通知 ViewModel */ }
        )
    }
    // ...
}
```

### `MainScreen` 加新 Tab

```kotlin
// ui/main/MainScreen.kt

private enum class MainTab { IMAGES, VIDEOS, AUDIOS, SETTINGS }  // 加 AUDIOS

// 在 NavigationBar 加
NavigationBarItem(
    selected = tab == MainTab.AUDIOS,
    onClick = { tab = MainTab.AUDIOS },
    icon = { Icon(Icons.Default.MusicNote, null) },
    label = { Text("音频") }
)

// 在 Scaffold body 加
when (tab) {
    MainTab.AUDIOS -> MediaGrid(...)
    // ...
}
```

### `MediaViewModel` 加新文件类型

```kotlin
// ui/main/MediaViewModel.kt

private val _audios = MutableStateFlow<List<MediaItem>>(emptyList())
val audios: StateFlow<List<MediaItem>> = _audios.asStateFlow()

fun refresh() {
    _images.value = vault.list(MediaType.IMAGE)
    _videos.value = vault.list(MediaType.VIDEO)
    _audios.value = vault.list(MediaType.AUDIO)  // 新增
}

private fun guessType(uri: Uri): MediaType? {
    val mime = context.contentResolver.getType(uri) ?: return null
    return when {
        mime.startsWith("video/") -> MediaType.VIDEO
        mime.startsWith("image/") -> MediaType.IMAGE
        mime.startsWith("audio/") -> MediaType.AUDIO  // 新增
        else -> null
    }
}
```

### 加 `VaultRepository.MediaType.AUDIO`

```kotlin
// data/VaultRepository.kt
enum class MediaType(val subdir: String) {
    IMAGE("images"),
    VIDEO("videos"),
    AUDIO("audios")  // 新增
}
```

---

## 添加新文件类型

完整流程(以"音频"为例):

### 1. 数据层

```kotlin
// data/VaultRepository.kt
enum class MediaType(val subdir: String) {
    IMAGE("images"),
    VIDEO("videos"),
    AUDIO("audios")  // 新增
}
```

### 2. ViewModel

```kotlin
// ui/main/MediaViewModel.kt
private val _audios = MutableStateFlow<List<MediaItem>>(emptyList())
val audios: StateFlow<List<MediaItem>> = _audios.asStateFlow()

fun refresh() {
    _audios.value = vault.list(MediaType.AUDIO)
}
```

### 3. UI

```kotlin
// ui/main/MainScreen.kt
private enum class MainTab { IMAGES, VIDEOS, AUDIOS, SETTINGS }

// 加 Tab、NavigationBarItem、Scaffold 内的 when 分支
```

### 4. 播放器(可选,音频不需要新播放器)

- 如果是图片:用 `BitmapFactory.decodeByteArray`
- 如果是视频:用 `MediaMetadataRetriever` 抽帧
- 如果是音频:用 `MediaPlayer` / ExoPlayer

> 注意:音频文件不需要缩略图,可直接显示文件名+大小。

---

## 添加新伪装模式

> 假设你想加一个"记事本"伪装,触发序列是打开菜单后点 5 下角落。

### 1. 新建 Composable

```kotlin
// ui/notepad/NotepadScreen.kt
@Composable
fun NotepadScreen(onSecretUnlocked: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var cornerTaps by remember { mutableStateOf(0) }
    
    Column {
        // 假装是普通记事本
        OutlinedTextField(value = text, onValueChange = { text = it })
        
        // 角落 5 连击触发
        Box(modifier = Modifier
            .size(40.dp)
            .clickable {
                cornerTaps++
                if (cornerTaps >= 5) {
                    cornerTaps = 0
                    onSecretUnlocked()
                }
            }
        )
    }
}
```

### 2. 在 `MainActivity` 加分支

```kotlin
// ui/MainActivity.kt
// 假装 prefs 加了 "disguise_mode" 字段
val mode = prefs.disguiseMode()  // "calculator" / "notepad" / "none"
when (mode) {
    "calculator" -> CalculatorScreen(onSecretUnlocked = { showCalculator = false })
    "notepad"    -> NotepadScreen(onSecretUnlocked = { showCalculator = false })
    "none"       -> AppNavGraph(lockViewModel = lockViewModel)
    else         -> AppNavGraph(lockViewModel = lockViewModel)
}
```

### 3. 加新 alias(可选)

```xml
<!-- AndroidManifest.xml -->
<activity-alias
    android:name=".ui.NotepadAliasActivity"
    android:targetActivity=".ui.MainActivity"
    android:icon="@mipmap/ic_launcher_notepad"
    android:label="@string/notepad_label"
    android:enabled="false">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity-alias>
```

---

## 添加新导出格式

### 1. 创建 Exporter 接口

```kotlin
// ui/settings/Exporters.kt
interface VaultExporter {
    suspend fun export(
        ctx: Context,
        password: String,
        vault: VaultRepository,
        output: OutputStream
    ): Boolean
}
```

### 2. 实现 zip 导出(已有的 `EncryptedExporter` 可重构为这个接口)

```kotlin
// ui/settings/EncryptedZipExporter.kt
class EncryptedZipExporter : VaultExporter {
    override suspend fun export(
        ctx: Context,
        password: String,
        vault: VaultRepository,
        output: OutputStream
    ): Boolean {
        val salt = ...
        val iv = ...
        val key = KeyDerivation.deriveKey(password.toCharArray(), salt)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        
        // 头
        output.write("PVLT".toByteArray())
        output.write(salt)
        output.write(iv)
        
        // zip
        CipherOutputStream(output, cipher).use { cout ->
            ZipOutputStream(cout).use { zout ->
                val items = vault.list(MediaType.IMAGE) + vault.list(MediaType.VIDEO)
                items.forEach { item ->
                    zout.putNextEntry(ZipEntry("${item.type.subdir}/${item.file.name}"))
                    item.file.inputStream().use { it.copyTo(zout) }
                    zout.closeEntry()
                }
            }
        }
        return true
    }
}
```

### 3. 实现明文 zip 导出(用于跨 App 迁移,**默认禁用,需在设置里手动开启**)

```kotlin
class PlainZipExporter : VaultExporter {
    override suspend fun export(...): Boolean {
        ZipOutputStream(output).use { zout ->
            val items = vault.list(...)
            items.forEach { item ->
                val key = CryptoManager.requireKey()
                zout.putNextEntry(ZipEntry("..."))
                // 解密 + 写明文
                val cis = AesCtr.openDecryptStream(key, item.file.inputStream())
                cis.copyTo(zout)
                cis.close()
                zout.closeEntry()
            }
        }
        return true
    }
}
```

### 4. 在 `SettingsScreen` 加切换

```kotlin
// SettingsScreen.kt
var selectedExporter by remember { mutableStateOf("encrypted") }
// 在 UI 加一个 Spinner / SegmentedButton
```

---

## 性能与安全边界

### 大文件导入

- 当前实现:64 KB 缓冲区流式加密 → 100 MB 视频约 +1 秒开销
- 改进方向:`BufferedInputStream` + `DirectByteBuffer` 减少 GC

### 大文件播放

- 当前实现:`DecryptingDataSource` 实时解密
- ExoPlayer 自带 4 MB 缓冲区,解密开销 < 播放延迟
- 4K HEVC 视频在高端设备上没问题,低端设备可能解码跟不上

### 内存占用

- 单张缩略图:< 1 MB(512x512 RGB)
- 1000 张缩略图:约 1 GB(应分页 LazyColumn)
- 视频缩略图:同图片大小

> LazyVerticalGrid 已经分页,内存只与可见项相关。

### 多用户

- 当前实现:单用户单密码
- 扩展:在 SharedPreferences 用 `pwd_hash_<userId>` 命名空间
- CryptoManager 切换用户时 `clearKey()` + `setKey(newKey)`
