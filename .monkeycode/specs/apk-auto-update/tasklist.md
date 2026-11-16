# Implementation Plan

**Feature Name**: APK 自动更新提醒
**Input**: `requirements.md` (EARS-001 ~ EARS-010), `design.md`
**Target Path**: `app/src/main/java/com/c/launcher/`
**Language**: Java (Kotlin 设计已按"替换为项目实际语言"映射为 Java)
**Status**: Ready for execution

---

## Summary

为 C 桌面（CDesk）实现应用内更新提醒功能：

1. **数据层**：`UpdateInfo`（版本元数据 POJO）、`UpdateChecker`（单例，后台线程拉取远程版本 + 10s 超时 + 本地 `SharedPreferences` 记忆抑制）。
2. **UI 层**：`UpdateDialogFragment`（基于 `android.app.DialogFragment` 的自定义布局，不依赖 AndroidX/Material）+ `dialog_update.xml`。
3. **入口**：`MainActivity` 启动时调用 `UpdateChecker`，命中未抑制且未已安装时展示 `UpdateDialogFragment`。
4. **清单**：在 `AndroidManifest.xml` 增加 `android.permission.INTERNET`。

实现严格遵循 `ARCHITECTURE.md`：

- 不引入 AndroidX / AppCompat / Material。
- 不使用 ExoPlayer / 原生库。
- 全部调用为 API 11+ 接口，无需 `Build.VERSION` 分支。
- "立即更新"通过 `ACTION_VIEW` 跳转浏览器，不引入下载管理器。

按 `implementation-planner` 规范，每个实现任务前先完成同主题单元测试（先失败、后通过），并遵循"每任务一次提交"。

---

## Prerequisites

- [ ] 1. `git status` 显示干净工作区，且在 `betamc` 分支上
  - *Check*: `git status` 输出应为 "nothing to commit" 或仅有已知本地改动，且分支名为 `betamc`
- [ ] 2. 远程端点 URL 与密钥占位方案已确认
  - *Check*: 实现时以 `https://example.com/api/v1/version` 占位；`UpdateChecker` 接受 `context` 即可，不依赖 BuildConfig
- [ ] 3. 当前项目无 AndroidX 依赖
  - *Check*: `grep -r "androidx" app/build.gradle` 应无输出（CDesk 架构已禁止）
- [ ] 4. 项目使用 `android.app.DialogFragment`，无 Material/AndroidX Fragment
  - *Check*: `grep -r "androidx.fragment" app/build.gradle` 应无输出

---

## Task List

### 测试与实现 (Test & Implementation)

#### Phase 1: 数据层 — `UpdateInfo` 与 `UpdateChecker`

- [ ] 1. Scaffold 更新模块目录结构（创建测试包与实现包）
  - *Files*:
    - Create: `app/src/main/java/com/c/launcher/update/`（目录）
    - Create: `app/src/test/java/com/c/launcher/update/`（目录）
  - *Note*: 仅创建目录，不写类
- [ ] 2. `UpdateInfo` POJO 定义（字段 + 构造器 + getter）
  - *Files*:
    - Create: `app/src/main/java/com/c/launcher/update/UpdateInfo.java`
    - Test: `app/src/test/java/com/c/launcher/update/UpdateInfoTest.java`
  - *EARS*: EARS-001（"远程服务器返回版本元数据"被本地数据类承载）
  - *Notes*:
    - 字段：`versionCode: int`, `versionName: String`, `url: String`, `mandatory: boolean`, `releaseNotes: String`, `minSupportedCode: int`
    - 构造器全参；getter 全部
    - `minSupportedCode` 缺省 0（design.md 已确认默认值）
- [ ] 3. `UpdateChecker` 后台拉取 + 本地比较 + 抑制判定
  - *Files*:
    - Create: `app/src/main/java/com/c/launcher/update/UpdateChecker.java`
    - Test: `app/src/test/java/com/c/launcher/update/UpdateCheckerTest.java`
  - *EARS*: EARS-002, EARS-003, EARS-004, EARS-005, EARS-006, EARS-007, EARS-008, EARS-009, EARS-010
  - *Notes*:
    - **实现细节**
      - 单例模式；`check()` 同步返回 `UpdateInfo` 或 null
      - 内存缓存 `cachedRemoteInfo`
      - 后台线程使用 `Thread`（`Runnable`，避免引入额外依赖）；主线程 `check()` 通过 `Future.get(10, SECONDS)` 阻塞等待
      - 拉取 URL：`https://example.com/api/v1/version`（占位，design.md 留白项 1）
      - 超时：`HttpURLConnection.setConnectTimeout(10_000)` + `setReadTimeout(10_000)`
      - 失败语义：`IOException` / 4xx / 5xx / `JSONException` → 返回 null 并 `Log.w("UpdateChecker", ...)`，**不调** DialogFragment
      - 本地比较：`PackageManager.getPackageInfo(packageName, 0).versionCode`
      - 抑制：`SharedPreferences`（`Context.MODE_PRIVATE`）读取 `dismissed_update_code` 键
      - 当 `remote.mandatory == true` 时跳过抑制判定
    - **实现策略**
      - 单元测试：使用 `Robolectric`（如项目已引入）或纯 JUnit + `Mock`；本项目未引入 Robolectric 时，将 `PackageManager` 与 `SharedPreferences` 抽象为 `UpdateChecker` 的两个受注入依赖，便于测试（保留默认构造器用于生产路径）
      - 先写测试（断言"返回 null 时不展示"行为由 `MainActivity` 集成测试覆盖，本单元仅验证 `check()` 返回值）

