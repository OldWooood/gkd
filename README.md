# gkd

<p align="center">
<a href="https://gkd.li/"><img src="https://e.gkd.li/2a0a7787-f2dd-4529-a885-93f3b8c857c3" alt="GKD.LI" width="40%" /></a>
</p>

基于 [高级选择器](https://gkd.li/guide/selector) + [订阅规则](https://gkd.li/guide/subscription) + [快照审查](https://github.com/gkd-kit/inspect) 的自定义屏幕点击 Android 应用

通过自定义规则，在指定界面，满足指定条件(如屏幕上存在特定文字)时，点击特定的节点或位置或执行其他操作

- **快捷操作**

  帮助你简化一些重复的流程, 如某些软件自动确认电脑登录

- **跳过流程**

  某些软件可能在启动时存在一些烦人的流程, 这个软件可以帮助你点击跳过这个流程

## 免责声明

**本项目遵循 [GPL-3.0](/LICENSE) 开源，项目仅供学习交流，禁止用于商业或非法用途**

## Fork 修改说明

本 fork 是 [gkd-kit/gkd](https://github.com/gkd-kit/gkd) 的个人定制版，只关注一件事：**跳过开屏广告**

- **仅保留开屏广告规则**：订阅解析阶段即丢弃其他类别的规则（更新提示、全屏广告、青少年模式等均不参与匹配）；开屏全局规则保留匹配，用于覆盖无专属规则的应用；规则类别与全局规则管理界面已移除
- **仅支持 Android 16+**：minSdk 26 → 36（API 36），移除全部旧版本适配分支；Android 15 及以下设备无法安装，请使用官方原版
- **按开屏周期自适应事件投递**：仅冷启动/切换应用后的 5 秒开屏窗口内 0ms 全速投递，其余时间 500ms 低频档 + 无规则应用内容事件入口丢弃，降低待机功耗
- **仅保留无障碍工作模式**：移除自动化模式及工作模式切换页，自动化规则执行链（UiAutomation 宿主、占用检测、a11y 范围名单）与局部关闭（白名单页、开关、服务启停联动）代码已删除；Shizuku 特权服务保留，用于写入安全设置授权与无感保活
- **应用内更新指向本仓库**：通过 `releases/latest/download/update.json` 检查更新，与官方源完全隔离
- **本地签名配置**：支持仓库根目录未跟踪的 `key.properties`（兼容 `storeFile/storePassword/keyAlias/keyPassword` 键名）
- **已移除**：快照、界面/事件/崩溃日志、HTTP 调试服务、高级设置页、GitHub Actions；对应 4 张数据库表通过迁移删除
- **本轮继续精简**：轨迹提示服务、缓慢查询页、规则新增入口（保留编辑与开关）、应用内 WebView（改外部浏览器打开）、快捷设置磁贴、无障碍截图链路、无用 hidden-api stub；应用白名单编辑页保留

## 安装

从 [本仓库 Releases](https://github.com/OldWooood/gkd/releases/latest) 下载 APK（文件名形如 `gkd-v1.13.11.apk`）

> 系统要求：Android 16 及以上。应用内“设置 - 关于 - 检测更新”会自动检查本仓库的新版本。

通用问题可参考官方 [疑难解答](https://gkd.li/guide/faq)

## 订阅

GKD **默认不提供规则**，本 fork 推荐使用以下第三方订阅（导入后仅开屏广告一类规则生效，其余类别会被丢弃）：

```
https://raw.githubusercontent.com/Lin-arm/GKD_subscription/main/dist/gkd.json5
```

国内可改用 CloudFlare 源：

```
https://gkd-subscription-667.pages.dev/gkd.json5
```

## 衍生

开发过程中的衍生项目，它们正在被 gkd 使用，也许对你有帮助

- [kotlin-json5](https://github.com/lisonge/kotlin-json5)
- [kotlin-loc](https://github.com/lisonge/kotlin-loc)
- [android-api-diff](https://github.com/android-cs/android-api-diff)
- [remap](https://github.com/lisonge/remap)
- [priv-kit](https://github.com/priv-kit/priv-kit)

## 捐赠

如果 GKD 对你有用, 可以通过以下链接支持该项目

<https://github.com/lisonge/sponsor>
