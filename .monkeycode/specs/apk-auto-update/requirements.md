# APK 自动更新提醒 - 需求文档

## 功能概述

在应用启动时检测远程服务器是否存在更高版本的 APK，若存在则向用户展示更新提醒，引导用户完成版本升级。

## 背景

- 当前应用以单 APK 形态分发（无 Play Store 托管），用户更新完全依赖手动重新安装。
- 老版本用户缺乏升级入口，造成版本碎片化。
- 需要轻量、低侵入的更新检测与提醒机制，适配 Android 4.0+ 老设备。

## 目标

- 应用启动时自动检查远程最新版本，若有更新则展示更新提醒 UI。
- 提供"立即更新"下载引导与"稍后提醒"取消两种用户操作路径。
- 检测与下载全程兼容 Android 4.0，不引入 AndroidX、原生库或 ExoPlayer。

## 范围

### 包含

- 远程版本检测（基于 HTTPS JSON 端点）
- 更新提醒 UI（Dialog 或全屏 Activity）
- 下载触发（通过 `ACTION_VIEW` 交给系统浏览器）
- 用户取消与"不再提醒"持久化（SharedPreferences）
- 更新失败重试提示

### 不包含

- 自动下载与静默安装（需系统权限，超出单 APK 边界）
- 增量差分更新（binaary diff）
- 更新包校验与签名验证（由下载页承担）
- Play Store 更新通道

## 用户故事

- 作为老版本用户，我希望打开 App 时看到新版本提醒，以便及时升级获取新功能。
- 作为老版本用户，我希望可以关闭提醒并稍后手动处理，以便不打断当前使用流程。
- 作为用户，我希望点击"立即更新"能跳转到下载页，以便完成 APK 安装。

## EARS 需求

- **EARS-001 (Event-Driven)**: 应用启动完成并进入主界面后，若检测到远程版本高于当前版本，系统 SHALL 在 5 秒内展示更新提醒 UI。
- **EARS-002 (Event-Driven)**: 用户在更新提醒 UI 点击"立即更新"后，系统 SHALL 通过 `ACTION_VIEW` 打开 APK 下载链接，并将用户导向下载页。
- **EARS-003 (Event-Driven)**: 用户在更新提醒 UI 点击"稍后提醒"后，系统 SHALL 关闭提醒 UI 并将"已取消版本"写入 SharedPreferences，该版本后续启动不再重复提醒。
- **EARS-004 (Unwanted-Behavior)**: 若版本检测网络请求超时（默认 10 秒）或失败，系统 SHALL 静默跳过本次检测，不展示错误提示，不打断应用正常使用。
- **EARS-005 (State-Driven)**: 当 SharedPreferences 中存储的"已取消版本"等于当前远程版本时，系统 SHALL 跳过更新提醒 UI 的展示。
- **EARS-006 (Event-Driven)**: 应用冷启动时，系统 SHALL 在 3 秒内发起远程版本检测请求，并缓存结果于内存，避免同一启动周期内重复请求。
- **EARS-007 (Unwanted-Behavior)**: 若用户设备未安装浏览器或 `ACTION_VIEW` 解析失败，系统 SHALL 展示 Toast 提示"无法打开浏览器，请手动访问更新链接"并复制链接到剪贴板。
- **EARS-008 (Ubiquitous)**: 版本检测与更新提醒功能 SHALL 在 Android 4.0 至 Android 15 全版本范围内可用，不依赖任何 AndroidX、原生库或 ExoPlayer 组件。
- **EARS-009 (State-Driven)**: 当远程端点返回 404 或 JSON 解析失败时，系统 SHALL 视为"无更新可用"，不展示提醒 UI。
- **EARS-010 (Event-Driven)**: 用户在"已取消版本"被新远程版本覆盖时（远程版本 > 已取消版本），系统 SHALL 重新展示更新提醒 UI。

## INCOSE 质量属性

- **性能**: 版本检测请求 SHALL 在冷启动后 3 秒内发起，整体检测链路（含网络往返）SHALL 在 15 秒内完成或超时降级。
- **可用性**: 检测失败时应用 SHALL 保持完全可用，更新提醒缺失不阻塞主功能。
- **安全性**: 版本检测 SHALL 仅通过 HTTPS 端点，下载链接 SHALL 为 HTTPS，避免中间人篡改 APK。
- **可维护性**: 版本端点 URL SHALL 通过 BuildConfig 或资源文件集中管理，便于更换 CDN。
- **兼容性**: 功能 SHALL 兼容 Android 4.0+，不引入高版本 API 或新依赖。

## 验收标准

- [ ] 启动应用后，若远程存在更高版本且未被取消，5 秒内展示更新提醒 UI。
- [ ] 点击"立即更新"，系统调起浏览器并打开下载页。
- [ ] 点击"稍后提醒"，关闭 UI，后续启动不再展示该版本的提醒。
- [ ] 断网环境下启动，应用正常进入主界面，无任何错误提示。
- [ ] 在 Android 4.0 模拟器上功能完整可用，无崩溃。
- [ ] 在 Android 15 模拟器上功能完整可用，无崩溃。
- [ ] 更新提醒 UI 符合应用整体视觉风格，含更新说明文案与版本号对比。

## 假设与依赖

- 远程版本端点为 HTTPS JSON API，返回结构固定：`{ "versionCode": int, "versionName": string, "downloadUrl": string, "releaseNotes": string }`
- 应用已有 HTTPS 通信能力（OkHttp 或 HttpURLConnection，遵循 ARCHITECTURE.md 依赖约束）。
- 下载页由团队维护，承担 APK 下载与安装引导。

## 术语表

| 术语 | 说明 |
|------|------|
| 版本端点 | 返回当前最新版本信息的 HTTPS JSON API |
| 当前版本 | 应用 APK 内 `versionCode` 值，通过 `PackageManager` 获取 |
| 远程版本 | 版本端点返回的 `versionCode` 值 |
| 已取消版本 | 用户选择"稍后提醒"后持久化的 `versionCode`，存储于 SharedPreferences |
| 冷启动 | 进程被系统杀死后重新创建，`Application.onCreate` 执行 |

## 参考资料

- `/workspace/ARCHITECTURE.md` - 架构约束与铁律
- `/workspace/CHANGELOG.md` - 历史版本记录

## 开放问题

- 更新提醒 UI 采用 Dialog 还是全屏 Activity？需在技术设计中确定。
- "已取消版本"是否需要提供"重新开启提醒"入口？当前范围外，后续迭代评估。
- 是否对下载链接做 SHA256 校验提示？当前由下载页承担，本功能不涉及。
