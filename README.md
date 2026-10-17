# gkd

[gkd-kit/gkd](https://github.com/gkd-kit/gkd) 的个人定制版，只做一件事：**跳过开屏广告**。

基于高级选择器 + 订阅规则，在应用开屏出现"跳过"按钮时自动点击。默认不提供规则，需自行导入第三方订阅。

## 与官方版的区别

**保留**

- 开屏广告规则匹配与点击跳过（含开屏全局规则，用于覆盖无专属规则的应用）
- Shizuku 特权通道（直连官方 Shizuku API，无第三方特权依赖）：写入安全设置自助授权、应用列表读取、栈顶应用识别、触摸事件注入
- 触发记录、订阅管理、应用内更新（指向本仓库 Releases，与官方源隔离）

**移除**

- 快照、界面/事件/崩溃日志、HTTP 调试服务、高级设置页
- 全局规则与规则类别管理页（订阅解析阶段即丢弃非开屏规则）
- 本地订阅（旧版本遗留数据会在启动时自动清理）
- 工作模式切换（仅无障碍模式）、自动化模式、局部关闭/白名单
- 底部「应用」页与应用规则汇总页（首页为单页：服务状态、触发记录、订阅、设置）
- 返回/滑动/长按动作（仅保留点击类 `clickNode/clickCenter/click`）
- 常驻通知服务、备份恢复、外观设置、多用户/分身、导出日志
- GitHub Actions（手动发版）

**其他调整**

- 仅支持 Android 16+（minSdk 36），移除了旧版本适配分支；Android 15 及以下设备请用官方原版
- 开屏窗口：冷启动/切换应用后 8 秒内全速匹配；Shizuku 在线且静止时只保留界面切换事件，降低待机耗电
- selector 仅保留 Kotlin/JVM 目标，hidden-api 仅保留必需 stub

## 系统要求

- Android 16 及以上
- 可选：Shizuku（运行并授权后启用特权能力；未启用时仅无障碍模式工作）

注意：特权能力依赖 Shizuku 进程。Shizuku 停止后特权立即失效（回落纯无障碍模式），Shizuku 恢复后自动重连；已授予的系统权限不受影响。

## 安装

从 [本仓库 Releases](https://github.com/OldWooood/gkd/releases/latest) 下载 APK（文件名形如 `gkd-v1.13.xx.apk`）。

应用内"设置 - 关于 - 检测更新"会自动检查本仓库的新版本。

通用问题可参考官方 [疑难解答](https://gkd.li/guide/faq)。

## 订阅

GKD **默认不提供规则**，本 fork 推荐使用以下第三方订阅（导入后仅开屏广告一类规则生效，其余类别会被丢弃）：

```
https://raw.githubusercontent.com/Lin-arm/GKD_subscription/main/dist/gkd.json5
```

国内可改用 CloudFlare 源：

```
https://gkd-subscription-667.pages.dev/gkd.json5
```

## 从源码构建

```sh
./gradlew :app:assembleGkdRelease
```

- 只打 `gkd` 渠道包；本地签名读取仓库根目录未跟踪的 `key.properties`（`storeFile/storePassword/keyAlias/keyPassword`，缺失时回落 debug 签名）
- 产物：`app/build/outputs/apk/gkd/release/app-gkd-release.apk`（`GKD_RENAME_APK_FLAG=1` 时改名为 `gkd-v<version>.apk`）

## 免责声明

**本项目遵循 [GPL-3.0](/LICENSE) 开源，项目仅供学习交流，禁止用于商业或非法用途**

## 捐赠

如果 GKD 对你有用，可以通过以下链接支持上游原作者

<https://github.com/lisonge/sponsor>
