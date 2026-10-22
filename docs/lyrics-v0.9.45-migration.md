# v0.9.45 锁屏歌词与胶囊修改说明

基线为正式版 `v0.9.45`（`3f0eaa4`），已合并本地取得的上游 `main`（`2119236`）。
参考用户分支 `stable-lyrics-fix`（`5440368`）及已迁移的 `feat/lockscreen-lyrics-plus`（`d85c3e3`）。
开发分支为 `fix/lyrics-and-capsule-controls`。

第二阶段针对用户真机反馈继续修复。最新差异、验收步骤和证据边界见 [第二阶段验收说明](phase2-device-check.zh-CN.md)。下方第一阶段构建记录只代表当时的代码，不代表第二阶段已经通过真机测试。

## 已确认的问题与证据范围

- 当前上游已实现 Lyricon `secondaryWords` 的读取及全文／词表不一致时的重建，保留了解析和绘制异常隔离；这些不是此次首次修复的能力。
- 当前 `LyricParse` 对嵌套 TTML 和声只取 `accompanimentLines.firstOrNull()`；独立和声行也只在 `owner.bg == null` 时保留。多段和声会因此丢失。
- 内部行尾此前只参考最后一个词的结束时间，不能表示“前词结束晚于后词”的重叠情况；无主唱文字的 Lyricon 行直接返回空值，会连带丢失其和声。
- `overlapping-backgrounds.ttml` 使用自编的短文本复现同一主唱带两段和声、主唱间重叠、逐字尾音超过下一主唱开始及空行情况。
- 已检查现有本地 Lyricon 日志。日志包含主唱与 secondary／secondaryWords 分离的数据，但没有找到能把用户报告的 v0.9.3 崩溃与某条调用栈绑定的 SystemUI fatal 记录。因此不把“转换器截断导致当年崩溃”写成已证实的唯一根因。
- 胶囊单色控件曾读取通知／媒体卡文字颜色，而展开卡片和胶囊可能使用不同材质；应用提供的深浅色图片也必须按胶囊材质选取。已针对这些代码路径处理，实际手机上的反色是否还有其他系统环节参与，仍需真机确认。

## 最终行为

### 歌词转换与外观

- Lyricon 主唱、和声使用独立转换和时间轴。复用用户分支的和声重建方案，匹配完整外层括号时仅调整显示副本，不改输入数据，也不删除正常重复歌词。
- TTML 保留同一行的全部和声；主唱与各和声分别建立布局和逐字缓存。焦点区间包含和声时间，行尾采用所有有效词尾的最大值，词首早于声明行首时也保留。
- 字符边界越界、数组长度不一致、负数／倒序时间等非法逐字信息降级为完整逐行文字；其余歌词继续使用。继承上游解析、绘制的异常保护。
- 主歌词 18–36 sp、和声 10–30 sp，独立保存。沿用多段布局、换行和可用高度适配；单个主唱带多段和声时也参与高度适配。
- 颜色支持官方、封面莫奈、自定义、系统动态取色；系统资源不可用时回退官方配色。默认和声字号、透明度低于主歌词，保留层级。自定义颜色的真机可读性仍需用户按背景核对。
- UI 复用 MIUIX 的开关、滑块、对话框、颜色选择器与主题。

### 来源与文件优先级

默认顺序：词幕（Lyricon）→ 歌曲元数据／音频内嵌标签 → 同目录文件 → AMLL → TTML Hub → QQ → 网易云 → 酷狗 → 酷我 → LRCLIB。

- 全局优先级使用原分支的 MIUIX 列表拖动手柄；越靠上越优先，移动一项时其他项稳定顺移。按最新要求允许调整各来源，不把前三来源锁死。
- 按顺序获取并解析，首个有效结果生效。跨来源不竞速；迟到回调受歌曲代次、配置版本和来源优先级约束。
- “允许从网络源获取歌词”默认开启。关闭后跳过在线来源、预取和各实际 HTTP 请求入口；所有本地来源失败也不会自动联网。已经发出的请求可能完成，但旧配置的回调不再发布结果。词幕提供者自身的联网不由本模块控制。
- 延续移植分支的“首个有效来源”行为，不再跨来源补充缺失的译文／罗马音；保留选中来源本身携带的译文／罗马音。
- 同目录格式使用 MIUIX 锚点小菜单，只有“TTML 优先”和“LRC 优先”，默认前者。先后尝试两者后，YRC／KRC／LYS 仍作为固定的最后回退以保留已有支持；无额外格式排序控件。KRC 仍要求解密文本。旧格式排序仅迁移一次，保存新键后不再参与运行时选择。
- 保留音频匹配规则；只读取同目录同名文件，空文件、超限文件、解析无效文件按顺序回退，不能匹配另一首歌。
- 配置通过 ModuleBridge → ProbeReceiver → ModuleState 保存，同时纳入设置备份／恢复。

### 胶囊点按与手势

点按优先级：音乐 → 导航 → 闹钟／计时器 → 其他已有沉浸场景 → 普通通知。

