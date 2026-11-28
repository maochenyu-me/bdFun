# 全屏转屏闪帧修复（FULLSCREEN_ROTATION_FLASH）

> 面向对象：维护者、以及每次合并上游后要复查的 AI 助手。
> 解决的问题：**横屏视频从竖屏点「展开」时，先闪一帧竖屏铺满，然后才转成横屏**。
> 设计约束：改动集中在少数几个值与少数几个文件，能单独回滚，尽量不碰上游热点结构。

---

## 0. 一句话结论

问题不在布局细节，而在**全屏状态的写入时机**。`fullscreen` 这一个值同时承担了
「用户点了展开」和「页面真的按全屏画」两种语义，点击瞬间它就翻真，而系统转屏要
200~500ms，于是先画出「竖屏窗口 + 已全屏化的骨架」。

修法是把这两种语义拆成两个值，参考 BiliPai 的 `PlayerPresentationPolicy`：

| 值 | 语义 | 生效时机 | 驱动什么 |
|---|---|---|---|
| `fullscreenIntent` | 用户点了展开 | 点击那一刻 | 朝向请求、系统栏、HDR、后台播放交接、返回键判断、清晰度回档 |
| `fullscreen`（呈现） | 页面按全屏绘制 | 窗口真的到目标朝向 | 页面骨架、播放器视口、Activity 覆盖层、`splitPanes`、控件可见性 |

转屏间隙里意图已为真、呈现仍为假，**整个页面状态完全不动**，等窗口转过去两者在同一帧一起翻。

---

## 1. 上一轮为什么失败（务必先读）

曾经的 `layoutFullscreen` 方案只推迟了 `VideoDetailLayout` / `VideoDetailPlayerViewport`
的**绘制尺寸**，而 Activity 的 `VideoActivityLayout.fullscreen`、`VideoPageOverlay` 的
「清评论 / 隐藏 tab」、`VideoPageBody` 的骨架仍读即时值。结果变成
「骨架先全屏化、画面还是半屏」，跳变反而更明显，因此被 revert（`6d227fba`）。

**教训**：这类「同一帧必须一起切」的状态，不能只改其中几层；要么都读呈现值，要么都改。

---

## 2. 判定规则

纯函数在 `core/domain/.../FullscreenPresentationPolicy.kt`，单测 7 例。

```kotlin
fun videoFullscreenPresented(
    intent, windowLandscape, portraitVideo, compactPhone, multiWindow, rotationGraceElapsed,
): Boolean
```

- `!intent` → `false`：退出全屏**不等待**，立刻回半屏（与改动前一致）。
- 竖屏视频 / 非紧凑设备 / 多窗口 → 直接 `true`：这三类场景没有「请求朝向再等转屏」这件事。
- 其余（紧凑手机的横屏视频）→ `windowLandscape || rotationGraceElapsed`。

`FULLSCREEN_PRESENTATION_GRACE_MS = 800L` 是安全阀：厂商 ROM 或多窗口下朝向请求可能不生效，
超时后照常铺满，避免「点了展开一直没反应」。BiliPai 对应值为 0（完全依赖朝向），
这里留 800ms 是为了不牺牲既有可用性。

读取环境的部分在 `VideoDetailPresentationEffects.kt` 的 `VideoFullscreenPresentation`：
从 `LocalConfiguration` 读朝向与 `smallestScreenWidthDp`，从 `Activity.isInMultiWindowMode` 读多窗口。

---

## 3. 改动清单

