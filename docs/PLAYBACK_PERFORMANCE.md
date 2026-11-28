# 播放与后台播放性能优化（PLAYBACK_PERFORMANCE）

> 面向对象：维护者、以及每次合并上游后要复查的 AI 助手。
> 解决的问题：播放/后台播放期间的掉帧、发热与耗电。
> 设计约束：**不碰上游热点文件的大结构**，改动集中在少数几个文件里，且每一处都能单独回滚。

---

## 0. 一句话结论

播放掉帧和发热的大头不是解码器，而是**屏幕和主线程在"没内容变化"的时候仍然被定时唤醒**。
本方案把三类"空转"改成事件驱动或按需降频：

| # | 空转点 | 原来 | 现在 |
|---|---|---|---|
| 1 | 刷新率控制 | 每 400ms 常驻唤醒主线程比对一次目标帧率 | 只在播放态/开关/触控结束时各下发一次 |
| 2 | 播放中触摸 boost | 触摸时无条件拉到屏幕满帧（120Hz） | 播放中不启用 boost，稳定在 60Hz |
| 3 | 后台轮询 / 前台服务 tick | 固定 1s，息屏也一样 | 息屏放宽一倍；暂停时通知 tick 放慢到 3s |
| 4 | 播放历史上报 | 每秒无脑建一个协程 | 只有真到期（≥15s 或播完）才建 |

---

## 1. 为什么是这四处（而不是换播放器 / 换解码器）

- **解码器不是瓶颈**：`TeakCodecSelector` 已按机型能力选硬解，软解只在用户显式选择或硬解失败时启用。换内核（IjkPlayer 等）会推翻 `infra/playback` 的全部既有适配，收益不确定而回归面极大。
- **掉帧的可控来源**是合成与唤醒：`TextureView` 播放需要每帧把视频纹理合成进窗口；弹幕用 `Choreographer` 自绘；两者叠加时，屏幕刷新率越高，单位时间的合成次数越多。而视频内容本身不超过 60fps，**把播放期间的窗口帧率钉在 60Hz 是纯收益**。
- **发热/耗电的可控来源**是空转唤醒：`SmartRefresh` 的 400ms 循环、后台 1s 轮询、前台服务 1s tick、每秒新建的上报协程。这些在息屏听音频、暂停挂机、长时间观看时都在持续消耗。

不改的东西（刻意保留）：

- 弹幕渲染引擎（`danmaku-engine`）的绘制与节流逻辑，已有 `pauseInvalidateWhenBlank` 在无弹幕时自动停帧。
- 缓冲策略（`StrategyLoadControl` / `core:domain/buffering`），这是**流畅度**与带宽的平衡，不是本次的能耗目标。
- 解码模式、音频焦点、音量、清晰度选择：行为语义不变。
- 视频表面类型（`TextureView` / `SurfaceView`）：`captureThirdSize()` 依赖 `TextureView.getBitmap()`，切换会破坏截图功能，风险大于收益。

---

## 2. 改动清单

### 2.1 `core/domain/.../RefreshRatePolicy.kt`（纯函数，有测试）

- 新增常量 `PLAYBACK_REFRESH_IDLE_GATE_MS = 1500L`：触控后维持高帧的静止门槛。
- 新增常量 `PLAYBACK_TOUCH_REFRESH_CAP_HZ = 60f`：播放中触控时的帧率上限。
- `targetPlaybackRefreshRate(enabled, idleMs, playing, fullscreen)`：
  - `!enabled` → `0f`（交回系统）；
  - 触控门槛内：播放中 → `60f`，其余 → `0f`（滑动跟手）；
  - 稳定态：播放或全屏 → `60f`，静止页面 → `1f`。

**为什么把常量放 domain**：调用方（`SmartRefresh`）需要按同一个门槛安排延时回调，常量放在一处避免两边各写一个 `1500L` 后失步。

### 2.2 `app/.../ui/SmartRefresh.kt`（事件驱动）

