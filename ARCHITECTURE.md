# 架构约束（Architecture Constraints）

> 这份文档存在的唯一目的：**防止有人（包括未来的我）不小心引入一个依赖，
> 就把"一个 APK 通吃 Android 4.0 ~ 15"这件事毁掉。**

---

## 唯一目标

```
一个 APK
minSdk 16  →  Android 4.1
targetSdk 34 →  Android 15
零 native 库（无 .so，不分 ABI）
第三方依赖最小化：ExoPlayer 2.18.1 + 纯 Java 工具库
```

参考产品 Aerial 分了 3 个包（standard / guard / lite），
其中 standard 与 lite 的差异**纯粹因为他们用了 ExoPlayer 和 AndroidX**，
我们锁 ExoPlayer 2.18.1 兼容 minSdk 16，一个 APK 通吃全版本。

---

## 五条铁律

违反任何一条，就会重新掉进"必须分版本"的坑。

### 1. 不引入 AndroidX / AppCompat / Material

```
✗ implementation 'androidx.appcompat:appcompat:...'
✓ android.app.Activity
```

AndroidX 组件大量要求 minSdk 21，且会让 APK 从 3 MB 涨到 8 MB 以上。
我们的界面全部用原生 View 手工搭。

### 2. ExoPlayer 锁 2.18.1（最后一版支持 minSdk 16），不升 media3

```
✓ com.google.android.exoplayer:exoplayer-*:2.18.1   // 声明 minSdk 16，一个 APK 通吃
✗ com.google.android.media3:exoplayer-*              // media3 起要求 API 21，老车机用不了
```

ExoPlayer 2.18.1 是最后一版声明支持 minSdk 16 的版本。media3 起要求 API 21，
一旦升级就得把 minSdk 提到 21、放弃 Android 4.x 老车机。所以锁死 2.18.1，
不升 media3，这是"一个 APK 通吃 Android 4.1 ~ 15"的关键。

**引入前必查**：确认 ExoPlayer 版本声明 minSdk ≤ 16，且不携带 .so 文件。

### 3. 不引入任何带 .so 的库

```
✗ .so 文件 = 必须同时提供 arm64-v8a 和 armeabi-v7a
✓ 纯 Java = 不分 ABI，一个包通吃 32/64 位
```

Android 9 起，只要 APK 里有 native 库，64 位设备就强制要求 64 位版本。
**没有 .so 就没有这个问题**，这是"一步到位"最关键的一条。

引入前先检查：解压 AAR/JAR 看有没有 `lib/` 目录。

### 4. 不调用 API 21+ 的 API 而不做版本分支

```
✗ ValueAnimator.ofArgb(...)              // API 21，4.x 上 NoSuchMethodError
✓ ValueAnimator.ofFloat + ArgbEvaluator  // API 1

✗ WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY   // API 26
✓ SDK_INT >= 26 ? TYPE_APPLICATION_OVERLAY : TYPE_PHONE
```

已知必须分支的清单：

| API | 替代 / 分支 |
|---|---|
| `ValueAnimator.ofArgb` | `ofFloat` + `ArgbEvaluator` |
| `TYPE_APPLICATION_OVERLAY` | `TYPE_PHONE` |
| `startForegroundService` | `startService` |
| `NotificationChannel` | 不创建 |
| `Settings.canDrawOverlays` | 默认视为已授权 |
| `Theme.Material.*` | `Theme.NoTitleBar.Fullscreen`（values/ 与 values-v21/ 分包） |
| `Notification.Builder(ctx, channel)` | `Notification.Builder(ctx)` |
| `getExternalFilesDir` | 已 API 8，安全 |

### 5. 不用 Kotlin

```
✗ Kotlin stdlib ≈ +1.5 MB，且协程/扩展函数容易带进高版本 API
✓ 纯 Java
```

---

## 允许的依赖

**最小化。** 只保留纯 Java、无 native、不引发 ABI 拆分的第三方库：

| 依赖 | 用途 | 约束 |
|---|---|---|
| `com.google.android.exoplayer:exoplayer-*:2.18.1` | 播放器（minSdk 16 兼容） | 纯 Java，无 .so，锁版本不升 media3 |
| `com.google.zxing:core:3.5.3` | 局域网传输二维码 | 纯 Java，R8 裁剪后约 30KB |
| `org.mozilla:rhino:1.7.14` | 音乐源 .js 脚本 | 纯 Java，无 native |

其余能力全部用 Android Framework 自带 API 实现：

| 需求 | 方案 |
|---|---|
| 实时网速 | `TrafficStats` |
| 悬浮窗 | `WindowManager` + 版本分支 |
| 壁纸采样 | `WallpaperManager` + `Bitmap` |
| 局域网传输 | `ServerSocket`（手写 HTTP） |
| 应用列表 | `PackageManager.queryIntentActivities` |
| 颜色动画 | `ValueAnimator` + `ArgbEvaluator` |
| 持久化 | `SharedPreferences` |

---

## 关于 guard 变体

某些固件在**安装阶段**扫描 manifest，看到 `CATEGORY_HOME` 就拒绝安装
（报"不允许安装桌面类应用"）。这不是运行时能绕过的。

处理方式：

- 代码库只有一份
- CI 产出两个 manifest 变体：`standard`（声明 HOME）与 `guard`（不声明）
- 官网只给**一个下载按钮**，安装脚本自动判断该用哪个
- **用户视角仍然是"一步到位"**

车机很少锁桌面，第一版可以只出 standard。

---

## 体积预算

| 项 | 目标 |
|---|---|
| APK 总量 | 5–10 MB |
| 代码（纯 Java，无依赖） | < 200 KB |
| 文案（中英各一套） | < 20 KB |
| 图标（矢量 layer-list） | < 10 KB |
| 内置二维码 | ~70 KB |

如果哪天超过 10 MB，**先查是不是偷偷引入了依赖**，而不是想着上混淆压缩。

---

## 提交前自检

- [ ] 新依赖是纯 Java、无 `.so` 吗？（`unzip -l app-release.apk | grep '\.so'`）
- [ ] 新调用的 API 有没有版本分支？
- [ ] `minSdkVersion` 还是 16 吗？
- [ ] APK 装到 Android 4.4 真机上还能起来吗？

## 依赖说明

**ExoPlayer**：`com.google.android.exoplayer:exoplayer-*:2.18.1`。
锁 2.18.1（最后一版支持 minSdk 16），纯 Java 无 native，不引发 ABI 拆分。详见铁律 2。

**ZXing**：`com.google.zxing:core:3.5.3`（Apache 2.0）。
用途仅一个：局域网传输页的地址二维码。纯 Java、无 native，R8 裁剪后增量约 30KB。

**Rhino**：`org.mozilla:rhino:1.7.14`。
用途：跑音乐源 .js 脚本（花音源等 lx-music 格式）。纯 Java、无 native。
