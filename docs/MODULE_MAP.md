# Module Map

> "我想加 X 功能,改哪里?" / "我看到 Y bug,看哪里?"
> 本文档按功能反向索引到代码位置。

## 目录

- [data/](#data)
- [security/](#security)
- [ui/](#ui)
- [resources/](#resources)
- [配置 / 清单](#配置--清单)
- [GitHub 配置](#github-配置)

---

## data/

| 文件 | 改这里如果你想… |
| --- | --- |
| `data/PasswordRepository.kt` | …改密码长度限制(目前 6 位) / 改失败次数 / 改锁定时长 / 改伪装默认值 |
| `data/VaultRepository.kt` | …改文件命名规则 / 改子目录结构 / 调整 `.nomedia` 行为 |

### 关键常量

```kotlin
// PasswordRepository.kt
const val MAX_FAILS = 5
const val LOCK_DURATION_MS = 30_000L
```

修改这些常量前请同步更新:
- `strings.xml` 中的 `locked_too_many_times` 文案
- `LockViewModel.kt` 的默认状态
- 本文档

---

## security/

| 文件 | 改这里如果你想… |
| --- | --- |
| `security/PasswordHasher.kt` | …改哈希算法 / 改迭代次数 |
| `security/KeyDerivation.kt` | …改 PBKDF2 迭代 / 改 KDF 算法(切到 Argon2id) |
| `security/AesCtr.kt` | …改加密算法(切到 GCM) / 改 IV 长度 |
| `security/CryptoManager.kt` | …改会话密钥生命周期 |
| `security/GlobalExceptionHandler.kt` | …自定义崩溃行为 / 加崩溃日志 |
| `security/SecureMediaStore.kt` | …改 MediaStore 删除策略 |
| `security/SecureStreamRegistry.kt` | …加更多资源追踪(比如 GL 纹理) |

### 加密算法升级路径

| 想升级 | 改 | 兼容性影响 |
| --- | --- | --- |
| SHA-256 → Argon2id | `PasswordHasher.kt` | 旧数据无法验证,需迁移 |
| PBKDF2 → Argon2id | `KeyDerivation.kt` | 同上 |
| CTR → GCM | `AesCtr.kt` + `data/VaultRepository.kt`(加 tag 长度 16) | **旧文件不兼容** |

> 任何"不兼容"升级都需要"双算法并行"过渡期:
> 1. 旧文件用旧算法解密后用新算法重新加密
> 2. 一段时间后(版本号+1)再彻底删除旧代码
> 推荐在 Prefs 加一个 `encryption_version` 字段。

---

## ui/

### `MainActivity` & `AppNavGraph`

| 文件 | 改这里如果你想… |
| --- | --- |
| `ui/MainActivity.kt` | …改启动流程 / 改 FLAG_SECURE 策略 / 加第二个 Activity |
| `ui/AppNavGraph.kt` | …加新页面路由 / 改默认目的地 |
| `ui/DisguiseController.kt` | …改桌面图标切换逻辑 |

### `ui/calculator/`

| 文件 | 改这里如果你想… |
| --- | --- |
| `ui/calculator/CalculatorScreen.kt` | …改触发序列 / 改计算器外观 / 改 UI 主题 |

### `ui/lock/`

| 文件 | 改这里如果你想… |
| --- | --- |
| `ui/lock/LockScreen.kt` | …改锁屏 UI / 改键盘布局 / 加新生物特征 |
| `ui/lock/LockViewModel.kt` | …改状态机(比如 SET → CONFIRM → VERIFY) |
| `ui/lock/BiometricAvailability.kt` | …改可用的生物特征类型(BIOMETRIC_STRONG 等) |

### `ui/main/`

| 文件 | 改这里如果你想… |
| --- | --- |
| `ui/main/MainScreen.kt` | …改 BottomNav / 改 FAB 行为 / 改网格列数 |
| `ui/main/MediaViewModel.kt` | …改导入流程 / 改删除授权 / 改导出策略 |
| `ui/main/MediaViewerScreen.kt` | …改全屏 UI / 改图片缩放手势 / 改视频播放控制 |
| `ui/main/ThumbnailDecoder.kt` | …改缩略图大小 / 改视频帧抽帧时间 |
| `ui/main/DecryptingDataSourceFactory.kt` | …改 ExoPlayer 数据源 |
| `ui/main/ByteArrayMediaDataSource.kt` | …改内存数据源实现 |

### `ui/settings/`

| 文件 | 改这里如果你想… |
| --- | --- |
| `ui/settings/SettingsScreen.kt` | …加新设置项 / 改备份文案 |
| `ui/settings/EncryptedExporter.kt` | …改导出格式 / 改压缩算法 / 改目标目录 |

### `ui/theme/`

| 文件 | 改这里如果你想… |
| --- | --- |
| `ui/theme/Color.kt` | …改品牌色 |
| `ui/theme/Theme.kt` | …改 dynamic color 行为 |
| `ui/theme/Type.kt` | …改字体 |

---

## resources/

| 路径 | 改这里如果你想… |
| --- | --- |
| `res/values/strings.xml` | …改所有用户可见的文本 |
| `res/values/colors.xml` | …改 XML 层引用的颜色(目前只在 themes 用) |
| `res/values/themes.xml` | …改 Activity 启动主题 |
| `res/drawable/ic_launcher_*.xml` | …改默认启动图标 |
| `res/drawable/ic_launcher_calc_*.xml` | …改计算器伪装图标 |
| `res/mipmap-anydpi-v26/ic_launcher.xml` | …改 Adaptive Icon 配置 |
| `res/xml/backup_rules.xml` | …改备份排除规则(API ≤ 30) |
| `res/xml/data_extraction_rules.xml` | …改备份排除规则(API 31+) |

### 加新图标

1. 准备 SVG/PNG 源文件(512x512 推荐)
2. 放 `res/drawable/your_icon.xml`(Vector) 或 `res/drawable/your_icon.png`
3. 在 `res/mipmap-anydpi-v26/ic_your_launcher.xml` 引用:
   ```xml
   <adaptive-icon>
       <background android:drawable="@drawable/your_bg" />
       <foreground android:drawable="@drawable/your_fg" />
   </adaptive-icon>
   ```
4. 在 `AndroidManifest.xml` 的 `android:icon="@mipmap/ic_your_launcher"`

---

## 配置 / 清单

| 文件 | 改这里如果你想… |
| --- | --- |
| `AndroidManifest.xml` | …改包名 / 改权限 / 改 Activity / 改桌面 alias |
| `app/build.gradle.kts` | …改依赖版本 / 改 minSdk / 改签名配置 |
| `build.gradle.kts` (root) | …改 AGP / Kotlin / KSP 版本 |
| `settings.gradle.kts` | …加新模块 |
| `gradle.properties` | …改 Gradle 内存 / 改 AndroidX 开关 |
| `gradle/wrapper/gradle-wrapper.properties` | …改 Gradle 版本 |
| `proguard-rules.pro` | …加 R8 规则 |

### 改包名

涉及 5 处:
1. `app/build.gradle.kts` → `namespace` / `applicationId`
2. `AndroidManifest.xml` → 所有 `.XxxActivity` 改前缀
3. 所有 `.kt` 文件的 `package` 行
4. 所有 `R.string.xxx` 引用(自动跟随)
5. 资源文件名(可选)

> 推荐:用 Android Studio 的 `Refactor > Rename` 一次性改。

### 改 minSdk

```kotlin
// app/build.gradle.kts
defaultConfig {
    minSdk = 26  // 改这里
}
```

注意:
- minSdk 升高 → 用户减少(但本项目已用 Photo Picker,API < 33 fallback 正常)
- minSdk 降低 → 需要处理更多 SDK 差异

---

## GitHub 配置

| 路径 | 改这里如果你想… |
| --- | --- |
| `.github/workflows/build.yml` | …改 CI 构建步骤 / 改触发条件 |
| `.github/workflows/lint.yml` | …改 lint 规则 / 改上传产物 |
| `.github/ISSUE_TEMPLATE/bug_report.md` | …改 bug 报告模板 |
| `.github/ISSUE_TEMPLATE/feature_request.md` | …改功能请求模板 |
| `.github/ISSUE_TEMPLATE/security_disclosure.md` | …改安全披露模板 |

### 启用 GitHub Pages(可选)

1. Settings → Pages
2. Source: `main` branch / `/docs` folder
3. 用户可以访问 `https://<username>.github.io/PrivateVault/`
4. 入口:`/docs/README.md` 或单独写一个 `docs/index.md`

---

## 跨文件依赖图(简化)

```
strings.xml
    ↑
    └─ 几乎所有 .kt 都引用 (R.string.xxx)

build.gradle.kts
    ↑
    └─ 加新依赖 / 改版本 → 必改

AndroidManifest.xml
    ↑
    ├─ MainActivity 类的全限定名必须与 android:name 一致
    ├─ CalculatorAliasActivity 是 activity-alias,targetActivity 必须是已存在的 Activity
    └─ 所有 uses-permission 必须有对应的运行时处理(本项目基本都走 Photo Picker,不需要)

PrivateVaultApp.kt
    ↑
    └─ 任何 .kt 通过 `context.applicationContext as PrivateVaultApp` 获取单例
```