- **Checkpoint**
  - *Task*: "UpdateInfo 与 UpdateChecker 实现与单元测试"
  - *Verify*:
    - `./gradlew :app:testDebugUnitTest --tests "com.c.launcher.update.*"` 全部通过
    - `git diff app/src/main/java/com/c/launcher/update/UpdateInfo.java` 字段与 design.md § 数据模型一致
    - `UpdateChecker` 中 `check()` 在以下 6 类输入下返回符合 design.md § 流程的错误处理表：(a) 远程 code > 本地, (b) 远程 code ≤ 本地, (c) 已抑制且非 mandatory, (d) 已抑制但 mandatory, (e) 拉取超时, (f) 拉取 4xx/5xx
    - 提交信息：`feat(update): add UpdateInfo and UpdateChecker with 10s timeout and suppression`

#### Phase 2: UI 层 — `UpdateDialogFragment` 与布局

- [ ] 4. 自定义布局 `dialog_update.xml`
  - *Files*:
    - Create: `app/src/main/res/layout/dialog_update.xml`
  - *EARS*: EARS-005, EARS-006, EARS-008（UI 资源层）
  - *Notes*:
    - 根节点 `LinearLayout`（垂直），宽 `match_parent`、高 `wrap_content`
    - 内部控件：`TextView`（版本名）+ `TextView`（releaseNotes）+ 两个 `Button`
    - 背景：`@color/background_color`（已存在，值为 `#faf6ef`）
    - 适配 `minSdk 14`：不使用 `?android:attr/selectableItemBackground` 之外的 AppCompat 资源
- [ ] 5. `UpdateDialogFragment` 继承 `android.app.DialogFragment`
  - *Files*:
    - Create: `app/src/main/java/com/c/launcher/ui/UpdateDialogFragment.java`
    - Test: `app/src/test/java/com/c/launcher/ui/UpdateDialogFragmentTest.java`
  - *EARS*: EARS-005, EARS-006, EARS-008, EARS-009, EARS-010
  - *Notes*:
    - **实现细节**
      - `onCreateDialog` 返回 `new Dialog(context, 0)` 并 `setContentView(R.layout.dialog_update)`
      - 通过 `setArguments(Bundle)` 传 `UpdateInfo` 数据（POJO 用 `Serializable`，`versionCode` 等原始字段直接 `putInt`）
      - "立即更新" 按钮：`Intent(Intent.ACTION_VIEW, Uri.parse(info.url))` + `Intent.createChooser(intent, "选择浏览器")`，`startActivity`；`catch (ActivityNotFoundException)` → `Toast`
      - "暂不更新" 按钮：`SharedPreferences.edit().putInt("dismissed_update_code", info.versionCode).apply()` + `dismiss()`
      - 当 `info.mandatory == true` 时隐藏"暂不更新"按钮（design.md § 留白项 2：`mandatory` 语义即"必更"，隐藏 dismiss 按钮是直观映射）
      - 静态文本（"发现新版本"、"立即更新"、"暂不更新"）使用 `@string` 资源，预留 i18n 入口
    - **实现策略**
      - 单元测试：断言 `mandatory=true` 时"暂不更新"按钮 `visibility == GONE`；断言"立即更新"触发 `startActivity` 时携带 `ACTION_VIEW`
      - 项目若未引入 Robolectric，Fragment 级测试可降级为：构造 `UpdateDialogFragment` 实例后调用 `onCreateDialog` 的 mock 验证逻辑；或用 `Robolectric` 若 build.gradle 已允许

- **Checkpoint**
  - *Task*: "UpdateDialogFragment 与自定义布局"
  - *Verify*:
    - `./gradlew :app:assembleDebug` 通过（验证 `res/layout/dialog_update.xml` 资源 ID 正确）
    - `grep -r "androidx" app/src/main/java/com/c/launcher/ui/UpdateDialogFragment.java` 应无输出
    - 提交信息：`feat(update): add UpdateDialogFragment with ACTION_VIEW and suppression persistence`

#### Phase 3: 入口集成与清单

