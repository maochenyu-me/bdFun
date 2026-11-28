# 听视频播放器（Listen Video Player）设计与实施

> 目标：把现在横屏 `FullscreenAudioMode` 与竖屏 `InlinePlayerAudioMode` 两套听视频界面，
> 统一成一套整页全屏播放器，交互与视觉对齐 BiliPai 的 `MusicPlayerContent`。
> 参考实现：`E:\github_maochenyu_me\some-github\BiliPai`（Apache-2.0），只借鉴结构与交互，代码重写。

## 1. 需求与决策

| 议题 | 决策 |
|---|---|
| 页面形态 | 整页全屏播放器，进入后接管整个屏幕，退出回详情页；原两套形态删除 |
| 页面结构 | 封面页 + 歌词页，横向滑动切换 + 底部「封面 / 歌词」胶囊 |
| 屏幕方向 | 跟随设备方向：竖持走竖排（上图下文），横持走左右分栏 |
| 背景配色 | 从封面取样主色，压暗成三段渐变（顶 → 中 → 底） |
| 歌词来源 | B 站字幕（含 AI 字幕）为主；支持导入本地 `.lrc`；在线歌词源只留接口，暂不实现 |
| 歌词交互 | 点击歌词跳转、偏移调整、双语副字幕；当前行可选「KTV 逐字」（按字点亮）或「普通」（整行高亮，不扫亮） |
| 无字幕 | 歌词页显示空态 + 导入 lrc 入口；胶囊仍可切 |
| 音频流 | 听视频切到 DASH 纯音频流，不再拉视频数据 |
| 封面样式 | 只做圆角卡片，不做旋转唱片与频谱律动 |
| 更多菜单 | 项目已有能力优先接入，缺基础设施的项直接隐藏（见 §6） |

## 2. 页面结构

```
ListenVideoScreen（整页，接管屏幕）
├── 背景：封面模糊 + 三段调色渐变
├── 顶栏：返回 / （歌词页）翻译、搜索 / 更多
├── 内容
│   ├── 竖排（宽 ≤ 高 × 1.15）
│   │   ├── 封面页：封面卡 → 实时歌词预览 → 标题/UP → 进度 → 传输 → 音量 → 四个次级按钮（随机 / 播放模式 / 列表 / 设置）
│   │   └── 歌词页：全屏滚动歌词（当前行逐字扫亮 + 柔和过渡）→ 细进度 → 传输
│   └── 横排（宽 > 高 × 1.15）
│       └── 封面卡 + 右侧信息列（标题/UP/进度/时间/传输/次级按钮）
├── 底部胶囊：封面 / 歌词
└── 浮层：更多面板、队列面板、音质面板（自绘）、选集/倍速/收藏/评论（复用现有面板）
```

## 3. 分层与文件

`feature:video`（presentation 层，纯 Compose + 状态投影，不碰网络与存储）：

| 文件 | 职责 |
|---|---|
| `listen/ListenLyrics.kt` | 歌词模型、LRC 解析、时间轴索引、偏移、翻译合并（纯逻辑，有测试） |
| `listen/ListenVideoLayout.kt` | 排布策略、页面数、页码收敛（纯逻辑，有测试） |
| `listen/ListenVideoPalette.kt` | 主色取样、渐变推导（纯逻辑，有测试） |
| `listen/ListenVideoMenu.kt` | 更多菜单项与可用性判定（纯逻辑，有测试） |
| `listen/ListenVideoState.kt` | 页面状态：当前页、歌词偏移、音量、A-B 循环、面板开关 |
| `listen/ListenVideoScreen.kt` | 整页容器：背景、顶栏、Pager、胶囊 |
| `listen/ListenVideoCoverPage.kt` | 封面页（竖排/横排两种摆放） |
| `listen/ListenVideoLyricsPage.kt` | 歌词页 |
| `listen/ListenLyricsLine.kt` | 一行歌词的绘制：KTV 逐字叠亮（按字边界裁剪）与普通（整行高亮）两种样式 |
| `listen/ListenLyricsClock.kt` | 逐字扫亮用的帧时钟：锚点跟采样、每帧线性外推，输出单调不回退，只有采样跳变才当 seek |
| `listen/ListenVideoTransport.kt` | 进度条、传输键、音量条、底部次级按钮（随机 / 播放模式 / 队列 / 设置） |
| `listen/ListenVideoSheets.kt` | 更多面板（含定时分钟内联）与播放队列 |

`app` 负责平台接线：封面取样（Coil + `dominantCoverColor`）、本地 lrc 选择（SAF）、音频码率/音量写入。

## 4. 与现有播放器的接缝

