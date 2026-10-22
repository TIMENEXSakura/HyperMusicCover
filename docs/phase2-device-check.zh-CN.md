# 第二阶段修改与真机验收

本文件用于集成测试，不能作为一个大 PR 整体提交上游。用户验收通过后按 `pr-split-plan.zh-CN.md` 拆分中文 PR。

## 两种优先级的最终定义

按用户最后确认的要求，全局来源仍可排序，默认词幕 → 元数据 → 同目录 → 各网络源。使用旧分支的列表拖动手柄和 MIUIX BasicComponent，越靠上越优先；没有逐项数值滑块。同目录格式独立使用 MIUIX `WindowDropdownPreference`，由控件自己的 `WindowDropdownPopup` 锚定设置项，只有“TTML 优先”和“LRC 优先”，不是底部面板。

新配置键 `lyriclocalformat` 只保存 `ttml` 或 `lrc`。加载旧状态或导入备份时，将旧 `lyricformatorder`／`formats` 中 TTML、LRC 的先后迁移一次；新键存在时总是新键胜出，与键在文件中的位置无关。新状态和新备份不写旧格式顺序，运行时请求不接受旧顺序覆盖新选项。YRC／KRC／LYS 保留为固定最后回退，无额外排序控件。来源排序与目录格式分别持久化，联网开关独立且默认开启。

## 原因、修改位置与证据边界

| 问题 | 源码确认及处理 | 主要位置 |
| --- | --- | --- |
| 高德方向图标仍黑 | 第一阶段 `ink()` 只改变文字／按钮，不改变 `note.icon`。通知行回退会把 drawable 已有颜色过滤结果绘入位图；主题资源选择也无法修复只有一份黑色资源的情况。新增针对高德功能图片的透明底中性色像素识别，玻璃／深色采用白色、浅色磨砂采用深色，保留 alpha，不改原通知或应用图标。仍未证实用户设备最后变黑的具体系统环节。 | `LockIslands.readFresh/rowIcon`、`NavigationIconRenderer`、`NavigationGlyph`、大小胶囊图片赋值 |
| 导航提示重叠 | 原主胶囊在 `drawChild` 内临时改 alpha 又立即恢复，叠加 Canvas 文字；小胶囊也在 `dispatchDraw` 额外画提示。现改为现有 TextView 的互斥内容状态，旧内容与提示不同时绘制。绑定期间保留最新原文，时间线结束后恢复最新数据。 | `CapsuleTextFeedback`、`MiniPlayerView.renderText`、`ShortcutDisc.renderCardText` |
| 音乐点按展开媒体 | 原 `tapIsland` 已调用专属函数，但该函数仍调用 `miniPlayerEnterCover()`，进入封面／媒体卡形变。现点按独立请求歌词页，新增 `LockHold.Owner.LYRICS` 只管理时钟空间和背景可读性；不改变媒体卡选择。原上滑路径保留。 | `Main.miniPlayerToggleLyrics`、`LockLyrics.toggleFromCapsule`、`LockHold`、`KeepDim`、`MediaCard` |
| 状态反馈与连续点按 | `LyricFeedbackState` 和单个 ValueAnimator 管理“词→✓／✓→×→当前播放图标”；新请求从当前帧接续，代次隔离旧回调，无堆积定时恢复任务。换歌／会话与视图移除清理提示，播放状态变化更新底层图标。动画不触发播放操作。 | `CapsuleFeedbackState`、`PressFeedbackButton`、`bindMusic/updateSmallIsland` |
| 点击、按钮和取消 | 系统窗分发先交给 `MiniPlayerRuntime.routeTouch` 并消费，再考虑底下的沉浸页面／通知；已接管的触摸发生异常时记录日志并消费，不穿透到系统面板；交换动画中按钮按压不转为主体点击。主体通过 `CapsuleBodyTouch` 和 `performClick` 处理，取消、额外手指或移出后不触发点击。 | `MiniPlayerRuntime`、`MiniPlayerView`、`CapsuleBodyTouch` |
| 词幕检测 | 旧 `installed()` 将断开也返回 true，不能表示正在运行。现在区分框架曾响应、当前连接和活动提供方；状态通过查询和预览轮询实时返回设置页。subscriber 0.1.70 在 `onConnected` 前先 `bindService` 注册活动监听，所以先缓存初始回调，握手确认后才允许取词。断开清除旧数据并回退。 | `LyriconAvailability`、`LyriconSource`、`LockLyrics`、`PreviewShots`、`ProbeReceiver`、`ModuleBridge`、`LyricSourceSettings` |
| 设置真实生效 | 全局来源串行选择，目录内部按新选项串行读取和解析；配置变化使旧请求失效。旧格式菜单与运行时旧键已移除，网络禁用仍守住实际 HTTP 入口。 | `LocalLyricFormat`、`LyricSourcePolicy`、`ModuleState`、`SettingsBackup`、`LocalLyricFiles` |

