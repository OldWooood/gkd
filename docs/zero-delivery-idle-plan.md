# 特权空闲零投递方案

> 适用项目：gkd fork（只跳开屏广告）
> 状态：待实现
> 版本：v6
> 事实依据：android-16.0.0_r1 源码

---

## 0. 摘要

1. **现状**：GKD 的无障碍服务会收到全系统的界面事件，静止看视频/阅读时进程也在被持续唤醒。
2. **目标**：只在可能出开屏广告的时间窗里收事件并匹配，窗口外几乎不收。
3. **做法**：把"收哪些事件、多快收、要不要开节点缓存"打包成三个**档位**自动切换；窗口 = 最近一次 **app 切换或亮屏**起 5 秒。

| # | 议题 | 决策 |
|---|---|---|
| 1 | 窗口长度 | 5 秒（沿用现有 `SPLASH_ACTIVE_WINDOW`） |
| 2 | 特权空闲档 `notificationTimeout` | 0ms |
| 3 | 按应用例外机制 | 不做 |
| 4 | `enableMatch=false` | 顺带优化：特权侧固定进空闲档 |
| 5 | 应用内广告（二级页/Dialog/WebView 内嵌） | 不做，接受损失 |
| 6 | 埋点/分阶段灰度/功能开关 | 不做；一次性落地 + 手工自检，回滚直接 revert |
| 7 | FAST 档节点缓存 | 必须开 |

---

## 1. 名词表

| 名词 | 含义 |
|---|---|
| **content 事件** | `TYPE_WINDOW_CONTENT_CHANGED`：界面内容变化（文字、布局、进度条、倒计时） |
| **state 事件** | `TYPE_WINDOW_STATE_CHANGED`：窗口/界面变化（进入新界面、弹窗出现） |
| **eventTypes** | 服务声明的"我要收哪些类型"白名单，系统按它过滤 |
| **notificationTimeout** | 系统把事件交给服务前最多等多久（用于合并/延迟） |
| **两道闸门** | 系统投递前的两次判定：① 类型白名单 ② "缓存维护"强制投递 |
| **cache-critical 事件** | 为维护 GKD 的节点镜像而强制投递的 11 类事件（含 content） |
| **mUses** | 服务端的"该服务正在用节点缓存"标志；打开它会带来 cache-critical 投递 |
| **预取（prefetch）** | 一次节点查询顺带把子孙节点一起取回，省掉后续逐节点查询 |
| **档位（profile）** | 投递配置三件套：`eventTypes` + `notificationTimeout` + 缓存开关 |
| **开窗** | 把 `appChangeTime` 作为窗口起点，窗口内档位为 FAST |
| **窗口 / 尾巴** | 窗口 = app 切换或亮屏后 5 秒；尾巴 = 规则触发成功后的 3 秒 |
| **门控（gate）** | 是否允许执行"匹配查询"（读屏幕节点树） |
| **特权** | Shizuku 提供的系统能力，本方案只在特权在线时启用 |

---

## 2. 背景：系统是怎么把事件交给 GKD 的

### 2.1 两类事件

界面发生变化时，系统会产生 **state 事件**（换了界面/弹了窗）和 **content 事件**（界面内容变了）。GKD 靠 content 事件发现"跳过"按钮，靠 state 事件知道"换应用/换界面了"。

### 2.2 两道闸门（本方案的立身之本）

```
界面发生事情（新界面 / 文字变化 / 点击 / 滚动…）
        │
        ▼
源应用把事件交给系统（system_server）
        │
        ▼
系统对每个无障碍服务逐个判定：
        │
        ├─ 闸门①：服务的 eventTypes 里有这个类型吗？ ── 否 ─┐
        │                                                 ├─→ 直接丢，服务完全不知道
        ├─ 闸门②：服务在用缓存 且 类型属于 11 类关键事件？ ─┤
        │            否 ───────────────────────────────────┘
        │            是
        ▼
进入投递队列，等待 notificationTimeout 后交给 GKD
   · 非 content 类型：等待期间同类型只留最后一条（合并）
   · content 类型    ：每条都投，但每条都晚 timeout
```

三条推论，后面所有设计都围绕它们：

1. **只改 eventTypes 没用**：只要缓存开着，content 会被闸门②强制投递。
2. **只要做过节点查询，缓存标志就被打开**，只有 `setCacheEnabled(false)` 能关。
3. **投递判定在事件入队那一刻完成**——改配置只影响之后入队的事件。

