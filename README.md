# v2rayNG Plus

An enhanced V2Ray / Xray client for Android with side-by-side installation support (`com.v2ray.angplus`), two-phase smart speed testing, dual download/upload benchmarking, and official release signing.

[![API](https://img.shields.io/badge/API-24%2B-yellow.svg?style=flat)](https://developer.android.com/about/versions/lollipop)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.4.0-blue.svg)](https://kotlinlang.org)
[![GitHub Releases](https://img.shields.io/github/downloads/armanpier/v2rayNG-Plus/latest/total?logo=github)](https://github.com/armanpier/v2rayNG-Plus/releases)

---

## Features / 特性

### Coexistence & Release Signing / 共存安装与正式签名
- **Side-by-Side Coexistence / 独立共存安装**:
  - Uses independent package name `com.v2ray.angplus`. You can install v2rayNG Plus alongside the official v2rayNG without uninstalling or overriding the original app.
  - 采用独立的包名 `com.v2ray.angplus`，无需卸载官方 v2rayNG 即可双开并存安装与独立运行。
- **Release Signed & Play Protect Clean / 正式密钥签名 & 规避报毒**:
  - Release APKs are signed with APK Signature Schemes v2/v3, eliminating Google Play Protect warnings and debug mode restrictions.
  - 采用正式发行证书与 v2/v3 签名方案，解除 Google Play 保护机制的安全未知警告与调试包限制。

### Smart Testing & Performance / 智能测速与性能
- **Two-Phase Smart Speed Test / 双阶段智能测速**:
  - Phase 1: Real latency (RTT) triage across all nodes.
  - Phase 2: Full throughput testing executed strictly on the top 15 fastest responsive nodes, saving bandwidth, battery, and time.
  - 第一阶段：对全部节点进行真实延迟测试（RTT）；
  - 第二阶段：自动筛选出响应最快的前 15 个节点进行完整吞吐量测速，大幅节省流量与电量。
- **Dual Download & Upload Speed Testing / 双向下载与上传测速**:
  - Measures download throughput via chunked streaming against Cloudflare CDN endpoints (`↓ [Download]`).
  - Measures upload throughput via an in-memory zero-allocation byte stream (`↑ [Upload]`).
  - 基于 Cloudflare CDN 测量实时下载带宽与内存零分配的上传吞吐量。
- **Configurable Test Payload Size / 可配置测速包大小**:
  - Choose between 5 MB, 10 MB (default), 25 MB, and 50 MB payload sizes via menu dialog or settings. Upload payload is scaled dynamically.
  - 支持在菜单与设置中选择 5 MB、10 MB（默认）、25 MB、50 MB 测速数据包，上传大小同步动态适配。
- **Instant Test Cancellation / 瞬时中断与取消**:
  - Interactive bottom banner with CANCEL button and top-menu action for instant cancellation of ongoing tests without blocking the app.
  - 测速期间底部常驻可交互取消提示条与顶部菜单项，毫秒级终止网络流，流畅不卡顿。
- **Sort by Download Speed / 按下载速度排序**:
  - Automatically sorts servers descending by measured throughput, keeping unmeasured or dead nodes at the bottom.
  - 一键按下载吞吐量降序排列配置，未测或失效节点自动沉底。
- **Enhanced Node Cards / 丰富节点信息展示**:
  - Visual status displays `[Delay] | ↓ [Download] | ↑ [Upload]` in real-time.
  - 节点卡片清晰展示延迟、下载测速及上传测速数据。
- **Stability & Deadlock Fixes / 稳定性与防死锁优化**:
  - Isolated ephemeral speed test inbounds to avoid port 10808 collisions and socket handle exhaustion.
  - Guaranteed `unlockStart()` on VPN service failures to eliminate service deadlocks.
  - 测速端口与配置隔离，彻底解决物理设备端口冲突及 VPN 服务启停死锁问题。

---

## Download / 下载

Download the latest release here:

在这里下载最新版本：

[https://github.com/armanpier/v2rayNG-Plus/releases](https://github.com/armanpier/v2rayNG-Plus/releases)

> [!TIP]
> v2rayNG is the mobile version. For the desktop version, please visit the v2rayN \
> v2rayNG 是手机版，电脑版请访问 v2rayN
>
> https://github.com/2dust/v2rayN

---

### Geoip and Geosite

- geoip.dat and geosite.dat files are in `Android/data/com.v2ray.ang/files/assets` (path may differ on some Android device)
- download feature will get enhanced version in this [repo](https://github.com/Loyalsoldier/v2ray-rules-dat) (note: it needs a working proxy)
- latest official [domain list](https://github.com/Loyalsoldier/v2ray-rules-dat) and [ip list](https://github.com/Loyalsoldier/geoip) can be imported manually
- possible to use a third-party dat file in the same folder, like [h2y](https://guide.v2fly.org/routing/sitedata.html#%E5%A4%96%E7%BD%AE%E7%9A%84%E5%9F%9F%E5%90%8D%E6%96%87%E4%BB%B6)

More in our [wiki](https://github.com/2dust/v2rayNG/wiki)

### Geoip 与 Geosite

- geoip.dat 和 geosite.dat 文件位于 `Android/data/com.v2ray.ang/files/assets`（部分设备路径可能不同）
- 下载功能将获取该 [仓库](https://github.com/Loyalsoldier/v2ray-rules-dat) 中的增强版本（注意：此功能需要一个可用的代理）
- 最新官方 [域名列表](https://github.com/Loyalsoldier/v2ray-rules-dat) 和 [IP 列表](https://github.com/Loyalsoldier/geoip) 可手动导入
- 也可在同一文件夹中使用第三方 dat 文件，例如 [h2y](https://guide.v2fly.org/routing/sitedata.html#%E5%A4%96%E7%BD%AE%E7%9A%84%E5%9F%9F%E5%90%8D%E6%96%87%E4%BB%B6)

更多内容请见我们的 [wiki](https://github.com/2dust/v2rayNG/wiki)

---

## Development guide / 开发指南

### Note

- Android project under the V2rayNG folder can be compiled directly in Android Studio, or using the Gradle wrapper. But the v2ray core inside the aar is (probably) outdated.
- The aar can be compiled from the Golang project [AndroidLibV2rayLite](https://github.com/2dust/AndroidLibV2rayLite) or [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite). For a quick start, read the guides for [Go Mobile](https://github.com/golang/go/wiki/Mobile) and [Makefiles for Go Developers](https://tutorialedge.net/golang/makefiles-for-go-developers/).
- v2rayNG can run on Android Emulators. For WSA, VPN permission needs to be granted via `appops set [package name] ACTIVATE_VPN allow`.

### 提示

- V2rayNG 文件夹下的 Android 项目可直接在 Android Studio 中编译，或使用 Gradle wrapper 编译。但 aar 内置的 v2ray core（可能）已过时。
- aar 可由 Golang 项目 [AndroidLibV2rayLite](https://github.com/2dust/AndroidLibV2rayLite) 或 [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) 编译而成。快速入门可参考 [Go Mobile](https://github.com/golang/go/wiki/Mobile) 指南和 [Makefiles for Go Developers](https://tutorialedge.net/golang/makefiles-for-go-developers/)。
- v2rayNG 可在 Android 模拟器上运行。对于 WSA，需要通过 `appops set [package name] ACTIVATE_VPN allow` 授予 VPN 权限。

---


## GPG Verification / GPG 签名校验

Release files are signed with GPG to verify authenticity and integrity, helping prevent mirror, ISP, or CDN hijacking.

发布文件已使用 GPG 签名，可用于校验文件真实性与完整性，预防镜像站、运营商或 CDN 劫持。

### Fingerprint / 公钥指纹

```text
7694 5E9F 3E9A 168F 8070 F195 805D 661C
134D FAF6 8903 C199 463C 31E5 AE90 3AE0
```

---

## Community / 社区

Telegram Group / Telegram 群组：

[https://t.me/v2rayN](https://t.me/v2rayN)

Telegram Channel / Telegram 频道：

[https://t.me/github_2dust](https://t.me/github_2dust)