- 删掉 `LaunchedEffect { while (true) { apply(); delay(400) } }`。
- 改为：`update(enabled, playing, fullscreen)` 在播放态/开关/全屏变化时下发一次；`touch()` 在手指按下时下发一次并起算门槛。
- `armGate()` 只在"还在 1500ms 门槛内"时排一个延时任务，到点补发一次；门槛已过则不排任何任务。**稳定态下没有常驻协程。**
- `syncTouchBoost()`：只在"开关开启 + 正在播放"时 `setFrameRateBoostOnTouchEnabled(false)`；其余情况（非播放、或开关关闭）恢复 `true`，即不干预系统默认行为。

**语义等价性**：原来 400ms 轮询是为了在"触控后 1500ms"这个时刻把帧率从 0 降到 60/1。现在改成精确延时补发，判定条件（`idleMs >= 1500`）与输出值完全一致，只是不再每 400ms 空转。唯一差异是补发精度从"最迟 400ms 后"变成"门槛后一帧"，对用户不可感知。

**为什么要关播放中的 touch boost**：API 35 的 `setFrameRateBoostOnTouchEnabled(true)` 会在触摸时把帧率顶到屏幕上限，且**优先级高于 `setRequestedFrameRate`**。播放中拖动进度条时，这一下会把 60Hz 拉到 120Hz，多合成一倍画面但视频本身没有新帧，纯粹多耗电。

### 2.3 `feature/video/.../player/PlayerProgressEffects.kt`（上报前置判断）

- 每秒的 tick 里先读 `trackingController.state.value`，用 `shouldReportPlaybackHistory(...)`（`core/domain` 既有纯函数）判一次到期；只有到期才 `historyScope.launch { reportIfDue(...) }`。
- 状态容器内部的 `reporting` 互斥与到期判断**保持不变**，这里只是避免每秒建一个注定早退的协程。

### 2.4 `infra/playback/.../AndroidBackgroundPlayback.kt`（息屏降频）

- 轮询间隔从 `if (playing) 1_000 else 5_000` 改为 `playbackWakeIntervalMs(playing, screenInteractive, active = 1_000, idle = 5_000)`。
- `screenInteractive` 取自 `PowerManager.isInteractive`（取不到时按亮屏处理，宁可多醒也不误降频）。

### 2.5 `infra/playback/.../PlaybackForegroundService.kt`（暂停与息屏降频）

- ticker 间隔改为 `playbackWakeIntervalMs(playing, screenInteractive, active = 1_000, idle = 3_000)`。
- 暂停时通知内容不会自己变化（进度由系统按 `PlaybackState` 停住），因此放慢到 3s；恢复播放由播放器回调即时刷新，不受影响。

### 2.6 `core/domain/.../PlaybackWakePolicy.kt`（新增纯函数 + 测试）

`playbackWakeIntervalMs(playing, screenInteractive, activeIntervalMs, idleIntervalMs)`：

| 场景 | 结果 |
|---|---|
| 亮屏 + 播放 | `activeIntervalMs` |
| 亮屏 + 暂停 | `idleIntervalMs` |
| 息屏 + 播放 | `activeIntervalMs × 2` |
| 息屏 + 暂停 | `idleIntervalMs × 2` |

**为什么是"乘倍率"而不是"直接写死 2s/10s"**：两个调用点的合理基准不同（后台轮询暂停时本来就该 5s，通知 tick 暂停时 3s 就够）。策略只负责"息屏统一放宽一倍"，基准留给调用方，这样以后调整手感不会牵动策略。

---

## 3. 与上游合并的关系

这是本方案最重要的约束，逐条说明：