### 2.3 延迟与合并

旧空闲档设 500/1000ms，靠"晚点投"省电，代价是 state 也晚到。本方案空闲档设 **0ms**：空闲期只剩 state 事件，合并省不下什么，晚到反而拖慢兜底路径。

### 2.4 缓存标志 mUses 的来龙去脉

| 谁能打开 | 方式 |
|---|---|
| GKD 自己 | 任何一次节点查询（`getChild`、按文字/ID 查找、取根节点） |

| 谁能关闭 | 方式 |
|---|---|
| GKD 自己 | 只有 `setCacheEnabled(false)` 一个入口 |

### 2.5 两层节点缓存与"预取"

| 层 | 记住什么 |
|---|---|
| GKD 自己的缓存 | 这一轮里**已经访问过**的节点 |
| 系统的镜像缓存（在 GKD 进程里） | **预取回来但还没访问过**的整批节点 |

预取的意义：缓存开着时，查询子节点会一次返回"该节点 + 一批子孙"，后续访问免去跨进程往返；缓存关掉时预取被禁，**每个子节点都是一次跨进程调用**（源应用还要逐个构建节点，很贵）。

### 2.6 改配置的代价

`setServiceInfo`（改 eventTypes/timeout）和 `setCacheEnabled` 每次调用都会让系统**向所有应用广播一次服务状态变化**；后者还会清空节点镜像。所以：能不调就不调、能合并就合并、重复值用哨兵挡住。

### 2.7 事实依据（android-16.0.0_r1）

| # | 事实 | 位置 |
|---|---|---|
| F1 | 两道投递闸门，都不满足才不投递 | `AbstractAccessibilityServiceConnection` 1802–1810 |
| F2 | 所有类型都按 timeout 延迟；非 content 在有 timeout 时合并 | 同上 1820–1836 |
| F3 | `CACHE_CRITICAL_EVENTS_MASK` 共 11 类（含 content/state） | `core/java/android/view/accessibility/AccessibilityCache.java:61` |
| F4 | 节点查询置 `mUses=true`（含 `getRootInActiveWindow`）；只有 `setCacheEnabled(false)` 能复位 | 同上 692/768/845、2641 |
| F5 | 缓存关闭 → 剥离预取标志；`getChild`/`getRootInActiveWindow` 默认带 `FLAG_PREFETCH_DESCENDANTS_HYBRID` | `AccessibilityInteractionClient` 629–634；`AccessibilityService` |
| F6 | `setServiceInfo`/`setCacheEnabled` 各触发一次全应用广播；`getServiceInfo()` 为整包 binder 往返 | 同上 550/2639；`AccessibilityManagerService` 5252→3078 |
| F7 | `setCacheEnabled` 为公开 API；eventTypes/notificationTimeout 动态可配 | `core/api/current.txt`；`AccessibilityServiceInfo.updateDynamicallyConfigurableProperties` |

---

## 3. 目标范围：只覆盖"开屏"

**覆盖**：冷启动打开应用、从其他应用/桌面切回应用、亮屏后进入应用——这三类场景的共同点是 **top app 发生变化**，正好是 5 秒窗口覆盖的时序。

**不覆盖**（明确接受，见 §9）：

- 应用内二级页广告；
- 应用内 Dialog/浮层广告；
- WebView/图形注入类内嵌广告。

理由是订阅在解析阶段就只保留"开屏广告"类别，这些场景本来就不在射程内；强行覆盖它们（让每次界面变化都开窗）会把应用内浏览时的噪声全放回来，反而把省电收益吃掉。

---

## 4. 三个档位

### 4.1 档位表

| 档位 | 什么时候用 | 收什么事件 | 多快收到 | 节点缓存 |
|---|---|---|---|---|
| **FAST** | 窗口/尾巴内，且当前应用有可用规则 | state + content | 立即（0ms） | 开 |
| **IDLE_PRIVILEGED** | 特权在线，窗口外（含匹配关闭/无规则） | **只有 state** | 立即（0ms） | 关 |
| **IDLE_FALLBACK** | 没连 Shizuku | state + content | 最多等 500ms | 开 |

设计理由：