| 文件 | 改动 |
|---|---|
| `core/domain/FullscreenPresentationPolicy.kt` | 新增：判定纯函数 + 宽限常量 |
| `core/domain/FullscreenPresentationPolicyTest.kt` | 新增：7 例单测 |
| `feature/video/.../VideoDetailUiState.kt` | `fullscreenIntent`（意图）+ `fullscreen`（呈现）两个值 |
| `feature/video/.../detail/VideoDetailPresentationEffects.kt` | 意图 → 呈现的桥接；新增 `VideoFullscreenPresentation`；`publishIntent` 单向同步 |
| `feature/video/.../detail/VideoDetailEffects.kt` | 返回键读意图（否则间隙按返回直接退出视频页） |
| `feature/video/.../detail/VideoDetailPlayerBindings.kt` | 展开/收起按钮只写意图 |
| `feature/video/.../VideoScreenContract.kt` | `VideoScreenPresentation.fullscreenIntent` |
| `feature/video/.../VideoPlayerContract.kt` | `VideoPlayerPresentation.fullscreenIntent` |
| `feature/video/.../VideoPlayerAssembly.kt` | `VideoPlayerFlags.fullscreenIntent` 透传 |
| `player/PlayerCompositionEffects.kt` | 朝向请求读意图 |
| `player/PlayerMediaBridge.kt` | 后台播放交接读意图 |
| `player/VideoPlayerEntry.kt` | 系统栏、HDR 读意图 |
| `player/VideoPlayerLayout.kt` | 听视频「顶回全屏」读意图（读呈现值会反手推回半屏） |
| `core/application/VideoPageStateHolder.kt` | `VideoPagePlayerState.fullscreenIntent` |
| `ui/PlayerUiState.kt` | `fullscreenIntent` 投影 |
| `ui/video/VideoActivityBody.kt` | 评论面板收起回播放器写意图；`presentation.fullscreenIntent` |
| `ui/video/VideoDetailPlayerWindow.kt` | `VideoPlayerFlags(fullscreen, ..., fullscreenIntent = ...)` |
| `VideoActivity.kt` | `onResume` / `onConfigurationChanged` / 朝向交还都读意图 |

**为什么 Activity 那三处必须读意图**：`onConfigurationChanged` 时呈现值要等这次配置变更
落地后才翻（必然还是 false），读它会让 `applyIdleOrientation()` 把刚请求的横屏锁回竖屏，
表现就是「转不过去 / 转过去又弹回来」。

---

## 4. UI 验收

| 步骤 | 期望 |
|---|---|
| 竖屏打开横屏视频 → 点展开 | 画面保持半屏那一帧**不动**，随后直接横过来铺满；**不出现竖屏铺满的中间帧** |
| 横屏全屏 → 返回 | 立刻回半屏，不等待、不卡顿 |
| 横屏全屏 → 点评论 | 就地开右侧面板（不变） |
| 竖屏视频 → 点展开 | 立即进全屏（不等待；竖屏窗口没有转屏可等） |
| 平板 / 折叠屏 | 分栏行为不变，全屏立即铺满 |
| 紧凑手机竖屏视频点展开 → 立刻按返回 | 退出的是全屏，不是整个视频页 |
| 听视频进全屏 | 不被「顶回全屏」逻辑反手推回半屏 |
| 画中画进/出 | 行为不变 |

---

## 5. 回滚

| 想撤销 | 操作 |
|---|---|
| 全部 | `git revert <本次提交>` |
| 只恢复点击即铺满 | 把 `VideoFullscreenPresentation` 的返回值改成直接 `intent`（一处，其余不动） |
| 只放宽等待时间 | 调 `FULLSCREEN_PRESENTATION_GRACE_MS` |

新增文件是纯函数与单测，不影响其它模块编译。

---

## 6. 合并上游注意

- 新增的 `fullscreenIntent` 参数都带**默认值**（`= fullscreen`），
  上游若新增 `VideoPlayerPresentation` / `VideoPlayerFlags` / `VideoScreenPresentation` 的构造点，
  不传也会编译通过、行为等于改动前。
- `VideoPagePlayerState` 新增字段同理带默认值 `false`。
- 上游改动 `VideoDetailPresentationEffects.kt` 是高概率冲突点（该文件是本分支重写的），
  冲突时保留本分支版本，把上游在该文件的新增量手工并进来。
- `portraitVideo` 字段（`VideoPageBus.playerUi.portraitVideo`）**在本仓库一直没有写入点**，
  恒为 `false`。本次没有依赖它：竖屏判定在页面侧用 `quality.width/height` 现算。
  修复它是独立任务，不要和本次混在一起。