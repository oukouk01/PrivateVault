# Changelog

All notable changes to PrivateVault are documented in this file.

The format is loosely based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.0] - 2026-06-07
### Added
- 6 位数字密码锁 + SHA-256+salt 单向哈希
- PBKDF2-HMAC-SHA256 派生 AES-256-CTR 文件加密密钥
- 5 次失败锁定 30 秒 + 自动重置
- BiometricPrompt 指纹解锁 (BIOMETRIC_WEAK)
- Photo Picker 多选导入图片/视频
- AES-256-CTR 文件级加密,12 字节随机 IV
- MediaStore.createDeleteRequest 跨版本删除原图
- 网格缩略图浏览(图片 BitmapFactory + 视频 MediaMetadataRetriever)
- 图片全屏 HorizontalPager + 双指缩放
- ExoPlayer + 自定义 BaseDataSource 视频解密流播放(无明文落盘)
- 长按媒体文件解密还原到系统相册
- 计算器伪装 (启动器 alias + 123+456= 触发)
- 设置里可关闭伪装(同步切换 alias 启用状态)
- 备份提醒卡 + 加密 zip 导出 (Download/PrivateVault/)
- 子目录 .nomedia 保护
- GlobalExceptionHandler 崩溃兜底
- onStop 自动上锁,onStart 重新检查伪装
- Material3 主题
