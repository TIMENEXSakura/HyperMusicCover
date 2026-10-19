# HyperMusicCover

HyperOS 4 音乐锁屏

## 维护声明

本仓库不提供任何开发支持，仅进行必要的 Bug 修复，并定期同步上游稳定版 Tag。除此之外，不做任何处理。

此版本以后有可能停止维护（由于原作者会修复bug）
目前该版本主要负责的是TTML歌词获取错误导致的系统界面崩溃问题。

本仓库不提供稳定版预编译安装包，需要自行编译。或者从Actions中下载CI构建。
工作流产物仅用于自行构建和测试，不作为稳定版发布，也不保证稳定性。

遇到问题请卸载该分支并使用原版，或等待自行发现修复。

酷安：@TIMENEX

## 编译建议

推荐 Fork 后使用 GitHub Actions，无需在手机上安装 Android SDK：

1. Fork 本仓库，选择包含修改的 `stable-lyrics-fix` 分支。
2. 在自己的仓库进入 **Actions**，启用工作流。
3. 选择 **Build lyrics fix APK** → **Run workflow**，分支选择 `stable-lyrics-fix`。
4. 构建成功后，在该次运行页面的 **Artifacts** 下载 `HyperMusicCover-0.9.3-Plus` 压缩包，解压得到 `HyperMusicCover-0.9.3-Plus.apk`。产物默认保留 14 天。

## 0.9.3-Plus

锁屏歌词支持自定义来源顺序，开启后可拖动右侧三横杠排序。默认依次读取歌曲元数据（含可读取的音频内嵌标签）、同目录文件、词幕、AMLL、TTML Hub、QQ、网易云、酷狗、酷我和 LrcLib；找到有效歌词即停止。低优先级结果不能覆盖已采用的歌词，关闭网络开关后不再发起网络歌词查询。词幕提供者自身的联网不受此开关控制。

同目录文件旁的“格式设置”可调整 TTML、LRC、YRC、KRC、LYS 的顺序，无效文件会继续尝试下一格式。KRC 仅支持解密文本。只有播放器元数据能够定位到本机音频时，才可读取其标签及同名旁置文件。

歌词缩放改为围绕段落中心，多段布局与居中共用滚动弹簧，字号变化同步补偿绘制位置。保留 HDR 开关及原有发光效果。实际流畅度仍需在目标设备验证。

工作流先运行歌词专项测试（`./gradlew :app:testDebugUnitTest -PmcLyricTestsOnly=true`），再构建 APK。该参数只编译歌词测试目录；仓库其余旧测试存在包名迁移后未同步的问题，未纳入本次验证。

也可以在电脑上安装 JDK 21、Android SDK（API 37），设置 `ANDROID_HOME`，接受 SDK 许可后，在源码根目录执行：

```sh
chmod +x gradlew
./gradlew --no-daemon :app:assembleRelease \
  -PmcVersionName=0.9.3 \
  -PmcVersionSuffix=-Plus \
  -PmcVersionCode=903005
```

APK 位于 `app/build/outputs/apk/release/`。依赖版本以仓库配置为准，首次构建需要联网下载。

未配置签名时会使用调试密钥。GitHub 的不同构建可能使用不同密钥，无法保证覆盖安装；长期使用建议在本地通过根目录的 `keystore.properties` 配置并保管固定签名（`storeFile`、`storePassword`、`keyAlias`、`keyPassword`），不要上传密钥或密码。自行构建包也不能直接覆盖签名不同的上游安装包。后续升级需沿用同一签名并递增 `mcVersionCode`。

上游项目：[zyl6932/HyperMusicCover](https://github.com/zyl6932/HyperMusicCover)。