词幕“未响应”不等于没有安装 APK；插件可嵌入其他框架，仅查包名不能确认实际启用。已安装但框架未启用的情况会显示等待／未响应，不能显示运行中。框架连接成功但无活动提供方会显示等待提供方，不能宣称当前歌曲已有歌词。

## 验证记录

- 本地独立 JVM 验证 115 项通过，使用真实 TTML／LRC 解析器、Lyricon 数据模型及生产策略／状态机。覆盖时间轴、多和声、重叠、异常数据、普通 LRC、联网禁用、全局回退、格式迁移、首选格式缺失／空／损坏、动画反向打断、取消点按及图标像素分类。
- 最终云构建：[Actions 39818946091](https://github.com/TIMENEXSakura/HyperMusicCover/actions/runs/39818946091)，代码提交 `bbd7e330937e4fb2bc71cf51e4a44bd4be0197dd`。全量 JVM 单元测试 **48 组、304 项通过，0 失败／错误／跳过**；release APK、lint 和 Xposed 入口检查通过。
- Lint **310 条警告、5 条提示、无错误**；相对第一阶段仍有现存警告及新的未用资源／KTX 风格建议，未宣称 lint 无警告。第一次编译发现的 `@Volatile` 不可变属性注解错误已修复，之后完整 Android 编译通过。
- APK：`artifacts/bbd7e33/HyperMusicCover-0.9.45-Plus2.apk`（位于源码目录上一级），版本 `0.9.45-Plus2` / `945006`，11,575,711 字节。SHA-256：`8e13122d286d58369918b7c6cd361b9888080bc12a123ccc8e7bb8377a13336f`。
- 下载的 APK／报告压缩包与 GitHub 返回的 SHA-256 一致；APK 与产物内校验文件一致，v2 签名有效，Xposed 入口为 `com.os4.musiccover.Main`，包含 `com.android.systemui` 作用域。测试报告、完整云构建日志和签名检查结果保存在同一产物目录。
- **测试签名不同**：此包仍使用项目未提供发布密钥时的 Android Debug 签名，证书 SHA-256 为 `347864f821193687477bb93fd04ce70016513323392f7d79d2f1b14351ce83da`，与第一阶段的 `1a05686221a033227a933df3ba88ffc1bee2ffc3a7f466e8fb0db7087149bb71` 不同。普通覆盖安装旧测试包会被拒绝；需要先导出配置，再更换测试版并在运行框架中重新启用模块／确认作用域。未自动卸载或安装任何应用。
- 没有运行真机锁屏自动化，也没有向当前设备安装此构建。环境可读取 Android 版本，但不能直接执行原生 `su`；不把源代码检查或构建成功当成 SystemUI 行为通过。

## 真机验收

1. 使用玻璃、浅色磨砂、深色三种背景查看高德方向图标；同时检查彩色应用图标、专辑封面没有被染白。记录是否为大胶囊、小胶囊或上滑后的原生通知卡。
2. 导航主体点按立即切换模块导航页，提示完全替换原有文字且随后恢复最新方向／距离；连续点按至少十次，最终状态一致。真实导航持续运行，上滑仍打开对应面板。
3. 音乐主体开／关歌词，不弹媒体卡。开启“词→✓”，关闭“✓→×”，每次最后恢复与真实播放状态一致的图标。分别在“词”、对勾、叉号和恢复过程中反向点击。
4. 提示期间暂停／播放／换歌，检查没有旧图标残留；播放按钮只控制播放。按住后移出、取消手势、上滑、锁屏重建，不遗留缩小按钮或误切歌词。
5. 把全局来源顺序改动后重进设置、重启 SystemUI，再核对列表与实际来源；词幕无有效歌词才按当前全局顺序继续回退。
6. 同名 TTML、LRC 内容写成可区分的自有测试文本。切换两项菜单，核对真正显示的文件。再分别删除、清空、破坏优先格式，必须回退到另一格式，且不能匹配旁边另一首歌。
7. 网络关闭且本地／插件均无词时，不出现网络歌词请求；重新启动后开关仍关闭。只控制本模块请求，不控制词幕提供者自己的联网。
8. 启用／停用词幕框架或提供方：检查“等待反馈 → 已连接／等待提供方 → 提供方运行 → 断开”的真实状态，不通过安装包存在与否判定运行。

如果高德仍黑，请提供设备型号、HyperOS 与高德版本、材质（玻璃／磨砂）、深浅主题、问题所在的大小胶囊／原生面板，以及这次操作附近的 `MCNavIcon` 日志。日志只记录资源路径类别、单色判定、目标颜色、最终 tint／filter 和 ForceDark 状态，不需要导航目的地或完整通知内容。音乐问题提供同次操作的 `capsule lyrics=`、模块 `mini` 与 `lyricstate` 诊断即可；词幕问题提供 `[MCLyricon] framework=`，不必发送歌词正文。
