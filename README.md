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

- **仅保留开屏广告规则**：订阅解析阶段即丢弃其他类别的规则（更新提示、全屏广告、青少年模式等均不参与匹配），订阅浏览界面同步只显示开屏广告分组
- **仅支持 Android 16+**：minSdk 26 → 36（API 36），移除全部旧版本适配分支；Android 15 及以下设备无法安装，请使用官方原版
- **按开屏周期自适应事件投递**：仅冷启动/切换应用后的 5 秒开屏窗口内 0ms 全速投递，其余时间（含应用内界面切换）250ms 低频档 + 无规则应用内容事件入口丢弃，降低待机功耗
- **应用内更新指向本仓库**：通过 `releases/latest/download/update.json` 检查更新，与官方源完全隔离
- **本地签名配置**：支持仓库根目录未跟踪的 `key.properties`（兼容 `storeFile/storePassword/keyAlias/keyPassword` 键名）

## 安装

从 [本仓库 Releases](https://github.com/OldWooood/gkd/releases/latest) 下载 APK（文件名 `app-gkd-release.apk`）

> 系统要求：Android 16 及以上。应用内“设置 - 关于 - 检测更新”会自动检查本仓库的新版本。

通用问题可参考官方 [疑难解答](https://gkd.li/guide/faq)

## 截图

|                                                               |                                                               |                                                               |                                                               |
| ------------------------------------------------------------- | ------------------------------------------------------------- | ------------------------------------------------------------- | ------------------------------------------------------------- |
| ![img](https://e.gkd.li/1e8934c1-2303-4182-9ef2-ad4c46882570) | ![img](https://e.gkd.li/01f230d7-9b89-4314-b573-38bd233d22f9) | ![img](https://e.gkd.li/dfa0a782-b21e-473a-96e4-eef27773b71b) | ![img](https://e.gkd.li/641decd1-2e60-4e95-b78c-df38d1d98a4d) |
| ![img](https://e.gkd.li/b216b703-d3de-4798-81ba-29e0ae63264f) | ![img](https://e.gkd.li/76c25ac9-4189-47cd-b40b-b9e72c79b584) | ![img](https://e.gkd.li/7288502e-808b-4d9a-88b5-1085abaa0d46) | ![img](https://e.gkd.li/aa974940-7773-409a-ae84-3c02fee9c770) |

## 订阅

GKD **默认不提供规则**，需自行添加本地规则，或者通过订阅链接的方式获取远程规则

也可通过 [subscription-template](https://github.com/gkd-kit/subscription-template) 快速构建自己的远程订阅

第三方订阅列表可在 <https://github.com/topics/gkd-subscription> 查看

要加入此列表, 需点击仓库主页右上角设置图标后在 Topics 中添加 `gkd-subscription`

<details>
<summary>示例图片 - 添加至 Topics (点击展开)</summary>

![image](https://e.gkd.li/9e340459-254f-4ca0-8a44-cc823069e5a7)

</details>

## 选择器

一个类似 CSS 选择器的选择器, 能联系节点上下文信息, 更容易也更精确找到目标节点

<https://gkd.li/guide/selector>

[@[vid=\"menu\"] < [vid=\"menu_container\"] - [vid=\"dot_text_layout\"] > [text^=\"广告\"]](https://i.gkd.li/i/14881985?gkd=QFt2aWQ9Im1lbnUiXSA8IFt2aWQ9Im1lbnVfY29udGFpbmVyIl0gLSBbdmlkPSJkb3RfdGV4dF9sYXlvdXQiXSA-IFt0ZXh0Xj0i5bm_5ZGKIl0)

<details>
<summary>示例图片 - 选择器路径视图 (点击展开)</summary>

[![image](https://e.gkd.li/a2ae667b-b8c5-4556-a816-37743347b972)](https://i.gkd.li/i/14881985?gkd=QFt2aWQ9Im1lbnUiXSA8IFt2aWQ9Im1lbnVfY29udGFpbmVyIl0gLSBbdmlkPSJkb3RfdGV4dF9sYXlvdXQiXSA-IFt0ZXh0Xj0i5bm_5ZGKIl0)

</details>

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

或前往 [Google Play](https://play.google.com/store/apps/details?id=li.songe.gkd) 给个好评
