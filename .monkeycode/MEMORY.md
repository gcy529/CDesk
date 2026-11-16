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
  - 预置构建阻塞点：debug 构建会走到 `:app:dexBuilderDebug` 失败，错误为 R8 4.0.52 整图构造阶段 NPE：`MusicShellActivity$4.class` 中 `Cannot invoke "String.length()" because null`（`$4` 是 MediaPlayer.OnPreparedListener，onPrepared 仅 `errStreak=0; player.start()`）。这是 AGP 7.4.2→R8 4.0.52 的兼容问题，非源码 bug；此时 compileDebugJavaWithJavac、processDebugResources 已通过，但 R8 仍失败。修复需升级 AGP/R8 或专门规避，属待办而非源码问题。
  - 自研 app 构建要点：app 模块不依赖 AndroidX/AppCompat，minSdk 16，debug 类型无 minifyEnabled（但 AGP 仍会触发 R8_4.0.52 任务），release 才开启 minify。
