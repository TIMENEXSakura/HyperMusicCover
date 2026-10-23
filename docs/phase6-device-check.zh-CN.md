# 第六轮：歌词阅读位置、导航背景与页面保留

本轮基于 Plus5，保留用户已确认正常的导航图标着色以及此前 TTML／和声、字号、取色配置、联网控制、歌词来源与胶囊反馈。继续修复用户在真机发现的布局、二次打开、页面与面板联动问题。本文的源码定位与自动化验证不等于真机验收。

## 修改与实际原因

### 中上部阅读位置与下沿遮挡

Plus5 把更大的可用下边界同时交给布局锚点、行组缩放和绘制裁切，增加行数时也把当前句推向屏幕底部，上滑时又会重新挤动整组歌词。

现在 `LockLyrics` 与 `LyricBandGeometry.Reference` 保留本次显示请求开始时的正常媒体卡参考位置；`LyricView` 的布局和阅读锚点仍使用这个区域，只向下扩展可见裁切边界。媒体卡上滑只遮去下方歌词，不改变当前句的布局位置；卡片收起和视图重建也不重新选取拖动中的参考位置。胶囊点按淡入位于阅读锚点，不再从底部胶囊平移整页歌词。

主要文件：`lyrics/LockLyrics.java`、`LyricBandGeometry.java`、`LyricView.java`、`island/MiniPlayerController.kt`。

### 再次开启时封面与歌词一起进入

旧入口先进入封面并发送缓存图片，再恢复歌词显示／缓存；第二次打开仍带有上次隐藏状态，之后还要经过图片模糊确认门槛，形成先封面、后歌词的两段进入。

`Main.miniPlayerToggleLyrics()` 现在先准备歌词请求和缓存，再监听会话、进入原有封面场景。胶囊入口不再额外等待模糊完成才开始歌词淡入。继续复用原封面、模糊、时钟和背景配置。未获取到歌词的新歌曲仍需等待真实数据，不能承诺网络尚未返回时已有歌词画面。

主要文件：`Main.java`、`lyrics/LockLyrics.java`、`LyricPageVisibility.java`。

### 导航页面上的歌词与渐变

旧胶囊入口无条件进入封面，触发沉浸场景向封面让位。现在导航页面实际显示时，歌词作为其上的文字层打开，不开启封面；在途封面任务在最终提交、安装视图和延迟动画时校验是否仍有效，避免迟到的图片覆盖导航。

已经显示的彩色歌词用 300 ms 可反向中断的颜色过渡转成黑白，隐藏状态下先选择黑白目标再淡入；关闭时向下轻移并淡出。主歌词、和声、翻译、逐字高亮与缓存模糊文字都沿同一颜色变化处理，不修改用户保存的配色。导航结束后渐变恢复当前保存配色。

主要文件：`Main.java`、`CoverPush.java`、`lyrics/LyricView.java`、`LyricNavigationColor.java`。

### 根据实际地图像素选择黑白

Plus5 的 `navigation-theme-fallback` 使用系统主题推断地图深浅，不能反映高德独立的自动日夜地图。本轮删除这条地图主题推断路径。

`NavigationSurfaceContrast` 读取当前显示的导航远端 Surface 子树中歌词所在的中部区域，每 1.5 秒异步采样最多约 48×64 像素。`NavigationPixelContrast` 统计实际明暗像素占比；首次有效结果决定黑白，反向颜色需连续两次达到 60% 占比才切换，避免车辆移动和道路细节导致闪色。采样不包含歌词层，不保存或上传画面，只保留计数；受保护／安全图层不请求捕获。

场景、Surface 或尺寸变化使旧请求失效；只允许一个在途请求，超时不堆积新截图。权限不足、隐藏 API 不兼容、透明／异常缓冲区明确返回未知。已有有效颜色可保持；首次没有有效结果时使用已有白色中性回退，不能称为实际采样成功。实际地图图层在该 HyperOS 版本上能否读取，必须通过真机日志确认。

