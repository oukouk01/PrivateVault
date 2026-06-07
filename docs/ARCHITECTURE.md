# Architecture

> 本文档从代码层面详细解释 PrivateVault 的整体架构、模块划分、关键数据流与生命周期管理。
> 阅读对象:想理解 / 修改 / 扩展本项目的开发者。

## 目录

- [设计原则](#设计原则)
- [顶层结构](#顶层结构)
- [包与模块](#包与模块)
- [核心类速查](#核心类速查)
- [应用生命周期](#应用生命周期)
- [Activity 生命周期](#activity-生命周期)
- [导航图](#导航图)
- [数据流](#数据流)
- [线程模型](#线程模型)
- [依赖注入(手动)](#依赖注入手动)
- [扩展点](#扩展点)

---

## 设计原则

1. **明文不落盘** —— 任何时刻,Media 文件都不以明文形式存在于应用的 cacheDir / filesDir 之外;
   解密只在内存流中完成。
2. **会话密钥最小暴露** —— `CryptoManager` 是单例,持有当前解锁状态的 `SecretKey`,
   `lock()` 立即清空引用。
3. **失败快速反馈** —— 密码错误 / 文件损坏 / 权限拒绝 都通过 `StateFlow` 立即反映到 UI,
   不依赖 Toast 队列。
4. **跨版本兼容** —— 任何对 API 30+ 的调用都有 SDK_INT 守卫;
   `SecureMediaStore.deleteRequestSender` 是关键适配点。
5. **崩溃不泄露** —— `GlobalExceptionHandler` 在崩溃路径关闭所有解密流并触发 GC。

---

## 顶层结构

```
PrivateVault/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/
│       └── java/com/example/privatevault/
│           ├── PrivateVaultApp.kt
│           ├── data/
│           ├── security/
│           └── ui/
│                   ├── MainActivity.kt
│                   ├── AppNavGraph.kt
│                   ├── DisguiseController.kt
│                   ├── calculator/
│                   ├── lock/
│                   ├── main/
│                   ├── settings/
│                   └── theme/
├── docs/
│   ├── ARCHITECTURE.md
│   ├── BUILD.md
│   ├── CRYPTO.md
│   ├── SECURITY.md
│   ├── USER_GUIDE.md
│   ├── MODULE_MAP.md
│   └── API.md
├── .github/
│   ├── workflows/build.yml
│   ├── workflows/lint.yml
│   └── ISSUE_TEMPLATE/
├── LICENSE
├── README.md
├── CHANGELOG.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── gradlew
```

---

## 包与模块

### `data/`

| 文件 | 角色 |
| --- | --- |
| `PasswordRepository.kt` | `SharedPreferences` 封装,负责:密码哈希存储/校验、KDF 盐管理、失败计数、伪装开关 |
| `VaultRepository.kt` | 私有媒体目录的元数据管理:`images/`、`videos/`、`.nomedia` 守护 |

> 详见 [MODULE_MAP.md](MODULE_MAP.md#data)。

### `security/`

| 文件 | 角色 |
| --- | --- |
| `PasswordHasher.kt` | 单向哈希:`SHA-256(salt ‖ password)` + 10 000 轮迭代 |
| `KeyDerivation.kt` | PBKDF2-HMAC-SHA256 派生 AES-256 密钥,120 000 轮 |
| `AesCtr.kt` | AES-256-CTR 文件流加解密,12 字节 IV |
| `CryptoManager.kt` | 单例:在内存中保存当前会话 `SecretKey` |
| `SecureStreamRegistry.kt` | 注册所有解密流,异常路径统一 `closeAll` |
| `SecureMediaStore.kt` | 跨版本 `MediaStore` 删除适配 |
| `GlobalExceptionHandler.kt` | 兜底异常处理 |

> 密码学细节见 [CRYPTO.md](CRYPTO.md)。

### `ui/`

```
ui/
├── MainActivity.kt            // FragmentActivity 入口
├── AppNavGraph.kt             // NavHost + 路由常量
├── DisguiseController.kt      // 切换桌面 alias
├── calculator/
│   └── CalculatorScreen.kt
├── lock/
│   ├── LockScreen.kt
│   ├── LockViewModel.kt
│   └── BiometricAvailability.kt
├── main/
│   ├── MainScreen.kt
│   ├── MediaViewModel.kt
│   ├── MediaViewerScreen.kt
│   ├── ThumbnailDecoder.kt
│   ├── DecryptingDataSourceFactory.kt
│   └── ByteArrayMediaDataSource.kt
├── settings/
│   ├── SettingsScreen.kt
│   └── EncryptedExporter.kt
└── theme/
    ├── Color.kt
    ├── Theme.kt
    └── Type.kt
```

---

## 核心类速查

### `PrivateVaultApp`

```kotlin
class PrivateVaultApp : Application() {
    val applicationScope: CoroutineScope
    val cryptoManager: CryptoManager
    val passwordRepository: PasswordRepository
    val vaultRepository: VaultRepository
    val secureMediaStore: SecureMediaStore

    override fun onCreate() {
        super.onCreate()
        instance = this
        GlobalExceptionHandler.install()
        DisguiseController.setDisguise(this, passwordRepository.disguiseEnabled())
        applicationScope.launch { vaultRepository.ensureNoMedia() }
    }
}
```

- **单例访问**:任何模块可通过 `LocalContext.current.applicationContext as PrivateVaultApp` 获取
- **`applicationScope`**:`SupervisorJob + Dispatchers.IO`,用于 Application 级后台任务
- **`.nomedia`** 在 `onCreate` 后台执行,避免阻塞主线程

### `MainActivity`

```kotlin
class MainActivity : FragmentActivity() {
    private val lockViewModel: LockViewModel by viewModels()
    private val resumeTick = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. 监听前台恢复
        // 2. setContent { ... }
        // 3. 根据 disguiseEnabled 决定 CalculatorScreen / AppNavGraph
    }

    override fun onStop()  { lockViewModel.lock() }   // 上锁
    override fun onStart() { resumeTick.value++ }     // 触发 recompose
}
```

为什么是 `FragmentActivity`?  因为 `BiometricPrompt` 需要 `FragmentActivity`。
`viewModels()` 来自 `androidx.activity.viewModels`,对 `ComponentActivity` 都适用。

### `LockViewModel`

```kotlin
class LockViewModel(app: Application) : AndroidViewModel(app) {
    enum class Mode { SET, CONFIRM, VERIFY }
    data class State(
        val mode: Mode,
        val firstInput: String,
        val password: String,
        val error: String?,
        val remainingLockMs: Long,
        val attemptsLeft: Int
    )

    val state: StateFlow<State>
    val isUnlockedFlow: StateFlow<Boolean>

    fun onDigit(d: Char)
    fun onBackspace()
    fun refreshLockInfo()
    fun requestUnlock()
    fun lock()
}
```

- **`Mode.SET`** —— 第一次设置密码,要求输入两次
- **`Mode.CONFIRM`** —— 第二次输入,与 `firstInput` 比对
- **`Mode.VERIFY`** —— 已设置过密码,验证输入
- **`refreshLockInfo()`** —— 用于重试后重新读失败计数

### `MediaViewModel`

```kotlin
class MediaViewModel(app: Application) : AndroidViewModel(app) {
    val images: StateFlow<List<MediaItem>>
    val videos: StateFlow<List<MediaItem>>
    val busy: StateFlow<Boolean>
    val pendingDelete: StateFlow<List<Uri>>

    fun refresh()
    fun importUris(uris, deleteOriginals, onNeedDeletePermission)
    fun onDeleteResult(granted)
    fun exportToGallery(item, onResult)
}
```

- **`importUris`** 把加密拷贝 + 删除原图拆成两步,删除需要系统弹窗授权
- **`onDeleteResult`** 由 `ActivityResultLauncher` 回调触发

### `CryptoManager`

```kotlin
object CryptoManager {
    fun setKey(key: SecretKey)
    fun clearKey()
    fun requireKey(): SecretKey  // 未解锁时抛异常
}
```

- **单例** 在 `lock()` 时清空引用。Java GC 不会立即清空原内存,但 SecretKey 的字节已经无法被访问。
- **`requireKey`** 在主线程不会阻塞,只是检查 null;业务模块应在 IO 协程中调用。

### `SecureStreamRegistry`

```kotlin
object SecureStreamRegistry {
    fun register(c: Closeable)
    fun unregister(c: Closeable)
    fun closeAll()  // 异常路径兜底
}
```

- 注册 `CipherInputStream` 实例
- `GlobalExceptionHandler` 在崩溃时调 `closeAll()`

---

## 应用生命周期

```
┌─────────────────────────────────────────────────────┐
│ Process start                                       │
│  │                                                  │
│  ▼                                                  │
│ PrivateVaultApp.onCreate()                          │
│  ├─ install GlobalExceptionHandler                  │
│  ├─ DisguiseController.setDisguise(prefs.enabled)   │
│  └─ applicationScope.launch { ensureNoMedia() }    │
│  │                                                  │
│  ▼                                                  │
│ MainActivity.onCreate()                             │
│  ├─ viewModels<LockViewModel>()                     │
│  └─ setContent { ... }                              │
└─────────────────────────────────────────────────────┘
```

`onCreate` 不会自动上锁(因为 `lockViewModel` 初始 `isUnlocked = false`),
但只要 `onStop` 被调用过(用户离开),下一次 `onStart` 就会触发 recompose,
`LockViewModel.lock()` 会确保 `isUnlocked = false`。

---

## Activity 生命周期

```
            ┌────────────────────────────────────┐
            │         MainActivity               │
            └────────────────────────────────────┘
                ▲              ▲             │
   onCreate ───┘              │             │
                              │             │
   onStart  ───────────────────             │
   ↑  └─ resumeTick++                        │
   │  └─ Compose 重新计算 showCalculator     │
   │     与 lockViewModel.lock()             │
   │                                        │
   (User interacts)                         │
   │                                        │
   onStop   ────────────────────────────────┘
   ↑  └─ lockViewModel.lock() → CryptoManager.clearKey()
   │  └─ 再 onStart 时 showCalculator 重新计算
```

### `onStop` 立即上锁

```kotlin
override fun onStop() {
    super.onStop()
    lockViewModel.lock()
}
```

`lock()` 会:
1. `CryptoManager.clearKey()` —— 清空 `currentKey` 引用
2. `_isUnlocked.value = false`

这意味着用户离开 App 后,内存中**没有 AES 密钥**,即使 dumpsys 内存也找不到。

### `onStart` 重新检查

```kotlin
override fun onStart() {
    super.onStart()
    resumeTick.value = resumeTick.value + 1
}
```

`resumeTick` 是 Compose 状态,变化时:
1. `remember(tick) { ... }` 重新计算 `showCalculator`
2. `LaunchedEffect(tick) { if (tick > 0) lockViewModel.lock() }` 兜底加锁

---

## 导航图

```
NavHost(startDestination = if (unlocked) MAIN else LOCK) {
    composable(LOCK)   { LockScreen(onUnlocked = navTo(MAIN, popUpTo LOCK inclusive)) }
    composable(MAIN)   { MainScreen(onOpenViewer, onLocked) }
    composable(VIEWER) { MediaViewerScreen(type, fileName, onBack) }
}
```

> 注意:`Routes.viewer(type, fileName)` 用 `URLEncoder.encode` 避免文件名中的 `/` 触发
> 路由解析错误。

---

## 数据流

### 1. 密码设置 (首次启动)

```
User tap 6 digits
    ↓
LockScreen.onDigit(d) (Composition event)
    ↓
LockViewModel.onDigit(d)
    ↓
_state.value = state.copy(password = "...")
    ↓ if length == 6
LockViewModel.submit() (coroutine)
    ↓
if (mode == SET) → state.copy(mode = CONFIRM, firstInput = "...")
if (mode == CONFIRM && password == firstInput) →
    PasswordRepository.setPassword(pwd)        // SHA-256+salt 存 prefs
    KeyDerivation.deriveKey(...)                // PBKDF2 派生
    CryptoManager.setKey(key)                   // 注入内存
    requestUnlock()                             // _isUnlocked = true
    ↓
LockScreen LaunchedEffect(isUnlocked)
    ↓ if true
onUnlocked()
    ↓
navController.navigate(MAIN, popUpTo LOCK inclusive)
```

### 2. 导入并删除原图

```
User tap FAB
    ↓
PhotoPicker.launch(PickVisualMediaRequest(ImageAndVideo))
    ↓ system
User selects N uris
    ↓
onResult(uris) → showDeleteConfirm = true
    ↓
User taps "确认"
    ↓
MediaViewModel.importUris(uris, deleteOriginals = true) {
    onNeedDeletePermission(IntentSender)
}
    ↓ coroutine
AesCtr.encryptStream(uri.inputStream, vault.newEncryptedFile().outputStream)
    ↓ success
SecureMediaStore.deleteRequestSender(uris)
    ↓ returns IntentSender
onNeedDeletePermission(intentSender)
    ↓
deleteSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
    ↓ system dialog
User taps "Delete" / "Cancel"
    ↓
deleteSenderLauncher callback → MediaViewModel.onDeleteResult(granted = true)
    ↓ coroutine
for (uri in pendingDelete) contentResolver.delete(uri, null, null)
    ↓
_pendingDelete.value = emptyList()
```

### 3. 视频播放

```
User tap video thumbnail
    ↓
navController.navigate(viewer/video/file.enc)
    ↓
MediaViewerScreen
    ↓
VideoPlayer(file)
    ↓
DecryptingDataSourceFactory(key, file).createDataSource()
    ↓
ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem)
    ↓
ExoPlayer.setMediaSource(...)
    ↓ start playback
DecryptingDataSource.open(dataSpec)
    ├─ FileInputStream(file)
    ├─ 跳过前 12 字节 IV
    └─ CipherInputStream(fs, cipher)
            ↓ on demand
ExoPlayer read → CipherInputStream.read → FileInputStream.read
            ↓
MediaCodec 解码 → SurfaceView 显示
```

---

## 线程模型

| 任务 | 线程 |
| --- | --- |
| Compose UI | Main |
| ViewModel 状态更新 | Main (`StateFlow.value =`) |
| `withContext(Dispatchers.IO)` 内的 I/O | IO Pool |
| ExoPlayer 解密 | ExoPlayer 内部 Loader thread |
| Photo Picker / MediaStore 删除 | 系统进程 |
| `applicationScope` 后台任务 | IO Pool |

> 规则:任何可能阻塞的操作都包在 `withContext(Dispatchers.IO)`,避免主线程 ANR。

---

## 依赖注入(手动)

> 本项目未引入 Hilt / Koin,而是采用"Application 持单例"模式。

```kotlin
// 业务模块获取仓库
val app = context.applicationContext as PrivateVaultApp
val vault = app.vaultRepository
val key = app.cryptoManager.requireKey()
```

扩展性:如果将来要拆分模块或换 DI 框架,把 `PrivateVaultApp` 中的 `val` 移到
`@Module @Provides` 即可,ViewModel 端的调用不需要改。

---

## 扩展点

| 想做的事 | 修改位置 |
| --- | --- |
| 加新 Tab (例如"音频") | `MainScreen.kt` 的 `MainTab` + `MainScreen` Composable + `MediaViewModel` 加 `audios` 流 |
| 加新加密算法 | `security/Crypto.kt` 包内新增 + `AesCtr` 旁路切换 |
| 加新伪装模式 | `ui/calculator/` 旁建 `ui/notepad/` + `DisguiseController` 加分支 |
| 加新导出格式 | `settings/EncryptedExporter.kt` 加新函数 |
| 加新权限请求 | `ui/main/MainScreen.kt` 加 `rememberLauncherForActivityResult` |
| 加新解密播放器 | 仿 `DecryptingDataSourceFactory` 实现 `DataSource.Factory` |

> 完整 API 见 [API.md](API.md)。