- [ ] 6. `MainActivity` 启动时触发更新检查
  - *Files*:
    - Modify: `app/src/main/java/com/c/launcher/MainActivity.java`（或实际入口 Activity）
    - Test: `app/src/test/java/com/c/launcher/MainActivityUpdateTest.java`
  - *EARS*: EARS-002, EARS-004
  - *Notes*:
    - **实现细节**
      - 在 `onCreate` 末尾（或 `onResume`，避免冷启动未完成时打断用户）调用 `UpdateChecker.getInstance().check(this)`
      - 结果非空且 `!UpdateChecker.isDismissed(this, info.versionCode)` 时：`new UpdateDialogFragment().show(getFragmentManager(), "update")`
      - 结果非空但已抑制或 mandatory：直接 `show`（`isDismissed` 内部已处理 mandatory 跳过抑制）
      - 结果 `null`：静默，不弹
    - **实现策略**
      - 单元测试：mock `UpdateChecker.check()` 返回 `UpdateInfo` 与 `null` 两种情况，断言 `show` 调用 / 不触发 DialogFragment
      - 不修改 Activity 现有逻辑；新增代码集中在一个私有方法 `triggerUpdateCheckIfNeeded()` 内，降低改动面

- [ ] 7. `AndroidManifest.xml` 增加 `INTERNET` 权限
  - *Files*:
    - Modify: `app/src/main/AndroidManifest.xml`
  - *EARS*: EARS-001（拉取远程端点需网络）
  - *Notes*:
    - 在 `<uses-permission>` 区域（如已有则不重复）添加 `<uses-permission android:name="android.permission.INTERNET" />`
    - 提交信息：`feat(update): add INTERNET permission to manifest`

- **Checkpoint**
  - *Task*: "MainActivity 集成 + 清单权限"
  - *Verify*:
    - `./gradlew :app:assembleDebug` 通过
    - 提交信息：`feat(update): wire UpdateChecker into MainActivity and add INTERNET permission`

#### Phase 4: 端到端验证

- [ ] 8. 端到端行为验证（手工或自动化）
  - *Files*: 无新增；验证既有代码
  - *EARS*: EARS-005 ~ EARS-010 全量
  - *Notes*:
    - **实现策略**
      - 启动应用，观察是否弹出更新对话框（在 remote 端点未真实部署时，应静默无对话框，EARS-004）
      - 用 `adb shell am broadcast` 或修改本地 mock 端点验证：
        - 正常更新提示 + "立即更新" 触发浏览器
        - "暂不更新" 后重启应用不再弹
        - `mandatory=true` 时"暂不更新"按钮隐藏
      - 断网或 404 端点：应用正常启动，无对话框、无崩溃（EARS-004/009）
      - 旋转屏幕：`DialogFragment` 状态保留（`onRetainInstance` 默认 DialogFragment 由FragmentManager 管理）
    - 验证结果写入 commit message 的 `chore(update): e2e verification results` 段落

- [ ] 9. 提交并同步
  - *Note*: 所有任务已分批 commit，最后一个 commit 为 e2e 验证记录
  - *Verify*: `git log --oneline -10` 应看到 `feat(update):` 系列提交

---

## Dependencies

- **数据层 → UI 层**：`UpdateInfo` 必须先于 `UpdateDialogFragment` 完成（后者通过 `arguments` 传前者）
- **UI 层 → 入口**：`UpdateDialogFragment` 必须先于 `MainActivity` 集成（后者构造 `show` 调用）
- **清单 → 全部**：`INTERNET` 权限是运行时拉取网络的前提，建议最后加（与 e2e 验证同步）
- **`UpdateChecker.check()` 在 10s 内必须返回**：`Future.get(10, SECONDS)` 是硬约束；`HttpURLConnection` 两个 timeout 必须都设 10s

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| `UpdateChecker` 使用线程时，单元测试无法直接测 `HttpURLConnection` | 抽象 `RemoteFetcher` 接口（可选），将 `HttpURLConnection` 实现为可注入依赖；测试用 fake 实现 |
| `SharedPreferences` 在单元测试中无法直接读写 | 抽象 `SuppressionStore` 接口，测试注入 fake；默认实现使用 `Context.getSharedPreferences` |
| `DialogFragment` 单元测试依赖 `FragmentManager` | 单元测试仅测 `onCreateDialog` 返回的 Dialog 控件与按钮逻辑；`show` 行为留给 e2e 手工验证 |
| `ACTION_VIEW` 在某些车机 ROM 上无浏览器可接 | `try/catch ActivityNotFoundException` → `Toast` 兜底（EARS-008） |
| 远程端点 URL 占位长期不替换 | 在 `UpdateChecker` 顶部以 `// TODO(remote): replace placeholder URL` 标记，避免被遗忘 |
| `minSdk 14` 不支持 `Thread.Builder`（API 30+） | 使用 `new Thread(...)` 直接构造，避免高版本 API |

## Notes

- `design.md` 留白项 1（远程端点 URL）：占位 `https://example.com/api/v1/version`，e2e 验证阶段再替换为真实 URL
- `design.md` 留白项 2（mandatory UI 影响）：实现中映射为"隐藏暂不更新按钮"，commit message 中标注
- 语言映射：`design.md` 以 Kotlin 示例，本项目为 Java，所有 `.kt` 等价替换为 `.java`，逻辑不变
- 提交策略：每任务一 commit；commit 前缀 `feat(update):` / `chore(update):`；避免 `WIP` 提交
- 测试位置：单元测试放 `app/src/test/java/com/c/launcher/update/` 与 `app/src/test/java/com/c/launcher/ui/`；若项目使用 Robolectric，UI 级测试放 `app/src/androidTest/java/...`