1. **状态**：沿用 `VideoPlayerUiStateHolder.fsAudioOnly` 作为唯一开关；进入听视频时同时置 `fullscreen = true`。
2. **方向**：`BindPlayerOrientation` 在听视频时改用 `FULL_SENSOR`，不再锁横屏。
3. **切流**：`PlaybackSource` 增加 `audioOnly`；`Media3PlaybackController` 在该标记下只构建音频 `MediaSource`。
4. **音量**：`PlaybackController` 增加 `setVolume(Float)`，Media3 落到 `player.volume`。
5. **歌词**：直接读 `FullscreenPlayerInfo.subtitleCues`（已加载）作为主歌词；副字幕轨由 app 额外加载后传入。
6. **面板**：选集 / 倍速 / 收藏 / 评论复用现有 `FullscreenPanelHost`；**音质不走它**——
   听视频只出声音，这里选的是 `dash.audio` 档位（64K / 132K / 192K / 杜比 / Hi-Res），
   与 `dash.video` 的画质无关，面板由听视频自己画（`ListenAudioQualitySheet`）。
   选中档位落在 `fsAudioQualityId`，`PlayerSourceEffects` 按它换 `audioUrl` 重起播，
   位置由 `continuePlaybackSource` 保留（mediaKey 不变），不会跳回开头。

## 5. 删除与替换

- 删除 `fullscreen/FullscreenAudioMode.kt`
- 删除 `inline/InlinePlayerAudioMode.kt` 及其调用点 `PlayerInlineAudio`
- `FullscreenChrome` 在 `audioOnly` 时直接返回，不再自己画听视频
- 听视频界面改由 `VideoPlayerLayout` 以最高层渲染

## 6. 更多菜单：移植与搁置清单

**本次接入（有基础设施）**：返回视频、选集/合集、音质（真实音轨档位）、倍速、播放模式、定时关闭、
A-B 循环、双语翻译、收藏、分享、画中画、切换横竖屏、字幕/歌词设置、歌词搜索（本地）、后台播放开关。

底部四个次级按钮对齐 BiliPai 的次级传输区：**随机**（独立开关，开启即把循环收回顺序）、
**播放模式**（顺序 → 单曲 → 列表 循环，点击必关随机）、**播放队列**、**更多设置**。
A-B 循环与双语翻译收进「更多」面板，避免底部一排按钮既占宽度又语义混杂；
随机与循环的互斥规则落在 `core:domain/ListenPlayModePolicy.kt`，有纯 JVM 测试。

「更多」里的**播放模式**是就地展开的四档（顺序 / 单曲 / 列表 / 随机）：点一下只展开、不直接切档，
选中哪档就写到哪档并收起面板，和音质、选集同一种交互。四档与选中判定由 `playModeOptions` /
`currentPlayMode` 统一表达，底部两个按钮也落到同一个 `onPlayModeSelected(loopMode, shuffle)`，
避免出现两套各改一半的入口。

### 6.1 三处必须守住的边界

| 边界 | 做法 |
|---|---|
| 更多面板里点的配置项要真的打开面板 | `ListenPanelHost` 用 `zIndex(31f)` 盖在听视频层（`zIndex(30f)`）之上；否则面板开在下面看不见 |
| 面板打开时返回键先收面板 | 听视频层的 `BackHandler` 先判 `fsPanel`，再判退出听视频 |
| 切横竖屏后仍要整页 | `fullscreenAfterVideoRotation(..., listenMode)` 在听视频时返回 `null`，不把播放器收回半屏；「听视频中」由 `PlayerInlineBindings.onListen → fsActions.onAudioOnlyChanged(true)` 写入 `VideoPagePresentationState.listenMode`，不能直接改 `fsAudioOnly` 绕过它 |

横屏空间紧：右侧信息列（标题/进度/传输）可滚动，底部那排按钮常驻，不再整排被裁掉。


**本次不接入（项目暂缺，菜单里直接隐藏）**：

| 项 | 原因 |
|---|---|
| 在线歌词搜索 / 重新匹配（网易云、QQ、酷狗） | 需接第三方未公开接口；先留 `LyricsProvider` 接口 |
| 缓存音频 | 项目只有视频缓存（音视频分轨下载），没有纯音频缓存链路 |
| 导入歌单 | 无歌单体系 |
| 设备接力 | 只有 DLNA 投屏，语义不同 |
| 封面样式（方形 / 唱片）、封面律动（频谱） | 本次只做卡片 |
| 歌词界面多套样式 | 只留「KTV 逐字 / 普通」两套，更多主题不做 |
| 折叠标题、3D 唱片架 | 收益低、与现有信息层级冲突 |

## 7. 验收

- 纯逻辑：`ListenLyricsTest`、`ListenVideoLayoutTest`、`ListenVideoPaletteTest`、`ListenVideoMenuTest`
- 回归：`:feature:video:testDebugUnitTest`、`:core:application:test`、`:infra:playback:testDebugUnitTest`
- 审计：`checkLayering`、`:app:lintDebug`
- 产物：`:app:assembleDebug` → adb 安装到 `10AD6J0JKH002T6`