- **FAST 收 content**：跳过按钮靠 content 发现，刚需。
- **IDLE_PRIVILEGED 留 state**：量极少（一次界面切换一条），但它负责 topActivity 兜底修正和订阅自动更新触发，不能全砍。
- **IDLE_PRIVILEGED 关缓存**：这是"零投递"的关键——不关，content 会被闸门②无视 eventTypes 一直投。
- **IDLE_FALLBACK 与今天完全一致**：没特权时开窗要靠事件，不能省。

### 4.2 判定规则

```kotlin
// 窗口或尾巴是否有效（appChangeTime 的语义就是"最近一次 app 切换或亮屏"）
fun windowOrTailActive(t) = t - appChangeTime < 5秒 || t - lastTriggerTime < 3秒

// 门控：允许匹配查询吗（非特权永远允许，保持现状）
fun gateOpen(t) = privilege == null || windowOrTailActive(t)

// 选哪个档位
fun computeProfile(t) = when {
    匹配已关闭            -> 特权在线 ? IDLE_PRIVILEGED : IDLE_FALLBACK   // 顺带优化
    当前应用没有可用规则   -> 特权在线 ? IDLE_PRIVILEGED : IDLE_FALLBACK
    windowOrTailActive(t) -> FAST
    else                  -> 特权在线 ? IDLE_PRIVILEGED : IDLE_FALLBACK
}
```

### 4.3 时间线示例

**场景 A：冷启动打开一个有开屏广告的应用（主场景）**

```
t=0       点图标，系统把该应用切到前台
t≈0.0s    Shizuku 的任务栈回调通知 GKD → appChangeTime 更新 → 开窗 → 档位切 FAST
t≈0.3s    开屏界面出现 → state 事件（FAST 已生效）
t≈0.5s    广告 UI 渲染 → content 事件（立即收到）
t≈0.5s    GKD 匹配到"跳过" → 点击
t=5s      窗口到期 → 档位切 IDLE_PRIVILEGED（只剩 state、关缓存）
```

**场景 B：静置看视频/阅读**

```
没有任何 app 切换/亮屏 → 窗口不开 → 一直 IDLE_PRIVILEGED
content/滚动/输入/焦点等事件在系统侧就被丢弃，GKD 进程不被唤醒
只有偶尔的 state 事件进来（用于 topActivity 与订阅更新）
```

**场景 C：应用内浏览（明确不开窗）**

```
应用已在前台，用户点进二级页/弹出 Dialog
→ 事件为 state/content，但没有 app 切换
→ 窗口不开，content 不投递
→ 若该页有广告：不匹配（属 §3 明确接受的范围）
```

---

## 5. 窗口与门控

### 5.1 窗口定义

窗口 = 从**最近一次 app 切换或亮屏**起 5 秒。这两件事都会更新 `appChangeTime`（现有逻辑），所以**不需要新增时间戳**：

| 开窗点 | 触发 | 说明 |
|---|---|---|
| app 切换 | `idChanged`（TaskStack 回调优先，事件路径兜底） | 开屏主场景 |
| 亮屏 | `ACTION_SCREEN_ON` | 解锁后直达应用 |

### 5.2 尾巴

规则触发成功后，窗口再延 3 秒用于"二次点击"（点击后 300ms 会重试匹配，慢关闭的广告可能需要再点一次）。尾巴期间档位保持 FAST，所以重试查询也快。

关窗时刻：

```
关窗时刻 = max(appChangeTime + 5秒, 上次触发成功时间 + 3秒)
```

### 5.3 门控规则

- 特权在线：只有窗口/尾巴内允许匹配查询。
- 非特权：永远允许（其投递本来就是全量的，门控没有意义）。
- 被门控挡下的查询必须"安静地失败"：不触发定时重试自旋，也不把事件堆进待处理队列。

**为什么必须门控**：这是一个会自我击败的循环——

```
空闲期做一次匹配查询
   → 系统把缓存标志 mUses 打开
   → content 等 9 类事件恢复投递（闸门②强制）
   → 又触发匹配查询 …… 循环
```

所以空闲期必须同时满足"不查询"和"缓存关"，缺一不可。

### 5.4 开窗到 FAST 生效之间的缝

系统是**在事件入队那一刻**判定的，所以存在一段极小的窗口：

```
t0     任务栈回调到达 GKD
t0+几ms 档位应用完成（两次配置调用）→ FAST 生效
```

