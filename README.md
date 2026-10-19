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
4. 构建成功后，在该次运行页面的 **Artifacts** 下载 APK 压缩包并解压。产物默认保留 14 天。

也可以在电脑上安装 JDK 21、Android SDK（API 37），设置 `ANDROID_HOME`，接受 SDK 许可后，在源码根目录执行：

```sh
chmod +x gradlew
./gradlew --no-daemon :app:assembleRelease \
  -PmcVersionName=0.9.3 \
  -PmcVersionSuffix=-multiline \
  -PmcVersionCode=903003
```

APK 位于 `app/build/outputs/apk/release/`。依赖版本以仓库配置为准，首次构建需要联网下载。

未配置签名时会使用调试密钥。GitHub 的不同构建可能使用不同密钥，无法保证覆盖安装；长期使用建议在本地通过根目录的 `keystore.properties` 配置并保管固定签名（`storeFile`、`storePassword`、`keyAlias`、`keyPassword`），不要上传密钥或密码。自行构建包也不能直接覆盖签名不同的上游安装包。后续升级需沿用同一签名并递增 `mcVersionCode`。

上游项目：[zyl6932/HyperMusicCover](https://github.com/zyl6932/HyperMusicCover)。
