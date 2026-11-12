# 技术设计：APK 自动更新提醒

> 配套 `requirements.md`（EARS-001 ~ EARS-010）。

## 1. 模块结构

```
com.example.app (Application 级)
├── update/
│   ├── UpdateChecker.kt      // 单例：检查远程版本、缓存结果
│   ├── UpdateInfo.kt         // 数据类：versionCode, versionName, url, mandatory, releaseNotes
│   └── UpdatePrefKeys.kt     // SharedPreferences 常量
├── ui/
│   └── UpdateDialogFragment.kt  // 更新提醒对话框（兼容 4.0+）
└── MainActivity.kt           // onCreate 中触发 UpdateChecker
```

> 注：项目若为 Java，对应类为 `.java`，逻辑不变。本文用 Kotlin 示例以便简洁，落地时按项目实际语言替换。

## 2. 关键接口定义

### 2.1 远程端点约定

- URL：`https://example.com/api/v1/version`（**占位**，实际地址由产品方提供后替换）
- 方法：`GET`
- 响应体（JSON）：

```json
{
  "versionCode": 42,
  "versionName": "2.3.1",
  "url": "https://cdn.example.com/app/app-release-v42.apk",
  "mandatory": false,
  "releaseNotes": "修复若干问题",
  "minSupportedCode": 30
}
```

| 字段 | 类型 | 必选 | 说明 |
|---|---|---|---|
| `versionCode` | int | 是 | 服务端最新版本的整型码 |
| `versionName` | string | 否 | 展示用的语义化版本 |
| `url` | string | 是 | APK 直链 |
| `mandatory` | bool | 否 | 默认 `false`；`true` 时不提供"稍后提醒我"按钮 |
| `releaseNotes` | string | 否 | 展示在对话框 |
| `minSupportedCode` | int | 否 | 若本地 < 此值，提示强制升级（可选策略，本期保留字段不启用） |

### 2.2 本地调用

- `UpdateChecker.check(context)`：
  - 入参：`Application` 或 `Context`
  - 返回：`UpdateInfo?`（`null` = 无更新）
  - 行为：
    1. 命中内存缓存 → 直接返回（EARS-006）
    2. 未命中 → 在后台线程 `HttpURLConnection` GET，超时 10s（EARS-004/EARS-005）
    3. 网络失败 → 记 `Log.w`，返回 `null`（EARS-004）
    4. HTTP 4xx/5xx 或 JSON 解析失败 → 视为"无更新"，返回 `null`（EARS-009）
    5. 成功 → 解析 `UpdateInfo`，缓存，返回

- `UpdateChecker.isDismissed(context, remoteCode)`：
  - 读 `SharedPreferences` 中 `dismissed_update_code`
  - 若 `remoteCode <= dismissedUpdateCode` 且非 mandatory → 返回 `true`（EARS-008）
  - mandatory 时恒返回 `false`（EARS-003）

- `UpdateChecker.markDismissed(context, code)`：
  - 写 `dismissed_update_code = code`（EARS-008）

### 2.3 SharedPreferences 键

| 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `dismissed_update_code` | int | 0 | 用户最近一次"稍后提醒我"对应的 versionCode |

## 3. UI 组件设计

### 3.1 对话框选型：`DialogFragment`（兼容 4.0+）

- 选 `DialogFragment` 而非 `AlertDialog.Builder`，原因：
  - 支持方向旋转不丢失状态
  - 可自定义 `View` 布局（`releaseNotes` 滚动区 + 按钮区）
  - 不需要 `androidx.fragment`，仅用原生 `android.app.DialogFragment`（**兼容 4.0+**）

### 3.2 布局

`res/layout/dialog_update.xml`：

```
LinearLayout (vertical, padding 24dp)
├── TextView (title) "发现新版本 ${versionName}"
├── ScrollView (maxHeight 200dp)
│   └── TextView (releaseNotes, autoLink off)
└── LinearLayout (horizontal, weightSum=2)
    ├── Button (稍后提醒我)   ← mandatory=false 时显示
    └── Button (立即更新)
```

- 文字色：`#3d3529`（继承 `docs` 主题）
- 按钮背景：`#ff6b35`（主色，与 `docs` CTA 一致）
- 圆角：4dp

### 3.3 交互流