在这几毫秒内发出的 content 事件会按旧档位丢弃。实际影响≈0：广告的首帧 content 要等界面首次绘制（通常几十毫秒后），远晚于档位切换。若想再压：把档位应用从后台调度挪到回调当场同步执行（可选优化，非必须）。

### 5.5 兜底取包名的一个例外

有个函数在"事件里的应用和当前记录不一致"时会去读根节点拿包名。它跑在正常查询之外，不受门控保护——特权且非窗口时必须禁掉这条 a11y 兜底（否则会偷偷打开缓存标志）。特权下 app 切换本就由 TaskStack 负责，不依赖它。非特权不动。

---

## 6. 缓存策略

| 档位 | 缓存 | 理由 |
|---|---|---|
| FAST | 开 | 预取把"一次遍历 = 几百次跨进程调用"压成几次批量调用，这是窗口内速度的关键 |
| IDLE_PRIVILEGED | 关 | 不关挡不住 content（闸门②） |
| IDLE_FALLBACK | 开 | 与今天一致 |

两个容易误解的点：

- **FAST 开缓存几乎不额外收事件**：窗口内第一次节点查询本来就会打开 mUses，那 9 类事件迟早要来；显式开只是恢复"客户端缓存/预取"。窗口开头镜像本来就是冷的，清了不亏。
- **不能永久关缓存**：预取被禁，窗口内每次读子节点都变跨进程调用，把最该快的 5 秒拖慢。

---

## 7. 实现机制

### 7.1 档位应用（伪代码 + 解释）

```kotlin
private val 锁 = Any()
private var 已发eventTypes: Int? = null
private var 已发timeout: Long? = null
private var 已发cache: Boolean? = true     // 平台默认开
private var 缓存脏了 = false                // 有查询动过 mUses
@Volatile private var 当前档位: Profile? = null

fun 应用档位() = synchronized(锁) {
    val p = 计算档位(当前时间)
    val svc = service as AccessibilityService ?: return

    if (p.eventTypes != 已发eventTypes || p.timeout != 已发timeout) {
        val info = svc.serviceInfo ?: return          // 读失败不记账，下次重试
        info.eventTypes = p.eventTypes
        info.notificationTimeout = p.timeout
        svc.setServiceInfo(info)                      // 一次调用改两个字段
        已发eventTypes = p.eventTypes; 已发timeout = p.timeout
    }
    if (p.cache) {
        if (已发cache != true) { svc.setCacheEnabled(true); 已发cache = true }
    } else if (缓存脏了 || 已发cache != false) {         // 强制重关，绕过去重
        if (svc.setCacheEnabled(false)) { 已发cache = false; 缓存脏了 = false }
    }
    当前档位 = p
}
```

关键点：

- **一把锁串行**：档位会被事件线程、后台调度线程、特权监听三方调用，必须串行，否则会出现"关缓存的调用晚于开缓存落地"的错序。
- **分开记账**：eventTypes/timeout 一个哨兵、缓存一个哨兵。重复设置缓存会清空镜像 + 多发广播，必须挡住。
- **缓存脏标记**：任何查询轮结束都标记"缓存脏了"，保证回空闲档时一定补发 `setCacheEnabled(false)`——否则查询打开的缓存永远关不上。
- **失败不记账**：读取服务信息失败直接返回，不能把"已应用"记上，否则永久卡在错误档位。
- **查询前保底**：匹配查询入口也调用一次（幂等），保证"第一个节点访问发生时缓存一定是开的"。

### 7.2 不变量（实现时逐条守住）

| # | 不变量 | 一句话解释 |
|---|---|---|
| I1 | state-only、缓存关、门控三者同生同死 | 缺任何一个，空闲都会漏投递 |
| I2 | 被放行的查询，档位必 cache-on | 防止"慢查询 + 缓存被重新打开" |
| I3 | 首个节点访问前档位已应用 | 保证窗口内第一轮匹配不退化 |
| I4 | 查询后必须有强制重关缓存的路径 | 查询会偷偷打开 mUses |
| I5 | 被门控挡下的查询不触发自旋、不堆队列 | 否则空转或内存堆积 |
| I6 | 特权掉线立即回 IDLE_FALLBACK | 否则掉线期收不到事件 |
| I7 | 档位应用串行、幂等、失败不记账 | 防止错序与卡死 |

---

## 8. 代码改动点

只动 `a11y/A11yRuleEngine.kt` 一个文件：

