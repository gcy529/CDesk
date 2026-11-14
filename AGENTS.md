# CDesk 开发约定

本文件是 CDesk 项目的行为约定，供 AI 编码代理及开发协作遵循。功能设计出处、实现记录与重构说明不在此重复，统一维护于 `docs/FEATURE_NOTES.md`。

## 版本迭代规则（适用 beta 分支）

- 每次迭代（改完代码准备推 beta 发布）必须 bump 一个小版本：`versionName` 末位 +1、`versionCode` +1，例如 `0.7.12 → 0.7.13`。
- 使用项目根目录的 `bump-version.sh` 脚本完成递增：
  - `./bump-version.sh`：写回 `app/build.gradle`
  - `./bump-version.sh --dry`：仅预览不写回
- 推 beta 会触发 CI 自动出包，资产名随 `versionName` 变化（如 `CDesk-0.7.13-beta.apk`）。
- 官网 `index.html` 与 `classic/index.html` 中的下载直链需同步更换资产名，或改为指向 `releases/tag/beta` 发布页，以始终获取最新资产。

## 构建约定（全分支通用）

- 构建命令从项目根目录执行：`./gradlew :app:assembleDebug`、`./gradlew :app:assembleRelease`。
- 必须使用 JDK 17（`/usr/lib/jvm/java-17-openjdk-amd64`）。默认 JDK 21 会导致 R8 压缩与 release 构建失败。
- 命令示例：`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug`。
- Android SDK 位于 `/opt/android-sdk`，`local.properties` 已配置 `sdk.dir`。
- GitHub CI 使用 JDK 17，构建通过。

## 架构铁律（全分支通用）

- 不使用 AndroidX / AppCompat / Material 组件，直接继承 `android.app.Activity`，保持 APK 体积小、启动快。
- 不使用 ExoPlayer。
- 不引入 `.so` 原生库。
- 不使用 API 21+ 专属特性，如需使用须做版本分支处理。
- 不使用 NotificationChannel。

## 分支约定

- `main`：稳定版主线，发布带版本号的正式 release。
- `beta`：滚动测试版主线，桌面 UI 与体验迭代走此分支。beta 分支与同名 tag 并存，推送时使用完整 refspec `refs/heads/beta` 避免歧义。
- 功能迭代与版本递增均在 beta 分支完成，验证通过后滚动发布。

## 提交规范

- 提交信息用中文，遵循约定式前缀（`feat` / `fix` / `chore` / `refactor` 等）。
- 保留 `Co-authored-by: monkeycode-ai <monkeycode-ai@chaitin.com>` 署名。
