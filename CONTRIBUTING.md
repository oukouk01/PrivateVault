# Contributing to PrivateVault

感谢您考虑为 PrivateVault 做贡献!🎉

本文档说明:
- 提 Issue / PR 的流程
- 编码规范
- 安全相关 PR 的特殊要求
- 提交流程

---

## 行为准则

请阅读 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。参与本项目即代表您同意遵守其中的条款。

---

## 我可以提什么?

| 类型 | 适合 Issue / PR |
| --- | --- |
| Bug 报告 | Issue (用 bug_report 模板) |
| 功能建议 | Issue (用 feature_request 模板) |
| 安全问题 | **不要**公开 Issue,用 [Security Advisories](../../security/advisories/new) |
| 文档修正 | 直接 PR |
| 翻译 | 直接 PR |
| 性能优化 | 直接 PR,先开个 Issue 说明动机 |
| 密码学相关 | **必须**先开 Issue 讨论,见下方"密码学 PR 流程" |

---

## 开发流程

### 1. Fork & 克隆

```bash
# 1. 在 GitHub 上 Fork
# 2. 克隆你的 fork
git clone https://github.com/YOUR_USERNAME/PrivateVault.git
cd PrivateVault
git remote add upstream https://github.com/ORIGINAL_OWNER/PrivateVault.git
```

### 2. 创建分支

```bash
git checkout -b feat/your-feature
# 或
git checkout -b fix/issue-123
```

### 3. 编码

- **遵循现有风格**:Kotlin 官方风格,4 空格缩进,UTF-8
- **小而专注的 PR**:一个 PR 只做一件事
- **加测试**:业务逻辑加单元测试(本项目暂未配齐测试框架,见下方"测试")
- **更新文档**:`README.md` / `docs/` / `CHANGELOG.md` 同步改

### 4. 验证

```bash
# 编译
./gradlew assembleDebug

# 跑 lint
./gradlew lintDebug

# 跑测试(本项目暂未配齐,先跳过)
# ./gradlew test
```

### 5. 提交

```bash
git add <specific_files>  # 不要 git add .
git commit -m "feat(import): 支持音频格式

- 加 VaultRepository.MediaType.AUDIO
- 加 MediaViewModel.audios StateFlow
- 在 MainScreen 加 AUDIOS Tab
- 缩略图解码器跳过音频(无帧)

Closes #123"
```

#### Commit 消息规范

使用 [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body>

<footer>
```

| type | 用途 |
| --- | --- |
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `docs` | 文档 |
| `style` | 格式(不改逻辑) |
| `refactor` | 重构(既不修 bug 也不加功能) |
| `test` | 加测试 |
| `chore` | 构建/CI/工具 |

scope: 模块名,如 `lock` / `import` / `crypto` / `settings` / `docs`。

### 6. Push & PR

```bash
git push origin feat/your-feature
```

在 GitHub 上点 "Compare & pull request"。

**PR 标题** 也用 Conventional Commits 格式。
**PR 描述**:
- 链接相关 Issue (`Closes #123` / `Fixes #456`)
- 说明改了什么
- 截图(UI 改动)
- 复测步骤

---

## 密码学 PR 流程

> 涉及 `security/`, `data/PasswordRepository.kt`, 加密算法 的 PR 必须:

1. **先开 Issue** 讨论设计,达成共识后再写代码
2. **更新 [docs/CRYPTO.md](docs/CRYPTO.md)**,包括:
   - 算法选型依据
   - 参数取值(NIST / OWASP 推荐值)
   - 兼容性影响
3. **附审计理由** 在 PR 描述中
4. **不要**:
   - 单独提交 AES/MD5/SHA1 等弱算法
   - 把 `password.toCharArray()` 改成 `String`
   - 把 `SecureRandom` 换成 `Random`
   - 把盐长度减小
5. **推荐**:
   - 把 CryptoManager 设计成可热替换
   - 加 migration path(旧文件可平滑升级)
   - 加单元测试覆盖关键逻辑

---

## 代码风格

### Kotlin 风格

遵循 [Kotlin 官方风格](https://kotlinlang.org/docs/coding-conventions.html)。
`gradle.properties` 中 `kotlin.code.style=official` 已经启用。

### Compose 风格

- 状态提升:Composable 默认 stateless
- 副作用:`LaunchedEffect` / `DisposableEffect` / `SideEffect`
- 重组安全:不要在 Composable 中创建 `Object` 引用

### 文件结构

```kotlin
// 1. license header (可选,本项目无)
// 2. package
// 3. imports (按字母序,分组)
// 4. @file:Suppress (如有)
// 5. public types
// 6. internal types
// 7. private types
```

---

## 测试

> ⚠️ 本项目**暂未配齐**测试框架。重构 PR 顺便补上。

### 单元测试建议

| 类 | 测试 |
| --- | --- |
| `PasswordHasher` | hash/verify 往返 · 错误密码返回 false · 同样输入产生不同 hash(盐随机) |
| `KeyDerivation` | 同样输入 + 同样盐 → 同样 key · 不同盐 → 不同 key |
| `AesCtr` | encrypt → decrypt 还原 · 篡改密文 1 字节 → 解密出错或明文不同 |
| `PasswordRepository` | set → verify 成功 · 5 次错误 → locked 30 秒 · disguiseEnabled 切换 |
| `VaultRepository` | newEncryptedFile 唯一 · list 排序按 modifiedAt 降序 · ensureNoMedia 创建 .nomedia |

### UI 测试建议(Compose)

| 屏幕 | 测试 |
| --- | --- |
| `LockScreen` | 输入 6 位 → onUnlocked 触发 · 错误密码 → 显示 error |
| `CalculatorScreen` | 输入 123+456= → onSecretUnlocked 触发 · 错误序列不触发 |
| `MainScreen` | FAB 点击 → 启动 Photo Picker · 长按 → 弹出 AlertDialog |

### 添加测试框架

```kotlin
// app/build.gradle.kts
android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

---

## 提交流程

```
Issue / Discussion
       ↓
PR Draft (WIP)
       ↓
编码 + 自测
       ↓
PR Ready for Review
       ↓
Review 反馈 → 修改
       ↓
合并 → 自动部署
```

合并者:目前由 @YOUR_GITHUB_USERNAME 维护。

---

## 发布流程

1. 更新 `CHANGELOG.md` 把 Unreleased 移到新版本号
2. 在 `app/build.gradle.kts` 更新 `versionCode` / `versionName`
3. Tag: `git tag v1.0.0`
4. 构建 release APK
5. 在 GitHub 上 [Draft a new release](../../releases/new):
   - 选 tag
   - 写 release notes
   - 上传 APK
6. 发布

---

## 联系方式

- 公开问题:GitHub Issues
- 私密安全:Security Advisories
- 一般讨论:GitHub Discussions (启用后)

---

## License

贡献即表示您同意您的代码以 [MIT License](LICENSE) 发布。