| 位置 | 改动 | 为什么 |
|---|---|---|
| 常量 | 新增 `POST_TRIGGER_WINDOW = 3s`、`PRIVILEGED_IDLE_TIMEOUT = 0`、三个档位定义 | 参数集中 |
| 新增 | 档位数据类、哨兵、锁、`applyDeliveryProfile`、`windowOrTailActive`、`gateOpen` | 方案主体 |
| `onTopActivityChanged` / `onScreenForcedActive` / `onA11yConnected` | 改为调用 `applyDeliveryProfile`（替换现有 `refreshEventDeliveryTimeout`） | 两个开窗点与连接点 |
| `consumeEvent` | 门控判断前移到 `queryEvents.addAll` 之前 | 防止被挡下的事件积压 |
| `startQueryJob` | 顶部加 `gateOpen` 早退；早退不调 `checkFutureStartJob` | 防 300ms 空转自旋 |
| `startQueryJob` finally | `缓存脏了 = true` + `applyDeliveryProfile` | 保证缓存能重关 |
| `queryAction` | 入口 `applyDeliveryProfile` | 首个节点访问前缓存已开 |
| `getTimeoutAppId` | 特权且非窗口时禁用 a11y 兜底 | 防止偷开缓存（§5.5） |
| 降档定时（原 restore job） | 关窗时刻 = `max(appChangeTime + 5s, lastTriggerTime + 3s)`；到期重算档位 | 尾巴参与关窗 |
| `onA11yConnected` | 挂两个监听：特权连接状态、匹配开关 → 立即重算档位（跑在 actionDispatcher） | 掉线/开关立即生效 |

可选优化（非必须）：连接建立时缓存一份服务信息，省掉每次转场的读取往返。

---

## 9. 接受清单（会失去什么）

| 损失 | 说明 |
|---|---|
| **特权空闲期的应用内广告** | 二级页、Dialog、WebView 内嵌一律不匹配——只跳开屏的范围内接受（§3） |
| 冷启动极慢、5 秒后才渲染出广告 | 窗口已关 → 跳过不了（慢设备/重应用的极端情况） |
| 开窗到档位生效之间的 content | 直接丢，实际≈0（§5.4） |
| 非特权用户 | 行为与今天完全一致：无收益也无损失 |

---

## 10. 上线与回滚

- 一次性落地，不设功能开关；出问题直接 revert 提交。
- 上线前手工自检（不需要埋点）：
  1. 冷启动开屏 —— 正常跳过；
  2. 通知/最近任务切回应用 —— 正常跳过；
  3. 亮屏直达应用 —— 正常跳过；
  4. 静置（滚动/视频/输入）—— 无异常、无卡顿；
  5. 应用内浏览 —— 不匹配属预期，无其它异常；
  6. Shizuku 被杀后重连 —— 掉线期行为回落正常。

---

## 11. 明确不做

| 不做 | 原因 |
|---|---|
| 让应用内结构变化开窗 | 超出"只跳开屏"范围，且会把浏览时的噪声放回来，吃掉省电收益 |
| 用 packageNames 白名单 | 破坏"无专属规则应用"的全局开屏规则，且挡不住闸门② |
| 熄屏时关闭无障碍服务 | 重连有 1~2 秒竞态，且熄屏本来就没有投递 |
| 永久关闭节点缓存 | 预取被禁，窗口内查询退化 |
| 靠 `setServiceInfo` 复位缓存标志 | 服务端标志只有 `setCacheEnabled(false)` 能复位 |
| 按应用开例外 | 已决定不做 |
| 埋点、分阶段灰度、功能开关 | 已决定不做（回滚直接 revert） |

---

## 12. 实现顺序（建议）

1. 常量与档位数据结构（`Profile`、三个哨兵、锁）。
2. `windowOrTailActive` / `gateOpen` / `computeProfile`。
3. `applyDeliveryProfile`（含缓存脏标记、失败不记账）。
4. 接线：三个入口改调 `applyDeliveryProfile`；降档定时改为关窗时刻。
5. 门控三处：`consumeEvent` 前置判断、`startQueryJob` 早退（不触发自旋）、`queryAction` 入口保底。
6. `getTimeoutAppId` 兜底禁用。
7. 查询结束打脏。
8. 监听器（特权连接状态 + 匹配开关）。
9. 按 §10 手工自检。