| 用户操作 | 结果 |
|---|---|
| 点"立即更新" | `startActivity(ACTION_VIEW, uri=url)`，try/catch `ActivityNotFoundException` → 回退 `ClipboardManager` + `Toast`（EARS-007）；然后 `markDismissed(code)` |
| 点"稍后提醒我" | `dismiss()` + `markDismissed(code)`（EARS-008） |
| mandatory=true 且点"立即更新"失败 | 回退后**不** `markDismissed`，下次仍会弹（避免用户因浏览器异常被永远跳过） |
| 对话框被 swipe 关闭（若系统允许） | 等同"稍后提醒我"，调用 `markDismissed`（EARS-008） |

## 4. 时序图

```
AppLaunch
  │
  ├─ onCreate 完成
  │
  ├─ 主线程：if (!UpdateChecker.isDismissed(remoteCode)) showUpdateDialog()
  │           （首次启动时 remoteCode 未知，先显示"正在检查…"占位对话框或静默等待）
  │
  └─ 后台线程：UpdateChecker.check()
        │
        ├─ 成功 → 主线程 Handler.post{ showDialog(info) }
        ├─ 失败（网络/解析/4xx） → 无 UI 变化，静默（EARS-004/EARS-009）
        └─ 缓存命中 → 直接回主线程
```

> 简化决策：首屏展示前等待一次 `check()` 结果（最多 10s），避免"正在检查…"占位 UI。若 10s 内无结果则不弹框，下次启动再试（EARS-006 保证同会话不重复）。

## 5. 错误与边界

| 场景 | 处理 |
|---|---|
| `versionCode` 字段缺失或非 int | 解析失败 → 视为无更新（EARS-009） |
| `url` 非 http(s) 开头 | 拒绝展示对话框，记 `Log.e`，不崩溃 |
| 响应体 > 64KB | 截断读取，解析失败 → 视为无更新 |
| 应用无 INTERNET 权限 | `check()` 抛 `SecurityException` → catch 后返回 `null` |
| `ACTION_VIEW` 无处理器 | 回退剪贴板 + Toast（EARS-007） |
| `SharedPreferences` 写入失败（磁盘满） | `catch` `IOException`，仅记日志，功能降级（下次启动仍会弹框） |
| 远程 `versionCode` 比本地旧 | 本地 `BuildConfig` 比较，若 `remote < local` 直接返回 `null` |

## 6. 合规核对（对应 ARCHITECTURE.md 铁律）

| 铁律 | 本设计是否遵守 |
|---|---|
| 1. 无 AndroidX / AppCompat / Material | ✅ 仅用 `android.app.*`、`android.webkit.*`、`java.net.HttpURLConnection`、`org.json` |
| 2. 无原生库 / ExoPlayer | ✅ 纯 Java/Kotlin 标准库 |
| 3. 单一 APK 覆盖 4.0–15 | ✅ 无 API 分支调用（本设计未使用任何 21+ 独有 API） |
| 4. 广告与变现零容忍 | ✅ 纯更新提醒，无任何广告 SDK |
| 5. 无第三方 SDK | ✅ 无 |

> API 兼容性检查：`HttpURLConnection`（API 1+）、`DialogFragment`（API 11+，`minSdk 14` 满足）、`BuildConfig`（Gradle 生成）、`SharedPreferences`（API 1+）。**无需任何版本分支**。

## 7. 文件清单（待创建）

| 文件 | 类型 | 说明 |
|---|---|---|
| `app/src/main/java/com/example/app/update/UpdateInfo.kt` | 新增 | 数据类 |
| `app/src/main/java/com/example/app/update/UpdateChecker.kt` | 新增 | 核心检查逻辑 |
| `app/src/main/java/com/example/app/update/UpdatePrefKeys.kt` | 新增 | 常量 |
| `app/src/main/java/com/example/app/ui/UpdateDialogFragment.kt` | 新增 | 对话框 |
| `app/src/main/res/layout/dialog_update.xml` | 新增 | 布局 |
| `app/src/main/AndroidManifest.xml` | 修改 | 追加 `<uses-permission android:name="android.permission.INTERNET"/>`（若尚未声明） |
| `app/src/main/java/com/example/app/MainActivity.kt`（或对应入口 Activity） | 修改 | `onCreate` 中接入 `UpdateChecker` + `UpdateDialogFragment` |

## 8. 开放问题

1. 远程版本端点（域名 + 路径）尚未确定，需产品/后端方提供。
2. `mandatory` 策略是否启用：本期保留字段但 UI 层不区分处理，是否需要后续扩展？
3. 更新提示是否需要 i18n（中/英）：`releaseNotes` 字段由服务端控制语言，UI 静态文案是否需要 `strings.xml` 双语？
