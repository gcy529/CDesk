# CDesk 开发约定

本文件是 CDesk 项目的行为约定，供 AI 编码代理及开发协作遵循。功能设计出处、实现记录与重构说明不在此重复，统一维护于 `docs/FEATURE_NOTES.md`。

## 版本迭代规则（适用 beta 分支）

- 每次迭代（改完**应用代码**准备推 beta 发布）必须 bump 一个小版本：`versionName` 末位 +1、`versionCode` +1，例如 `0.7.12 → 0.7.13`。
- 使用项目根目录的 `bump-version.sh` 脚本完成递增：
  - `./bump-version.sh`：写回 `app/build.gradle`
  - `./bump-version.sh --dry`：仅预览不写回
- 推 beta 会触发 CI 自动出包，资产名随 `versionName` 变化（如 `CDesk-0.7.13-beta.apk`）。
- 发版只需改两处：`app/build.gradle`、`CHANGELOG.md`，`version.json` 只在真正发版时同步更新。
- `classic/` 经典版页面已删除，不要再加回来。

## 官网（site 分支）

官网是独立的 `site` 分支，GitHub Pages 从这里发布（域名 `gcy5.cn.mt`）。

- **只改官网文案 / 排版 / 图片时，不要 bump APK 版本号**，也不要动 `app/build.gradle`。
  以前每次改网页都要升版本号、发一个空版本，这条就是为它加的。
- `site` 分支只放静态资源：`index.html`、`styles.css`、`app.js`、`i18n.js`、`site-version.js`、
  `assets/`、`shots/`、`archive/`、`CNAME`、`.nojekyll`。
- `version.json` **不在** site 分支：它是应用的更新检查与公告来源，必须留在 `main`（beta 渠道对应 `beta` 分支）。
  应用的 `UpdateChecker` 与该文件路径绑定，移走会导致存量用户收不到更新。
- 网页上的版本号、下载链接、SHA-256 由 `site-version.js` 运行时从 `version.json` 拉取填充，
  **不要在 HTML 里写死版本号**；新增类似元素时加 `data-ver`（下载链接加 `data-dl`，详情链接加 `data-tag`）。
- `i18n.js` 的中文原文是唯一翻译源，版本号用 `#V#` 占位，`\d+\.\d+\.\d+` 会被归一化；
  切换语言时 `i18n.js` 会整体重写 innerHTML，因此它会派发 `cdesk:lang` 事件让 `site-version.js` 重新填充。

## 构建约定（全分支通用）

- 构建命令从项目根目录执行：`./gradlew :app:assembleDebug`、`./gradlew :app:assembleRelease`。
- 必须使用 JDK 17（`/usr/lib/jvm/java-17-openjdk-amd64`）。默认 JDK 21 会导致 R8 压缩与 release 构建失败。
- 命令示例：`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug`。
- Android SDK 位于 `/opt/android-sdk`，`local.properties` 已配置 `sdk.dir`。
- GitHub CI 使用 JDK 17，构建通过。

## 架构铁律（全分支通用）

- 不使用 AndroidX / AppCompat / Material 组件，直接继承 `android.app.Activity`，保持 APK 体积小、启动快。
- 播放器沿用 ExoPlayer 2.18.1（最后一版支持 minSdk 16）；不得升级到 media3 或要求 API 21+ 的版本。
- 不引入 `.so` 原生库。
- 不使用 API 21+ 专属特性，如需使用须做版本分支处理。
- 前台服务通知：API 26+ 必须建低优先级 NotificationChannel，低版本走旧构造。

## 分支约定

- `main`：稳定版主线，发布带版本号的正式 release。
- `beta`：滚动测试版主线，桌面 UI 与体验迭代走此分支。beta 分支与同名 tag 并存，推送时使用完整 refspec `refs/heads/beta` 避免歧义。
- `site`：官网静态资源，GitHub Pages 发布源。改网页只动这里，不牵动应用版本。
- 功能迭代与版本递增均在 beta 分支完成，验证通过后滚动发布。

## 提交规范

- 提交信息用中文，遵循约定式前缀（`feat` / `fix` / `chore` / `refactor` 等）。
- 保留 `Co-authored-by: monkeycode-ai <monkeycode-ai@chaitin.com>` 署名。
