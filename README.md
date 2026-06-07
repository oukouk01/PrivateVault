# PrivateVault

> 一个 Android 端的"私密空间"App,使用 **Jetpack Compose + Material3** 构建。
> 所有图片/视频在导入时使用 **AES-256-CTR + PBKDF2** 全量加密,
> 播放/查看时只在 **内存中解密**,**不产生明文临时文件**。

[![Platform: Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-blue)](https://developer.android.com/about/versions/oreo)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34-green)](https://developer.android.com/about/versions/14)
[![Language: Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![UI: Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![Security Audit](https://img.shields.io/badge/Security-Self--reviewed-blueviolet)](docs/SECURITY.md)

---

## 目录

- [功能总览](#功能总览)
- [截图占位](#截图占位)
- [快速开始](#快速开始)
- [技术栈](#技术栈)
- [架构概览](#架构概览)
- [安全模型](#安全模型)
- [文档导航](#文档导航)
- [路线图](#路线图)
- [贡献](#贡献)
- [许可证](#许可证)
- [致谢](#致谢)

---

## 功能总览

| 模块 | 能力 |
| --- | --- |
| 锁屏 | 6 位数字密码 · SHA-256 + 盐 · 5 次失败 30 秒锁定 · 指纹 BiometricPrompt |
| 加密 | AES-256-CTR · PBKDF2-HMAC-SHA256 (120 000 轮) · 256 位会话密钥 · 12 字节随机 IV |
| 存储 | 私有目录 `filesDir/private_media/{images,videos}/*.enc` · 永不写明文到磁盘 |
| 导入 | 系统 Photo Picker 多选 · 加密拷贝 · 二次确认 · MediaStore.createDeleteRequest 删原图 |
| 浏览 | 网格缩略图 · 全屏图片 (双指缩放 / 滑动切换) · ExoPlayer 解密流视频播放 |
| 还原 | 长按文件 → 流式解密 → 写回 DCIM/PrivateVault |
| 伪装 | 计算器 launcher alias · 123+456= 触发 · 设置一键关闭 |
| 安全细节 | 备份提醒 · 加密 zip 导出到 Download · `.nomedia` 保护 · GlobalExceptionHandler 崩溃兜底 |
| 生命周期 | onStop 自动上锁 · onStart 重新检查伪装 · FLAG_SECURE 锁屏时屏蔽截屏 |

---

## 截图占位

> 实际 UI 取决于你 build 的版本。下面 6 张截图是建议放置的位置。

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  计算器     │  │  锁屏       │  │  设置       │
│  (伪装)     │  │  (6 位输入) │  │  (备份/伪装)│
└─────────────┘  └─────────────┘  └─────────────┘

┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  主界面     │  │  全屏图片   │  │  视频播放   │
│  (图片网格) │  │  (双指缩放) │  │  (ExoPlayer)│
└─────────────┘  └─────────────┘  └─────────────┘
```

把 build 出来的截图放到 `docs/screenshots/` 目录,文件名与上面占位对应:

```
docs/screenshots/01_calculator.png
docs/screenshots/02_lock.png
docs/screenshots/03_settings.png
docs/screenshots/04_grid.png
docs/screenshots/05_image_viewer.png
docs/screenshots/06_video_player.png
```

---

## 快速开始

### 1. 环境要求

| 工具 | 最低版本 | 推荐 |
| --- | --- | --- |
| JDK | 17 | 17 (Temurin) |
| Android Studio | Hedgehog 2023.1.1 | Koala 2024.1.1+ |
| Android SDK | Platform 34 · Build-Tools 34.0.0 | 最新 |
| Gradle | 8.4 | 8.4 (由 wrapper 指定) |
| Kotlin | 1.9.22 | 1.9.22 |
| min SDK | 26 (Android 8.0 Oreo) | — |
| target SDK | 34 (Android 14) | — |

> 详细安装/排错请见 [docs/BUILD.md](docs/BUILD.md)。

### 2. 克隆 & 构建

```bash
git clone https://github.com/YOUR_GITHUB_USERNAME/PrivateVault.git
cd PrivateVault

# 用 Android Studio 打开,首次 Sync 会自动下载 gradle-wrapper.jar
# 也可以本地装 Gradle 8.4+ 后:
gradle wrapper --gradle-version 8.4

# 之后可以直接:
./gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

### 3. 安装到设备

```bash
# 打开 USB 调试后:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. 第一次使用

1. 启动 App,看到 **计算器** 界面
2. 输入 `1 2 3 + 4 5 6 =` 触发隐藏入口
3. 弹出 **设置 6 位数字密码** 界面,设置后再次输入确认
4. 进入主界面,开始导入

> 完整使用流程见 [docs/USER_GUIDE.md](docs/USER_GUIDE.md)。

---

## 技术栈

### 核心
- **Kotlin** 1.9.22 + **Jetpack Compose** (BOM 2024.02.00)
- **Material3** 1.2.x
- **AndroidX Lifecycle / Activity-Compose / Fragment-KTX**

### 多媒体
- **Coil 2.5.0** — 图片/视频帧加载
- **AndroidX Media3 / ExoPlayer 1.2.1** — 视频播放
- **MediaMetadataRetriever** — 视频缩略图抽帧
- **Android Photo Picker** (`PickMultipleVisualMedia`) — 无需 READ_MEDIA_* 权限

### 安全 / 存储
- **AndroidX Biometric 1.1.0** — `BiometricPrompt`
- **javax.crypto** — AES-256-CTR / PBKDF2-HMAC-SHA256
- **MediaStore** — 跨版本删除 (`createDeleteRequest`)

### 构建
- **Gradle 8.4** + **AGP 8.2.2**
- **KSP 1.9.22-1.0.17**
- **JDK 17** (`sourceCompatibility` / `targetCompatibility` / `jvmTarget`)

> 完整依赖列表见 [app/build.gradle.kts](app/build.gradle.kts)。

---

## 架构概览

```
┌──────────────────────────────────────────────────────────────────┐
│                        PrivateVaultApp                            │
│  · 初始化 GlobalExceptionHandler / .nomedia                       │
│  · 暴露 CryptoManager / PasswordRepository / VaultRepository …   │
└──────────────────┬───────────────────────────────────────────────┘
                   │
       ┌───────────┴───────────┐
       ▼                       ▼
  ┌────────────┐        ┌────────────────┐
  │  锁屏      │        │  计算器        │
  │  LockScreen│        │  CalculatorScrn│
  │  LockVM    │        │  (123+456=)    │
  └─────┬──────┘        └────────┬───────┘
        │ onUnlocked              │ onSecretUnlocked
        ▼                         ▼
       ┌─────────────────────────────┐
       │   AppNavGraph (NavHost)     │
       │  ┌────────┐  ┌────────┐    │
       │  │ MAIN   │  │ VIEWER │    │
       │  └────┬───┘  └────────┘    │
       └───────┼────────────────────┘
               ▼
   ┌──────────────────────────────────────┐
   │  MainScreen                          │
   │  BottomNav: 图片 / 视频 / 设置       │
   │  FAB: PhotoPicker → MediaViewModel   │
   │   ├─ importUris (加密拷贝)           │
   │   ├─ requestDeleteOriginals (Sender) │
   │   └─ exportToGallery (流式解密写回)  │
   └──────────────────────────────────────┘

   ┌─────────────────────┐
   │  SettingsScreen     │
   │  · 备份提醒卡       │
   │  · 加密 zip 导出    │
   │  · 伪装开关         │
   └─────────────────────┘
```

### 模块分层

| 包 | 职责 |
| --- | --- |
| `data/` | `PasswordRepository` 密码配置持久化 · `VaultRepository` 私有媒体目录 |
| `security/` | 加密原语 + 跨版本 MediaStore 适配 + 全局异常 |
| `ui/calculator/` | 计算器 UI + 隐藏触发器 |
| `ui/lock/` | 锁屏 ViewModel + Composable + BiometricPrompt |
| `ui/main/` | 主界面、网格、查看器、缩略图解码器、ExoPlayer 解密 DataSource |
| `ui/settings/` | 设置 + 加密导出 |
| `ui/theme/` | Material3 ColorScheme / Typography |
| `ui/` (root) | MainActivity + AppNavGraph + DisguiseController |

### 关键数据流 (导入)

```
┌──────────────┐    uri         ┌──────────────────┐
│ PhotoPicker  │ ─────────────▶ │ MediaViewModel   │
│ (系统组件)   │                │ .importUris()    │
└──────────────┘                └────────┬─────────┘
                                         │ AesCtr.encryptStream
                                         ▼
                                ┌──────────────────┐
                                │ filesDir/        │
                                │  private_media/  │
                                │   images/*.enc   │
                                └──────────────────┘
                                         │
                                         │ onSuccess
                                         ▼
                                ┌──────────────────┐
                                │ AlertDialog      │
                                │ "确认删除原文件?" │
                                └────────┬─────────┘
                                         │ user confirm
                                         ▼
                                ┌──────────────────┐
                                │ MediaStore       │
                                │ .createDelete    │
                                │  Request()       │
                                │ → IntentSender   │
                                └────────┬─────────┘
                                         │ ActivityResult
                                         ▼
                                ┌──────────────────┐
                                │ contentResolver  │
                                │ .delete(uri)     │
                                └──────────────────┘
```

---

## 安全模型

### 威胁模型

| 假设保护 | 假设不保护 |
| --- | --- |
| 设备落入他人手中 (无 root) | 设备已被 root / 安装了恶意 App 可读取内存 |
| 系统截图 / 录屏 / 多任务预览 | 设备基带 / 硬件级攻击 |
| 普通 App 访问公共相册 | 用户主动导出文件后明文外传 |
| 系统扫描 (`MediaScanner`) | 用户主动 `adb pull` 私有目录 |
| 应用卸载/数据清除后的残留 | 用户忘记 6 位密码 (无任何后门) |

### 加密栈

```
用户输入 6 位数字
    │
    ▼  PBKDF2-HMAC-SHA256 (120 000 轮, 16 字节随机盐)
SessionKey (AES-256)
    │
    ├─▶ AesCtr.encryptStream(file_in, file_out)
    │       │
    │       └─▶ [12 字节 IV] [ciphertext] 写入 private_media/
    │
    └─▶ DecryptingDataSource (ExoPlayer)
            └─▶ CipherInputStream → 实时解码
                    (内存中,关闭后立即失效)
```

### 多层防护

1. **密码单向哈希** — 即使 SharedPreferences 泄露,攻击者无法反推密码
2. **会话密钥内存化** — CryptoManager 仅在解锁后持有,`onStop`/`lock()` 立即清零
3. **每文件随机 IV** — 即使明文相同,密文也不可识别
4. **流式加解密** — 不生成明文中间文件,`SecureStreamRegistry` 异常路径兜底
5. **MediaStore 二次删除** — 系统弹窗授权后才彻底删除原图
6. **FLAG_SECURE 锁屏** — 屏蔽系统截屏与多任务预览
7. **GlobalExceptionHandler** — 崩溃时关闭所有解密流,`System.gc()` 触发
8. **`.nomedia`** — 防止任何 MediaScanner 扫描到加密文件

完整安全审计见 [docs/SECURITY.md](docs/SECURITY.md),
密码学实现细节见 [docs/CRYPTO.md](docs/CRYPTO.md)。

---

## 文档导航

| 文档 | 适合读者 |
| --- | --- |
| [README.md](README.md) *(本文件)* | 所有人 |
| [docs/USER_GUIDE.md](docs/USER_GUIDE.md) | 最终用户 |
| [docs/BUILD.md](docs/BUILD.md) | 想从源码构建的开发者 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 想理解代码结构的开发者 |
| [docs/MODULE_MAP.md](docs/MODULE_MAP.md) | 想知道"我应该改哪里"的开发者 |
| [docs/CRYPTO.md](docs/CRYPTO.md) | 想做密码学审计的安全研究员 |
| [docs/SECURITY.md](docs/SECURITY.md) | 想做整体安全评估的审计员 |
| [docs/API.md](docs/API.md) | 想要扩展功能 (导入/备份/还原) 的开发者 |
| [CHANGELOG.md](CHANGELOG.md) | 关注版本变更的发布经理 |
| [LICENSE](LICENSE) | 法务 / 合规 |

---

## 路线图

- [ ] **Android Keystore** 绑定指纹 (`StrongBox` 可用时优先)
- [ ] **加密备份还原** 桌面工具 (Python / Go),恢复 `.zip` 备份
- [ ] **AES-GCM** 选项(可提供完整性校验)
- [ ] **应用锁屏样式自定义**(纯色 / 背景图)
- [ ] **回收站** —— 长按"移出"前先临时移入回收站
- [ ] **多用户 / 多密码** 隔离不同私密空间
- [ ] **.nomedia** 之外的 **Scoped Storage** 强化
- [ ] **Compose UI 测试** + **Robolectric** 单元测试

详见 [GitHub Issues](../../issues)。

---

## 贡献

欢迎 PR 与 Issue!请先阅读:

- [CONTRIBUTING.md](CONTRIBUTING.md) *(待补充)*
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) *(待补充)*

提交前请确保:
- `./gradlew assembleDebug` 通过
- `./gradlew lintDebug` 无 ERROR 级别问题
- 涉及密码学的 PR 必须附 [docs/CRYPTO.md](docs/CRYPTO.md) 的更新说明

---

## 许可证

[MIT](LICENSE) © 2026 PrivateVault Contributors

附带额外的 [安全免责声明](LICENSE#L37-L60) — 在用于存储真正敏感数据前,
请自行审计代码。

---

## 致谢

- **Jetpack Compose** 团队 —— 让声明式 UI 在 Android 上如此顺手
- **ExoPlayer / Media3** 团队 —— 强大的可扩展播放引擎
- **Coil** —— Compose 原生图片加载
- 所有给本项目提过 Issue / PR / Star 的人 ⭐
