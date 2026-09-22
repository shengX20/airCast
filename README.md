# AirCast

<div align="center">

<p align="center">
  <strong>简洁优雅的 Android AirPlay 2 音频发送端应用</strong><br>
  将手机全局音频实时无损无线串流至 HomePod / Apple TV / AirPort 等 AirPlay 设备
</p>

[![Release](https://img.shields.io/github/v/release/shengX20/airCast?style=flat-square&color=orange)](https://github.com/shengX20/airCast/releases)
[![Platform](https://img.shields.io/badge/Platform-Android%2010%2B-brightgreen?style=flat-square)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-1.7-4285F4?style=flat-square&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![C++](https://img.shields.io/badge/C%2B%2B-20-00599C?style=flat-square&logo=c%2B%2B)](https://isocpp.org)

</div>

---

## 📱 界面预览

<div align="center">
  <table>
    <tr>
      <td align="center" width="33%">
        <img src="https://imgchr.com/i/pnQb6Hg" width="100%" alt="正在扫描设备" /><br />
        <b>① 局域网扫描</b>
      </td>
      <td align="center" width="33%">
        <img src="https://imgchr.com/i/pnQb4g0" width="100%" alt="设备列表与正在投放" /><br />
        <b>② 设备列表与音频投射</b>
      </td>
      <td align="center" width="33%">
        <img src="https://imgchr.com/i/pnQb5vV" width="100%" alt="拟物复古音量旋钮" /><br />
        <b>③ 拟物复古音量旋钮</b>
      </td>
    </tr>
  </table>
</div>

---

## ✨ 核心特性

- **⚡ 一键发现与秒级连接**：基于 mDNS (DNS-SD) 自动发现局域网内的 AirPlay / RAOP 协议设备，自适应设备型号与认证握手方式。
- **🎵 全系统级音频捕获**：基于 Android 10+ 的 `AudioPlaybackCapture`，捕获手机全局媒体音频，无需特定 App 适配，网易云音乐、QQ音乐、Bilibili、YouTube、播客等即开即投。
- **🔇 智能声道控制与防回音**：连接成功后**自动将手机本体扬声器静音**，音频仅从投屏音响流出；断开连接时自动恢复手机原音量。
- **🎛 拟物化复古旋钮调音**：提供细腻的抽屉式旋钮（Vintage Knob）调节体验，配合线性马达触感震动反馈与声效，让调音沉浸感拉满。
- **🔋 稳定后台与锁屏播放**：前台服务（Foreground Service）+ 专用 WakeLock + 自动看门狗机制（Audio Watchdog），确保锁屏、息屏或切换后台时持续稳定推流。
- **🎨 现代深色拟物设计**：采用 Jetpack Compose 构建的 Material You + Neumorphic（微拟物）设计风格，界面纯粹克制、无广告、无多余冗余功能。

---

## 📖 使用指南

### 1. 准备工作
- 确保 Android 手机与你的 **HomePod / Apple TV / Mac / AirPlay 接收设备** 连接在**同一个局域网（Wi-Fi）**。
- 手机系统要求：**Android 10 (API 29) 及以上**。

### 2. 开始投射
1. 打开 **AirCast** 应用，应用将自动开始搜索局域网内的 AirPlay 设备（点击右上角刷新图标可手动重新扫描）。
2. 在“附近的设备”列表中，点击目标设备右侧的 **“连接”** 按钮。
3. 系统将弹出 **“屏幕转播/录制音频权限”** 请求，点击 **“立即开始” / “允许”** 授权（用于捕获系统音频流）。
4. 连接成功后，顶部卡片与列表将显示绿色 **“正在投放”** 状态，此时在手机上播放任意音乐或视频，声音即可无损无线传输至音响设备。

### 3. 音量调节
- **快捷调节**：在顶部播放卡片中点击 **`-`** / **`+`** 步进按钮增减音量。
- **精准调节**：点击卡片中的音量进度条，将唤出**复古拟物调音旋钮**（Vintage Knob），手指顺时针或逆时针旋转即可平滑精细调音。
- **系统通知栏控制**：在下拉通知栏的常驻卡片中，亦可随时进行音量增减与一键断开。

### 4. 断开连接
- 点击正在播放卡片右上角的电源按钮，或列表项右侧的 **“断开”** 按钮，即可结束投射，手机扬声器将自动解除静音并恢复原有音量。

---

## 🛠 技术架构与实现

```
AirCast 架构图
┌────────────────────────────────────────────────────────┐
│                   UI 层 (Jetpack Compose)              │
│   Material 3 / Neumorphic Components / Vintage Knob    │
└───────────────────────────┬────────────────────────────┘
                            │ JNI (NativeBridge.kt)
┌───────────────────────────▼────────────────────────────┐
│                    业务与服务编排层                     │
│  • DeviceDiscovery (mDNS / NsdManager)                 │
│  • CastService (Foreground Service / WakeLock)         │
│  • AudioPlaybackCapture (PCM 16-bit 44.1kHz Stereo)    │
└───────────────────────────┬────────────────────────────┘
                            │ JNI C++ 桥接 (jni_bridge.cpp)
┌───────────────────────────▼────────────────────────────┐
│                 原生协议栈 (C++20 NDK)                  │
│  • RAOP / RTSP 握手会话与状态机 (ANNOUNCE/SETUP/RECORD)  │
│  • NTP 时钟同步与低延迟 RTP 传输管道                    │
│  • HAP 配对验证 (SRP-6a, HKDF, ChaCha20-Poly1305)       │
│  • Ed25519 / X25519 密钥交换 & Mbed TLS 加密支持        │
└────────────────────────────────────────────────────────┘
```

- **编程语言**：Kotlin (UI & Android 框架层) + C++20 (原生协议核心)
- **UI 框架**：Jetpack Compose (Material 3)
- **协议移植**：C++ 原生移植并优化了 RAOP/AirPlay 2 协议通信栈，具备极高执行效率与极低延迟。
- **架构支持**：原生编译针对 `arm64-v8a` 进行优化。

---

## 🏗 本地编译与构建

### 环境要求
- **Android Studio**：Ladybug (2024.2.1) 或更高版本
- **JDK**：OpenJDK 17
- **Android SDK**：API 36 (Android 15+)
- **NDK**：`27.0.12077973`
- **CMake**：`3.22.1`

### 命令行编译
```bash
# 克隆仓库
git clone https://github.com/shengX20/airCast.git
cd airCast

# 编译 Debug APK
./gradlew assembleDebug

# 编译 Release APK
./gradlew assembleRelease
```
编译产物位于 `app/build/outputs/apk/`。

---

## ❓ 常见问题 (FAQ)

<details>
<summary><b>Q1: 为什么扫描不到我的 HomePod 或 Apple TV？</b></summary>
<br>
1. 请确认手机与 AirPlay 设备处于同一局域网网段，且路由器未开启 AP 隔离。<br>
2. 部分路由器/Wi-Fi 扩展器会拦截 mDNS (Multicast) 组播广播，可尝试重启路由器或在路由器后台开启 mDNS / 组播转发功能。
</details>

<details>
<summary><b>Q2: 为什么连接时提示输入 4 位 PIN 码？</b></summary>
<br>
部分 Apple TV 或开启了“需要密码/PIN”的 AirPlay 设备在首次连接时会进行安全性验证。此时在电视屏幕上查看显示的 4 位数字，并在 AirCast 弹出的对话框中输入确认即可。
</details>

<details>
<summary><b>Q3: 锁屏一段时间后音频中断怎么办？</b></summary>
<br>
应用已申请前台服务和 WakeLock，但部分厂商（如小米、华为、OPPO/vivo）的激进省电策略可能会在锁屏后清理后台。建议前往系统设置将 AirCast 的“省电策略”设为<b>“无限制 / 允许后台高耗电”</b>，并允许自启动。
</details>

---

## 📄 开源协议与第三方声明

本项目采用 [Apache License 2.0](LICENSE) 许可证开源。

### 核心协议引用与鸣谢
本项目核心 AirPlay 协议栈基于以下开源项目研究与二次开发移植：
- **[akustikrausch/airplay2-sender-cpp](https://github.com/akustikrausch/airplay2-sender-cpp)** — Apache License 2.0
- **[postlund/pyatv](https://github.com/postlund/pyatv)** — MIT License（参考并移植了 RAOP/AirPlay 协议时钟与 RTP 数据结构设计）
- **[Mbed TLS](https://github.com/Mbed-TLS/mbedtls)** — Apache License 2.0（提供 SRP-6a、HKDF、ChaCha20-Poly1305 加密套件）
- **[orlp/ed25519](https://github.com/orlp/ed25519)** — zlib License（提供 Ed25519 签名验证与 X25519 密钥交换）

详细的第三方开源许可证与版权说明请参阅 [THIRD-PARTY-NOTICES.txt](app/src/main/cpp/licenses/THIRD-PARTY-NOTICES.txt)。

---

## ⚠️ 免责声明

- 本项目仅供个人学习、技术研究与兼容性测试使用，严禁用于任何商业目的。
- **AirPlay**、**HomePod**、**Apple TV**、**Apple** 均为 Apple Inc. 的注册商标。本项目与 Apple Inc. 无任何隶属、赞助或合作关系。
- 使用本软件所产生的任何后果由使用者自行承担，请遵守所在地区的相关法律法规。