- 音乐主体直接控制独立歌词页面，钟表空间由独立 LYRICS 所有者管理，不调用进入封面或展开媒体卡。开启提示依次为“词”和对勾，关闭时对勾变为叉号，随后恢复当前播放／暂停图标；允许连续中断。
- 导航主体切换已有沉浸导航内容，在原有文字视图中暂时替换为“已进入导航界面”／“已退出导航界面”，再恢复最新导航文本；不再使用画布覆盖提示。只控制模块内容，不结束真实导航，不猜测地图 Activity。
- 闹钟／计时器复用已有倒计时操作页、系统点击器或通知自带 PendingIntent。没有可用入口时显示提示，不穿透到通知列表。
- 专属场景暂未就绪时由其消费点按，普通通知才采用原版展开。被锁屏隐私隐藏的通知仍走系统原有入口。
- 主胶囊、小胶囊及切换动画中的点按采用同一分派函数。原有上滑拖拽／面板路由保留，独立按钮仍执行原操作。
- 播放控制按压最低缩放 0.65，仅缩放绘制内容；取消、移出、失焦、禁用、移除时复原。
- 每类提示只有一条可取消时间线，无延时恢复任务队列；新点击失效旧动画回调。换歌／换会话／视图移除时清理提示，播放状态更新只更新底层真实图标。
- 单色文字／控件按玻璃或磨砂材质取色，彩色应用图片保留自身颜色并优先选择对应主题资源。

## 关键文件

| 范围 | 文件（位于 app/src/main/java/com/os4/musiccover） |
| --- | --- |
| 转换、模型与渲染 | lyrics/LyricParse.kt、LyriconConverter.kt、LyriconBackground.kt、LyricLine.java、LyricFocus.java、LyricView.java |
| 来源与请求 | lyrics/LyricSourcePolicy.java、LyricSource.java、LyricHttp.java、LocalLyricFiles.java、LocalLyrics.java、各在线来源 |
| 外观与设置 | lyrics/LyricStyle.java、LyricAppearance.java、ui/screen/features/LyricSourceSettings.kt、LyricAppearanceSettings.kt |
| 配置传递与保存 | ModuleState.java、ProbeReceiver.java、app/ModuleBridge.kt、app/SettingsBackup.kt |
| 胶囊行为 | island/CapsuleTapPolicy.kt、MiniPlayerController.kt、MiniPlayerView.kt、PressFeedbackButton.kt、LockIslands.kt、Main.java |

## 验证与交付状态

云构建已于 2026-10-22 通过：[Actions 39810626165](https://github.com/TIMENEXSakura/HyperMusicCover/actions/runs/39810626165)。代码提交为 `595eef47410c7a35b724568e96309df69593691a`。工作流使用 `workflow_dispatch` 手动触发，尚未提交任何上游 PR。

云端全量单元测试 **288 项通过，0 失败、0 错误、0 跳过**；release APK、lint 和 Xposed 入口／作用域检查通过。Lint 报告仍包含 309 条 Warning、5 条 Hint，没有 Error；不将 lint 任务成功表述为零警告。

产物版本 `0.9.45-Plus`，versionCode `945005`。APK 的 SHA-256 为：

```text
bff246ab3004c13860527dc46e01c2686d6d8079a39f7d410f5aa9a3e87756e2
```

下载后的压缩包摘要与 GitHub artifact digest 相符，APK 摘要与云端 SHA256SUMS 相符。apksig 验证通过（v2 签名，Android Debug 测试证书）。这份安装包不是上游正式签名发布包。

本地交付目录：`Download/codex/HyperMusicCover-v0.9.45/artifacts/595eef4/`，包括 APK、SHA256SUMS、云端构建日志、JUnit/lint 报告及签名验证结果。

云构建时补齐了 ShortcutDisc 与 MiniPlayerView 的共同反馈接口；小圆胶囊在原区域内完整显示短暂状态提示，不为提示展开通知或修改触摸区域。

本机独立 JVM 回归通过 **99 项测试**，资源 XML 检查及 `git diff --check` 通过。使用独立 JVM 编译生产逻辑并运行测试，使用真实 lyrics-core 0.4.7 和 Lyricon 0.1.70 模型；仅 Xp 日志和 LockLyrics 的两个常量使用占位类，Android jar 仅提供协议模型所需的类型。该结果不等同于完整 Android 工程构建。

新增／扩展的回归覆盖：多段 TTML 和声、独立桥接词表、纯和声行、越界／倒序、时间首尾与数组隔离、普通 LRC、同目录文件优先级与解析回退、联网禁用的实际连接拦截、来源顺序／迟到结果、外观保存、胶囊点按优先级。

已修正上游迁包后遗留的测试包名，取消只编译歌词测试的限制。云端执行的检查命令：

```sh
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:lintDebug
./gradlew --no-daemon :app:assembleRelease -PmcVersionName=0.9.45-Plus -PmcVersionCode=945005
```

工作流还检查 APK 中的 Xposed 入口／作用域并上传 APK、SHA-256、测试和 lint 报告。完整编译与云端检查已通过，新 APK 已下载，真机锁屏／SystemUI 验证仍待用户完成。

### 真机待测

- Flamingo／词幕的实际歌曲、拖动进度、切歌、多个重叠和声与普通 LRC；SystemUI 日志无新增崩溃。
- 主／和声字号极值、长行、多段和声、翻译、浅色／深色／玻璃背景、系统取色变化及 AOD。
- 关闭网络后重播、切歌、本地全空、配置变化时请求正在进行；确认无新网络歌词请求。
- 调整来源及 TTML/LRC 顺序，验证解析失败回退；重启 SystemUI 和导入／导出后设置保留。
- 音乐／导航主体点按、连续点按、动画中点按；播放／暂停在提示期间变化后恢复正确图标。
- 上滑、按钮点击、拖出取消、系统手势中断、主／小胶囊切换；确认没有意外通知展开或重复点击。
- 高德等导航图标和多色应用图标、闹钟／倒计时入口、受隐私保护通知的系统验证流程。

等待用户安装验证通过后，按 [PR 拆分清单](pr-split-plan.zh-CN.md) 将各功能分别整理为中文 PR，不能将当前整合分支整体提交给上游。
