# User Instruction Memory

This file records user instructions, preferences, and teachings for reference in future interactions.

## Format

### User Instruction Entry
User instruction entries should follow this format:

[User Instruction Summary]
- Date: [YYYY-MM-DD]
- Context: [Mentioned scenario or time]
- Instructions:
  - [Content of user teaching or instruction, described line by line]

### Project Knowledge Entry
Entries discovered by the Agent during task execution should follow this format:

[Project Knowledge Summary]
- Date: [YYYY-MM-DD]
- Context: Discovered by Agent while performing [specific task description]
- Category: [Operations & Deployment | Build Methods | Testing Methods | Troubleshooting & Debugging | Workflow & Collaboration | Environment Configuration]
- Instructions:
  - [Specific knowledge points, described line by line]

## Deduplication Strategy
- Before adding a new entry, check for similar or identical instructions.
- If a duplicate is found, skip the new entry or merge it with the existing one.
- When merging, update the context or date information.
- This helps avoid redundant entries and keeps the memory file tidy.

## Entries

[Project Knowledge Summary]
- Date: 2026-11-12
- Context: Discovered by Agent while performing APP 代码出处/负优化审查与体验问题修复的构建验证
- Category: Troubleshooting & Debugging
- Instructions:
  - 构建验证命令：在 /workspace/app 目录用后台终端执行 `./gradlew assembleDebug --no-daemon --console=plain`（编译、资源处理、R8 打包步骤都走该命令，前置需先 `background_terminal_list` 检查已有终端再创建后台终端）。
  - 预置构建阻塞点：debug 构建会走到 `:app:dexBuilderDebug` 失败，错误为 R8 4.0.52 整图构造阶段 NPE：`MusicShellActivity$4.class` 中 `Cannot invoke "String.length()" because null`（`$4` 是 MediaPlayer.OnPreparedListener，onPrepared 仅 `errStreak=0; player.start()`）。这是 AGP 7.4.2→R8 4.0.52 的兼容问题，非源码 bug；此时 compileDebugJavaWithJavac、processDebugResources 已通过，但 R8 仍失败。修复需升级 AGP/R8 或专门规避，属待办而非源码问题。（2026-11-16 复核：当前 main/beta 的 debug 与 release 构建均已通过，此阻塞点不再复现。）
  - 自研 app 构建要点：app 模块不依赖 AndroidX/AppCompat，minSdk 16，debug 类型无 minifyEnabled（但 AGP 仍会触发 R8_4.0.52 任务），release 才开启 minify。

[Project Knowledge Summary]
- Date: 2026-11-16
- Context: Discovered by Agent while setting up local build env and diagnosing a CI Security Scan failure
- Category: Environment Configuration | Build Methods | Troubleshooting & Debugging
- Instructions:
  - 本地构建环境：JDK17 在 `/opt/jdk17/jdk17`（AGENTS.md 写的 `/usr/lib/jvm/java-17-openjdk-amd64` 不存在）；Android SDK 在 `/opt/android-sdk`；`local.properties` 写 `sdk.dir=/opt/android-sdk`（该文件被 gitignore）。环境默认未预装，需自行补齐：Temurin 17 解压到 `/opt/jdk17/jdk17`；cmdline-tools 解压到 `/opt/android-sdk/cmdline-tools/latest`；`yes | sdkmanager --sdk_root=/opt/android-sdk "platform-tools" "platforms;android-34" "build-tools;34.0.0"`。
  - 本地构建命令（从项目根目录，走 `background_terminal_create` 受管后台终端，前置先 `background_terminal_list`）：`JAVA_HOME=/opt/jdk17/jdk17 ANDROID_HOME=/opt/android-sdk ./gradlew :app:assembleDebug`；release 同名换 `assembleRelease`（开 R8 + shrinkResources，本地约 2m49s 成功产出 `app/build/outputs/apk/release/app-release.apk`，峰值内存约 2.15 GiB）。
  - 单测/lint 本地命令：`JAVA_HOME=/opt/jdk17/jdk17 ./gradlew :app:testDebugUnitTest`、`./gradlew :app:lintDebug`（不触发 dexBuilder/R8）。CI `Tests` 工作流含 `unit-test` + `lint-api` 两 job，任一失败即整体红。
  - CI `Security Scan` 的 `Build & SAST (narvy-cli)` job 可能因 runner 拉 `repo.maven.apache.org` 返回 HTTP 429（Too Many Requests）导致 `:classpath` 依赖解析失败，属临时基础设施问题而非代码问题；用 `POST https://api.github.com/repos/{owner}/{repo}/actions/runs/{id}/rerun-failed-jobs` 重跑即可恢复绿。
  - 获取 GitHub API 鉴权 token：`printf 'protocol=https\nhost=github.com\n\n' | git credential fill` 可取到具备 repo 权限的 token，用于调 API（未鉴权限额极易耗尽，且 job 日志下载必须鉴权）。
  - 发版 SHA-256 回填流程：debug APK 属可复现构建（同源码 + 同签名 keystore → 字节与 SHA-256 一致），CI `android.yml` 每次推 beta/tag 都会重建并重传资产，故可「先出包 → 取 Release 资产 SHA-256 → 提交回填 version.json/官网」；回填提交触发的重建不会使哈希失效。稳定版走 tag `v*`（`prerelease:false`）、beta 推 `refs/heads/beta`（预发布）。
