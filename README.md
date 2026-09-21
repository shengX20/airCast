# AirCast

简洁的 Android AirPlay 2 发送端应用，将手机音频无线投送到 HomePod / AirPlay 设备。

## 特性

- 一键发现并连接局域网内的 AirPlay 设备
- 全设备音频投射（无需选择单应用）
- 连接时自动静音手机本地播放，断开恢复
- 前台服务 + WakeLock 保证锁屏持续播放
- 复古旋钮式音量控制，支持线性马达触感反馈
- 深色 Material You 风格界面，简洁无多余功能

## 技术栈

- Kotlin + Jetpack Compose (Material 3)
- NDK / C++20 原生 RAOP/RAOP 协议栈
- mDNS (NsdManager) 设备发现
- MediaProjection 音频捕获

## 基于项目

本项目核心 AirPlay 2 协议栈基于以下开源项目二次开发：

- [akustikrausch/airplay2-sender-cpp](https://github.com/akustikrausch/airplay2-sender-cpp) — Apache License 2.0

在此基础上增加了 Android NDK JNI 桥接、Compose UI 层、设备发现、前台服务和音量控制。

## 开源协议

本项目沿用上游协议，采用 **Apache License 2.0** 开源。

## 免责声明

- 本项目仅供个人学习和技术研究使用，不得用于商业用途。
- AirPlay、HomePod、Apple 是 Apple Inc. 的商标，本项目与 Apple Inc. 无任何关联。
- 使用本软件所产生的任何后果（包括但不限于设备损坏、数据丢失、网络问题）由使用者自行承担。
- 请遵守您当地的法律法规，不得用于侵犯他人隐私或版权的行为。
- 本项目不保证在所有设备和系统版本上正常工作，使用前请自行测试。

## 构建要求

- Android Studio Ladybug 或更新版本
- NDK 27.0.12077973+
- CMake 3.22.1+
- arm64-v8a 设备
