# Build

> 本文档面向想从源码构建 PrivateVault 的开发者。
> 普通用户请参考 [USER_GUIDE.md](USER_GUIDE.md)。

## 目录

- [环境要求](#环境要求)
- [Android Studio 方式](#android-studio-方式)
- [命令行方式](#命令行方式)
- [常见问题](#常见问题)
- [Release 构建](#release-构建)
- [CI 集成](#ci-集成)
- [故障排查](#故障排查)

---

## 环境要求

| 工具 | 最低 | 推荐 |
| --- | --- | --- |
| JDK | 17 | 17 (Temurin / Zulu) |
| Android Studio | Hedgehog 2023.1.1 | Koala 2024.1.1+ |
| Android Gradle Plugin | 8.2.2 | 8.2.2 |
| Gradle | 8.4 | 8.4 (由 wrapper 指定) |
| Kotlin | 1.9.22 | 1.9.22 |
| Android SDK | Platform 34, Build-Tools 34.0.0 | 最新 |
| min SDK | 26 (Android 8.0) | — |
| target SDK | 34 (Android 14) | — |
| compile SDK | 34 | — |
| NDK | 不需要 | — |
| CMake | 不需要 | — |

### 检查本地环境

```bash
java -version      # openjdk version "17.x" ...
git --version      # git version 2.x
echo $ANDROID_HOME # /Users/.../Android/sdk
```

> 缺哪项请先安装。`ANDROID_HOME` 必须指向有效的 Android SDK 目录。

---

## Android Studio 方式

### 第 1 步:克隆

```bash
git clone https://github.com/YOUR_GITHUB_USERNAME/PrivateVault.git
cd PrivateVault
```

### 第 2 步:打开

1. Android Studio → **File** → **Open**
2. 选择 `PrivateVault` 目录
3. 等待首次 **Gradle Sync**(自动下载依赖,需 3-10 分钟)
4. 期间会自动:
   - 下载 Gradle 8.4
   - 下载 `gradle-wrapper.jar`
   - 解析所有 Maven 依赖
   - 编译 KSP / KAPT(本项目无 KAPT)

### 第 3 步:连接设备

- 启用 **USB 调试**:`设置 → 开发者选项 → USB 调试`
- USB 连接后,Android Studio 顶部应显示设备名

### 第 4 步:运行

- 工具栏绿色 ▶ 按钮 → 选设备 → **OK**
- Logcat 会输出 `PrivateVaultApp` 标签的日志

### 第 5 步:调试

- 在任意 `.kt` 文件左侧栏点击 → 设置断点
- 再点 ▶ 按钮(此时是 **Debug** 而非 Run)
- 启动时调试器附加

---

## 命令行方式

### 第 1 步:克隆

```bash
git clone https://github.com/YOUR_GITHUB_USERNAME/PrivateVault.git
cd PrivateVault
```

### 第 2 步:下载 gradle-wrapper.jar

首次从源码构建,需要 `gradle/wrapper/gradle-wrapper.jar`(二进制文件,不会出现在 git 中)。
最简单的方式:用本机已装 Gradle 生成:

```bash
# 装 gradle 8.4+ (macOS)
brew install gradle

# Linux / WSL
sudo apt install gradle  # 或从 https://gradle.org/releases/ 下载

# 生成 wrapper
gradle wrapper --gradle-version 8.4
```

或者:用 Android Studio 打开项目一次,Sync 后会自动生成。

### 第 3 步:chmod +x

```bash
chmod +x gradlew
```

### 第 4 步:构建 Debug APK

```bash
./gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

### 第 5 步:安装

```bash
adb devices                                    # 确认设备连接
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.privatevault/.ui.MainActivity
```

### 常用命令

| 命令 | 作用 |
| --- | --- |
| `./gradlew assembleDebug` | 构建 Debug APK |
| `./gradlew assembleRelease` | 构建未签名 Release APK |
| `./gradlew installDebug` | 构建并安装到当前连接设备 |
| `./gradlew lintDebug` | 跑 Android Lint |
| `./gradlew test` | 跑单元测试 |
| `./gradlew clean` | 清理 build/ 目录 |
| `./gradlew tasks` | 列出所有可用任务 |
| `./gradlew :app:dependencies` | 打印依赖树 |
| `./gradlew --stacktrace` | 出错时打印完整堆栈 |

---

## 常见问题

### Q1: Sync 失败,提示 "SDK location not found"

**A**:
1. 创建 `local.properties` 在项目根目录:
   ```properties
   sdk.dir=/Users/yourname/Library/Android/sdk
   ```
   Windows:
   ```properties
   sdk.dir=C\:\\Users\\yourname\\AppData\\Local\\Android\\Sdk
   ```
2. 或设置环境变量:
   ```bash
   export ANDROID_HOME=/Users/yourname/Library/Android/sdk
   ```

### Q2: 编译报 "Compose Compiler / Kotlin 版本不匹配"

**A**:检查 `app/build.gradle.kts` 中:
- `kotlinCompilerExtensionVersion = "1.5.10"` 对应 Kotlin 1.9.22
- 如果升级 Kotlin,需同步升级 Compose Compiler 版本(查 [Compose-Kotlin Compatibility Map](https://developer.android.com/jetpack/androidx/releases/compose-kotlin))

### Q3: 找不到 androidx.fragment:fragment-ktx

**A**:`app/build.gradle.kts` 已声明:
```kotlin
implementation("androidx.fragment:fragment-ktx:1.6.2")
```
如果仍报缺,检查 `settings.gradle.kts` 是否有 `google()` 仓库。

### Q4: 启动后立刻崩溃: `ClassNotFoundException: androidx.fragment.app.FragmentActivity`

**A**:`MainActivity` 继承 `FragmentActivity`,需要 `androidx.fragment` 依赖。
确认 `app/build.gradle.kts` 的 dependencies 块包含 `fragment-ktx`。

### Q5: 提示 "License for package ... not accepted"

**A**:
1. Android Studio → Tools → SDK Manager
2. SDK Tools 标签 → 勾 "Android SDK Build-Tools 34" → Apply
3. 接受 license 即可

### Q6: Media3 / ExoPlayer 找不到

**A**:检查 `app/build.gradle.kts`:
```kotlin
val media3Version = "1.2.1"
implementation("androidx.media3:media3-exoplayer:$media3Version")
implementation("androidx.media3:media3-ui:$media3Version")
implementation("androidx.media3:media3-datasource:$media3Version")
implementation("androidx.media3:media3-common:$media3Version")
```

### Q7: Photo Picker 提示 "Not implemented"

**A**:在 API < 33 设备上,Photo Picker 会 fallback 到 `ACTION_OPEN_DOCUMENT`。
确认:
- `minSdk = 26` (项目已设)
- 已加 `androidx.activity:activity-compose:1.7.0+` (项目用 1.8.2)

### Q8: 安装后桌面只有"PrivateVault"图标,没有"计算器"

**A**:默认两者都应有(看 [AndroidManifest.xml](../app/src/main/AndroidManifest.xml))。
如果只能看到一个,可能 OEM 修改了 launcher。可在设置中切换"伪装"开关,
或手动添加 alias 启动器:
```bash
adb shell pm enable com.example.privatevault/.ui.CalculatorAliasActivity
```

---

## Release 构建

### 1. 生成签名密钥

```bash
keytool -genkey -v -keystore release.keystore \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias privatevault -storepass YOUR_STORE_PASS \
  -keypass YOUR_KEY_PASS \
  -dname "CN=PrivateVault,O=Example,C=US"
```

**把 `release.keystore` 妥善保管,绝不入库!**

### 2. 配置签名

`app/build.gradle.kts` 中:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = "YOUR_STORE_PASS"
            keyAlias = "privatevault"
            keyPassword = "YOUR_KEY_PASS"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true   // 启用 R8 / ProGuard
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

**生产配置建议用 `gradle.properties` 或环境变量**:
```kotlin
val storePass: String = project.findProperty("RELEASE_STORE_PASS") as String? ?: ""
```

### 3. 构建

```bash
./gradlew assembleRelease
# 产物: app/build/outputs/apk/release/app-release.apk
```

### 4. 验证

```bash
# 看签名
apksigner verify --verbose app-release.apk

# 看包大小
ls -lh app/build/outputs/apk/release/
```

### 5. ProGuard 规则

`app/proguard-rules.pro` 已有:
```pro
-keep class com.example.privatevault.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
```

启用 `isMinifyEnabled = true` 后会自动应用。**勿删除 `-keep class`**,
否则反射调用 `BiometricPrompt` 等会失败。

---

## CI 集成

项目自带 [GitHub Actions workflow](../.github/workflows/build.yml):

```yaml
name: Build Debug APK
on: [push, pull_request, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew assembleDebug --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/*.apk
```

### 触发方式

1. 推送到 `main` / `develop` 分支
2. 提交 PR
3. 手动触发:GitHub → Actions → Build → Run workflow

### 产物下载

- 任务完成后,在任务页底部 **Artifacts** 区域下载 `app-debug.zip`
- 解压得到 `app-debug.apk`

---

## 故障排查

### 1. 编译耗时过长

**原因**:首次编译需下载 ~500 MB 依赖。

**优化**:
```bash
# 在 gradle.properties 中
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g
org.gradle.parallel=true
org.gradle.caching=true
```

### 2. `OutOfMemoryError: Java heap space`

```bash
# 临时
./gradlew assembleDebug -Dorg.gradle.jvmargs=-Xmx4g

# 永久:编辑 gradle.properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m
```

### 3. `java.lang.UnsupportedClassVersionError`

**原因**:JDK 版本 < 17。
**修复**:安装 JDK 17+ 并设置 `JAVA_HOME`:
```bash
export JAVA_HOME=/usr/lib/jvm/temurin-17-jdk
```

### 4. R8 报 missing classes

通常第三方库没适配 R8。**临时方案**:
```kotlin
buildTypes {
    release {
        isMinifyEnabled = false  // 不推荐,会暴露代码
    }
}
```

**正确方案**:在 `proguard-rules.pro` 加 `-dontwarn`:
```pro
-dontwarn com.example.somelib.**
-keep class com.example.somelib.** { *; }
```

### 5. Logcat 看不到日志

```bash
# 按 tag 过滤
adb logcat -s PrivateVaultApp:V VaultCrash:V MediaViewModel:V

# 看崩溃
adb logcat -d *:E | grep -A 30 "AndroidRuntime"
```

### 6. 安装后立刻闪退

```bash
# 1. 看崩溃
adb logcat -d *:E | tail -100

# 2. 看进程是否存在
adb shell ps -A | grep privatevault

# 3. 看崩溃历史(API 30+)
adb shell dumpsys dropbox --print | grep -A 5 privatevault
```

常见原因:
- 资源找不到(检查 `res/`)
- `setContent` 之前的代码抛异常
- 权限被拒后未处理(本项目用 Photo Picker 不需要权限)

---

## 下一步

- 跑测试:`./gradlew test`
- 看架构:[ARCHITECTURE.md](ARCHITECTURE.md)
- 看密码学:[CRYPTO.md](CRYPTO.md)
- 改功能:[API.md](API.md)
- 找改哪里:[MODULE_MAP.md](MODULE_MAP.md)