1. **新增文件优先**：`PlaybackWakePolicy.kt` 与其测试是全新文件，上游不会改到它们，合并时零冲突。
2. **纯函数放 `core:domain`**：策略与 Android 生命周期解耦，上游重写 UI 或播放器实现时，策略仍可复用；反之若上游改了策略，冲突也只是几行 when 分支。
3. **Android 侧只改"间隔表达式"**：`AndroidBackgroundPlayback` 与 `PlaybackForegroundService` 各只改 `delay(...)` 一行（加一个 `PowerManager` 字段），即使上游重排这两个文件，冲突也局限在单个语句块。
4. **`SmartRefresh.kt` 是自有文件**（非上游组件），整体重写不会产生语义冲突；但它被 `TeakActivityScaffold` 以 `Modifier.smartRefresh(settings.smartRefresh)` 调用，**签名与调用方式保持不变**。
5. **`RefreshRatePolicy` 的语义变更需要留意**：上游若也在改这个函数，需人工合并——变更点已写进本文档第 2.1 节，并有五条测试锁住新语义。
6. **不新增依赖、不改 Gradle 配置、不动资源与布局**：合并时不需要重跑任何额外的版本对齐。

---

## 4. 验证

### 4.1 自动化

```bash
# 纯策略测试（不启模拟器、不联网）
./gradlew :core:domain:test

# 编译
./gradlew :feature:video:compileDebugKotlin :infra:playback:compileDebugKotlin :app:compileDebugKotlin

# 完整门槛
./gradlew :app:assembleDebug :app:testDebugUnitTest
python3 tools/check-layering.py
./gradlew :tools:kotlin-audit:checkKotlinSizes
```

测试清单：

- `RefreshRatePolicyTest`（5 例）：播放中触控封顶 60、非播放触控交回系统、开关关闭一律 0、稳定态播放/全屏 60、静止页面 1。
- `PlaybackWakePolicyTest`（3 例）：亮屏保持原节奏、息屏两档都放宽、自定义基准是被放大而非替换。

### 4.2 手工回归（播放器三敏感点）

| 场景 | 期望 |
|---|---|
| 进入播放 → 静止 2 秒 | 帧率从系统默认降到 60Hz，画面无跳变 |
| 播放中拖动进度条 | 拖动期间不超过 60Hz，松手后保持 60Hz，不闪 |
| 暂停 → 手指滑动页面 | 立刻恢复系统满帧，滑动跟手（boost 已恢复） |
| 全屏旋转 | 帧率仍为 60Hz，控件与弹幕位置正常 |
| 画中画 | 进入/退出画中画不崩，帧率请求不残留 |
| 后台播放（熄屏） | 声音连续，通知进度正常，无卡顿；电量统计中唤醒次数明显下降 |
| 后台暂停后息屏 | 通知不再每秒刷新，恢复播放立即更新 |
| 观看 20 秒以上 | 历史记录仍按 15 秒上报（可在 debug 日志确认） |

---

## 5. 回滚

每处改动互相独立，可单独回退：

| 想撤销 | 操作 |
|---|---|
| 只恢复常驻轮询 | 还原 `SmartRefresh.kt` 一个文件 |
| 只恢复触摸 boost | 删除 `syncTouchBoost()` 调用，或让 `RefreshRatePolicy` 触控分支回到 `0f` |
| 只恢复 1s 轮询 | 还原 `AndroidBackgroundPlayback.kt` / `PlaybackForegroundService.kt` 的 `delay(...)` 表达式 |
| 只恢复上报行为 | 还原 `PlayerProgressEffects.kt` 的一处 `if` |
| 全部撤销 | `git revert <本批提交>`；新增文件不影响其它模块编译 |

---

## 6. 后续可选项（未实施）

- **`collectAsState` → `collectAsStateWithLifecycle`**：`docs/PERFORMANCE_AUDIT.md` 已列为待办，收益在后台/不可见时停止收集，与本次的"减少空转"同向，但改动面广（逐页迁移），建议单独立项。
- **弹幕遮罩的 `saveLayer`**：仅在用户开启"弹幕遮罩"时有开销，属可选项。
- **视频表面改 SurfaceView**：需要先解决 `captureThirdSize()` 的截图能力，属中等风险改造。
