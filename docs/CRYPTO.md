# Cryptography

> 本文档是给密码学研究者 / 安全审计员的实现细节说明。
> 重点:**为什么**选这个算法、参数取值依据、与最佳实践的对照表。

## 目录

- [算法选型总览](#算法选型总览)
- [密码哈希 (PasswordHasher)](#密码哈希-passwordhasher)
- [密钥派生 (KeyDerivation)](#密钥派生-keyderivation)
- [文件加密 (AesCtr)](#文件加密-aesctr)
- [格式规范](#格式规范)
- [随机数 (SecureRandom)](#随机数-securerandom)
- [常量时间比较](#常量时间比较)
- [未来迁移路径](#未来迁移路径)
- [参考标准](#参考标准)

---

## 算法选型总览

| 用途 | 当前实现 | 备选 | 选择理由 |
| --- | --- | --- | --- |
| 屏幕密码校验 | SHA-256(salt ‖ pwd) 10 000 轮 | PBKDF2 / Argon2 / scrypt | 6 位数字熵低,迭代仅延缓在线攻击;PBKDF2 留给文件密钥 |
| 文件密钥派生 | PBKDF2-HMAC-SHA256, 120 000 轮 | Argon2id / scrypt | Argon2id 在 Android 上需引入 [com.lambdapioneer.argon2kt](https://github.com/lambdapioneer/argon2kt);PBKDF2 是 Android 自带、无第三方依赖 |
| 文件加密 | AES-256-CTR | AES-256-GCM / ChaCha20 | GCM 提供 AEAD 但本项目无篡改威胁;CTR 简洁且性能好 |
| 随机数 | `java.security.SecureRandom` | `KeyStore` 派生 | Android 上 `SecureRandom` 默认是 `AndroidOpenSSL` |
| IV / Salt | 12 / 16 字节 | RFC 5116 / NIST SP 800-132 | 标准值 |

---

## 密码哈希 (PasswordHasher)

### 代码

```kotlin
// security/PasswordHasher.kt
fun hashPassword(password: String): String {
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(salt)
    digest.update(password.toByteArray(Charsets.UTF_8))
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

### 存储格式

```
base64(salt_16B) ":" base64(hash_32B)
```

示例: `c2FsdHNhbHRzYWx0c2FsdA==:aGFzaGhhc2hoYXNoaGFzaGhhc2hoYXNoaGFzaGhhc2g=`

### 关键参数

| 参数 | 值 | 出处 |
| --- | --- | --- |
| 哈希函数 | SHA-256 | NIST FIPS 180-4 |
| 盐长度 | 16 字节 (128 bit) | NIST SP 800-132 ≥ 128 bit |
| 迭代次数 | 10 000 | 自定义;中端手机 ~50ms |
| 字符编码 | UTF-8 | RFC 8259 |

### 设计权衡

- **为什么不用 PBKDF2?** 6 位数字密码熵仅 20 bit,PBKDF2 与 SHA-256 迭代的离线攻击成本
  差异 < 1 个数量级(对攻击者而言都是分钟级)。SHA-256 自迭代 10 000 轮实现简单、无依赖。
- **为什么迭代 10 000?** 平衡点:< 100ms(UI 不卡) + 离线攻击 ~10x 减速(聊胜于无)
- **每次 `hash = SHA256(hash ‖ salt)`** 比 `SHA256(salt ‖ pwd)` 后固定 hash 多次更安全
  (避免 length-extension 的边缘场景)

### 弱点

- 6 位数字 = 1 000 000 种可能,离线 50 ms/次 → **3 小时**穷举
- 不抗离线攻击;**仅抗在线攻击**(5 次失败锁定 30 秒)
- 解决:本项目不存储密码,用户如担心应使用更强密码(尚未支持)

---

## 密钥派生 (KeyDerivation)

### 代码

```kotlin
// security/KeyDerivation.kt
private const val ITERATIONS = 120_000
private const val KEY_LENGTH_BITS = 256
private const val SALT_BYTES = 16

fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
    val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val keyBytes = factory.generateSecret(spec).encoded
    spec.clearPassword()  // 清零密码缓冲
    return SecretKeySpec(keyBytes, "AES")
}
```

### 关键参数

| 参数 | 值 | OWASP 2023 推荐 | NIST SP 800-132 |
| --- | --- | --- | --- |
| 算法 | PBKDF2-HMAC-SHA256 | PBKDF2-HMAC-SHA256 ≥ 600 000 (默认) | ≥ 1 000 |
| 迭代次数 | 120 000 | 600 000 | ≥ 1 000 |
| 盐长度 | 16 字节 | ≥ 16 字节 | ≥ 128 bit |
| 输出长度 | 256 bit (32 字节) | ≥ 输出所需 | ≥ 密钥长度 |

### 为什么不取 600 000?

- 在中端 Android 设备 (Snapdragon 7xx) 上,600 000 轮 PBKDF2 耗时约 1.5 秒
- 120 000 轮 ≈ 300 ms
- 仍远超 [NIST SP 800-132](https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-132.pdf) 的最低要求
- 用户每次解锁都要等待 → 折中

> 未来可升级到 Argon2id (引入 [argon2kt](https://github.com/lambdapioneer/argon2kt)),
> 在相同用户等待时间内提供更强保护。

### 关键安全点

1. **`CharArray` 而非 `String`**:
   - `String` 在 Java 字符串池中是 interned,可能留存在 PermGen/Metaspace
   - `CharArray` 可在派生后调用 `Arrays.fill(password, '\u0000')` 清零
   - 本项目通过 `spec.clearPassword()` 间接清空 PBKDF2 内部副本

2. **盐不可变且持久化**:
   - 盐存于 SharedPreferences,卸载时清除
   - 同一密码在不同设备上派生出的密钥不同
   - 即使知道派生算法,没有盐也不能离线攻击

---

## 文件加密 (AesCtr)

### 代码

```kotlin
// security/AesCtr.kt
private const val IV_BYTES = 12
private val rng = SecureRandom()

fun encryptStream(key: SecretKey, plainIn: InputStream, out: OutputStream) {
    val iv = ByteArray(IV_BYTES).also { rng.nextBytes(it) }
    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
    out.write(iv)
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = plainIn.read(buf)
        if (n <= 0) break
        out.write(cipher.update(buf, 0, n))
    }
    out.write(cipher.doFinal())
}
```

### 关键参数

| 参数 | 值 | 标准 |
| --- | --- | --- |
| 算法 | AES-256-CTR | NIST SP 800-38A |
| 密钥长度 | 256 bit (32 字节) | NIST FIPS 197 |
| IV 长度 | 12 字节 (96 bit) | RFC 3686 / RFC 5116 |
| 块大小 | 16 字节 | AES 固定 |
| 缓冲 | 64 KB | 经验值(平衡内存 / 系统调用) |

### 安全性

- ✅ **AES-256** 是 NIST 批准的抗量子候选(虽然 Grover 算法减半有效位,
  但 256 bit 仍提供 128 bit 量子后安全级别)
- ✅ **CTR 模式** 是流密码模式,可并行加解密
- ✅ **12 字节 IV** 来自 `SecureRandom`,2^96 空间,几乎不可能碰撞
- ⚠️ **CTR 模式无完整性** — 攻击者可翻转密文比特,对应明文比特也会翻转
  - 本项目没有"受信的文件输入路径",所以不影响
  - 如果将来加入"从云端下载文件并解密"等功能,需切换到 GCM

### IV 重用风险

> 同一密钥下,**绝不能**重用 IV。Java 的 `Cipher` 内部维护计数器,
> 但 IV 本身是用户提供的;我们的代码为每个文件生成新 IV,故安全。

---

## 格式规范

### 加密文件格式 (`.enc`)

```
┌────────────────────────────┬──────────────────────────┐
│  IV (12 字节)              │  AES-256-CTR ciphertext  │
│  SecureRandom 生成         │  与原文件等长             │
└────────────────────────────┴──────────────────────────┘
```

- 文件大小 = 12 + 原文件大小
- 没有 magic number 头(为了减小文件);但缺点是无法识别文件类型
  - 解决:文件名后缀固定为 `.enc`,目录结构(`images/` vs `videos/`)表明类型

### 加密备份格式 (`PrivateVault_Backup_*.zip`)

```
┌──────────────┬────────────┬──────────┬────────────────────────┐
│ Magic "PVLT" │ Salt 16B   │ IV 12B   │ AES-256-CTR 加密的 zip │
│ 4 字节       │            │          │                        │
└──────────────┴────────────┴──────────┴────────────────────────┘
```

- `PVLT` = `0x50 0x56 0x4C 0x54` 便于识别
- 解密后是标准 zip,内部:
  ```
  images/1234567890_xxxx.enc
  images/1234567891_yyyy.enc
  videos/1234567892_zzzz.enc
  ...
  ```

### 备份恢复 (待实现)

```python
# 伪代码
def restore(backup_path, password):
    with open(backup_path, 'rb') as f:
        assert f.read(4) == b'PVLT'
        salt = f.read(16)
        iv = f.read(12)
        key = pbkdf2_hmac('sha256', password.encode(), salt, 120_000, 32)
        cipher = AES.new(key, AES.MODE_CTR, nonce=iv, initial_value=...)
        decrypted_zip = cipher.decrypt(f.read())
        # 解压 decrypted_zip 到目标目录
        # 每个 .enc 文件用同一个 password 通过 PasswordRepository 校验,
        # 然后用同一把 key 解密
```

> 桌面还原工具的实现在另一个仓库(暂未开源)。

---

## 随机数 (SecureRandom)

### 用法

```kotlin
SecureRandom().nextBytes(salt)  // 16 字节
SecureRandom().nextBytes(iv)    // 12 字节
```

### Android 默认实现

- `AndroidOpenSSL` (API 21+)
- 底层:`/dev/urandom` + 熵混合 (LiveAlgos, /proc/sys/kernel/random/entropy_avail)
- 出厂时熵源 + 启动时用户交互 + 传感器数据

### 注意点

- `new SecureRandom()` 每次都查询 `nextBytes`,不耗尽熵池
- 不需要 `SecureRandom.getInstanceStrong()`(会阻塞等熵)
- 不要在主线程 `nextBytes(1024 * 1024)`(可能阻塞数十毫秒)

---

## 常量时间比较

### 代码

```kotlin
private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var r = 0
    for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
    return r == 0
}
```

### 为什么

- 防止**计时攻击**:标准 `==` 在第一个不匹配字节就返回 `true`,
  攻击者可测量响应时间推断密码前缀
- `r = r or xor` 强制遍历整个数组,运行时间与差异位置无关
- Java 17 的 `MessageDigest.isEqual(byte[], byte[])` 已实现常量时间,可改用

### 改进

```kotlin
return MessageDigest.isEqual(a, b)  // Java 17+ 推荐
```

---

## 未来迁移路径

### AES-256-CTR → AES-256-GCM

**动机**:提供 AEAD(认证加密),任何密文篡改都会导致解密失败。

**改动**:

```kotlin
// 旧:out.write(iv); cipher.update/doFinal
// 新:
val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
out.write(nonce)
val cipher = Cipher.getInstance("AES/GCM/NoPadding")
cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
// 加密后 cipher.doFinal() 返回 ciphertext || 16 字节 tag
out.write(cipher.doFinal(plain))
```

**文件格式**:`[12B nonce][ciphertext][16B tag]`

**性能影响**:GCM 比 CTR 慢 ~5-10%,但在 ExoPlayer 解密时仍可实时

**注意点**:GCM 的 nonce 也必须不重用;每次加密生成新 nonce 即可

### PBKDF2 → Argon2id

**动机**:Argon2id 抗 GPU/ASIC 攻击更强,迭代参数 = 内存 + 时间

**改动**:
- 引入 [com.lambdapioneer.argon2kt:argon2kt](https://github.com/lambdapioneer/argon2kt)
- 调整 `KeyDerivation.kt` 内部实现
- **派生结果完全等价**(256 bit SecretKey),业务代码无需改

**参数建议**:
- `memoryCost` = 64 MB (中端设备)
- `timeCost` = 3
- `parallelism` = 1

### SHA-256 哈希 → Argon2id

**动机**:Argon2id 比迭代 SHA-256 抗离线字典攻击

**改动**:在 `PasswordHasher` 内改用 Argon2id

**风险**:对 6 位数字密码而言,Argon2id 的额外保护意义不大;
**关键**是把屏幕密码升级为支持任意长度字符串(目前限定 6 位数字)

---

## 参考标准

| 标准 | 链接 |
| --- | --- |
| NIST FIPS 197 (AES) | https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.197.pdf |
| NIST SP 800-38A (Modes of Operation) | https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38a.pdf |
| NIST SP 800-132 (PBKDF) | https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-132.pdf |
| NIST SP 800-38D (GCM) | https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38d.pdf |
| RFC 2898 (PBKDF2) | https://datatracker.ietf.org/doc/html/rfc2898 |
| RFC 3686 (AES-CTR) | https://datatracker.ietf.org/doc/html/rfc3686 |
| RFC 5116 (AES-GCM) | https://datatracker.ietf.org/doc/html/rfc5116 |
| OWASP Password Storage Cheat Sheet | https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html |
| OWASP Mobile Top 10 | https://owasp.org/www-project-mobile-top-10/ |