依据：[AOSP ScreenCapture](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/window/ScreenCapture.java)、[SurfaceControl](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/SurfaceControl.java)、[HardwareBuffer](https://developer.android.com/reference/android/hardware/HardwareBuffer)。已正常工作的导航方向图标材质处理未改。

主要文件：`NavigationSurfaceContrast.java`、`LyricContrastBackground.java`、`immersive/ImmersiveHost.java`、`LiveAlertScene.java`、`lyrics/NavigationPixelContrast.java`。

### 歌词句尾缩小淡出

`LyricLineExit` 仅在连续播放真正越过该行完整结束时间时，保存上一次实际绘制的位置并用 300 ms 缩小至 0.85、淡出；不随下一句的滚动向上飞走。结束时间包含尚未完成的背景歌词和逐字时间，仍在唱的重叠行继续显示。退场行仍跟随整页关闭位移，并受正在升起的媒体卡裁切。快进、回放、换歌、布局重建和长时间睡眠不重放陈旧退场动画。

主要文件：`lyrics/LyricLineExit.java`、`LyricView.java`。

### 展开和收起面板后保留歌词／导航

旧 `handCapsuleSceneToNative()` 在上滑时清除歌词页面请求；`CardTouch.cardSwipe` 又在实际下滑入口直接调用 `exitFromTap()`，因此只保留胶囊动画并不足以保留页面。

现在胶囊显式开启的页面与媒体卡展开状态分别管理。上滑仍展开媒体控制，下滑仅折叠控制卡，歌词和所选背景继续显示；下滑取消则回到控制卡，页面请求不变。`LyricPageVisibility` 优先使用显式歌词请求，不被已折叠卡片的零进度隐藏。

导航通过 `CapsuleSceneRetention` 保留胶囊主体显式开启的页面，打开通知面板、列表和随后回收时不关闭地图；再次点击胶囊、导航状态结束或通知真正消失才清除相应选择。普通非胶囊入口与其他类型场景沿用原行为，不结束真实导航。

主要文件：`Main.java`、`CardTouch.java`、`lyrics/LyricPageVisibility.java`、`island/MiniPlayerRuntime.kt`、`MiniPlayerController.kt`、`LockIslands.kt`、`CapsuleSceneRetention.kt`。

## 验证

- 本地独立 JVM 集成运行 **208 项测试全部通过**，覆盖地图像素统计／稳定性、图层重建保持颜色、颜色渐变打断、句尾与和声结束、布局与裁切、页面保留，以及已有 TTML、LRC、来源回退和配置相关纯逻辑。
- `git diff --check` 通过。实际触摸入口及异步封面提交通过源码调用链检查，不能等同于 Android 触摸测试。
- [GitHub Actions 构建](https://github.com/TIMENEXSakura/HyperMusicCover/actions/runs/39883893153) 对代码提交 `d072470` 的 Release APK、完整单元测试、Lint 与 Xposed 入口检查全部通过。
- 云端 **57 组、383 项测试通过**，0 失败／错误／跳过；Lint 316 条警告、5 条提示、0 错误。较 Plus5 新增的 5 条全部为地图图层反射访问的 `PrivateApi` 警告，反映 ROM 兼容性限制，不能靠构建确认实际图层捕获可用。
- APK：`HyperMusicCover-0.9.45-Plus6.apk`，版本号 `945010`，11592095 字节。已解码 Manifest 确认版本／应用 ID，并确认实际地图采样与胶囊诊断进入 DEX。
- APK SHA256：`d607bd157e715d0f3db426a3ba090b01bd5c425b1b34ad9e9291a67e14b5ca03`。
- v2 签名验证通过，证书 SHA256：`296a9ea955897efb1a4f5abd15583a03ec317ef2ac2adcc7ebbb9230c89d2540`。与 Plus5 不同，不能直接普通覆盖安装；更换测试版前先导出配置。
- APK、原始测试报告、构建日志、归档摘要、签名与核验结果保存在 `../artifacts/d072470/`（相对仓库根目录）。当前环境未执行 HyperOS 锁屏真机验证。

## 真机验收

1. 同一首有缓存歌词的歌连续打开、关闭、再打开：封面和歌词同步开始进入；当前句在中上部，增加的是下方可见行。
2. 慢速上滑媒体卡、反向、取消、完成再下滑：卡片只吞掉下面的歌词，不能把整页挤上去；面板收起后歌词继续显示。播放按钮保持独立。
3. 导航已显示时开启歌词：地图保持显示。关闭歌词应下移淡出，快速反向点击不中断真实页面状态。已有彩色歌词进入导航状态、导航结束恢复配色时均需渐变。
4. 系统深色配浅色地图，以及系统浅色配深色地图分别测试；等至少两个采样周期，确认 `navigation-surface-ratio` 与黑白选择匹配实际地图。不能仅凭颜色可读就认定采样成功。
5. 导航胶囊开启地图后上滑面板、下滑收起、多次往返及取消手势：地图仍在。再次点击胶囊才关闭模块中的地图页面，不结束真实导航。
6. 当前句结束时缩小淡出；多和声、跨行重叠和长尾逐字不能提前消失。拖动进度回放、切歌、熄屏／唤醒和系统视图重建后不残留旧动画。
7. 回归右侧导航小球提示不闪烁，方向图标颜色正确，“词”绘制／叉号可打断，以及联网开关、来源排序、同目录格式选择、普通 LRC 和 TTML 和声。

若有问题，提供操作顺序、媒体应用、图片／视频／保留壁纸模式、是否 HDR 歌词，以及对应模块日志。地图取色日志前缀为 `MCLyricContrast: navigation`，有效结果含明暗计数；失败含 `surface-not-ready`、`capture-status`、`capture-api` 或 `capture-timeout` 等原因。页面问题同时看 `capsule lyrics request`、`lyricstate`、`MCImmersive` 和 `MCMini`。不需要歌词正文、目的地、坐标或完整地图截图。

## 中文 PR 归属

页面保留分别纳入音乐／导航胶囊交互 PR；导航像素取色与颜色渐变纳入黑白歌词功能；固定阅读位置与下沿遮挡纳入布局功能；句尾缩小淡出独立 PR。用户验收通过后再按 [拆分清单](pr-split-plan.zh-CN.md) 整理上游分支。
