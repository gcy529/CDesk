# 更新日志

## 0.5.1 (beta)

修复（都在 beta 0.5.0 上真实存在，测试先行锁定后逐个修掉）：

- **音乐壳启动即崩**：`lvItems` 的父布局是 FrameLayout 却被强转 LinearLayout，点开音乐页必闪退
- **列表点不动**：`setItemsCanFocus(true)` 后行自己接了触摸/OK 键，ListView 的点击回调从不触发——看得见台、打不开；补上行级点击与长按收藏
- **Android 12 上服务崩**：网速前台服务的 `PendingIntent` 缺 `FLAG_IMMUTABLE`（targetSdk≥31 时强制）
- **Android 4.1–4.4 全部放不出视频**：`setVideoURI(uri, headers)` 是 API 21，低版本直接走异常被吞成一句"播放失败"；现在低版本退回无头重载
- **Android 4.1–4.3 历史记录静默失效**：`JSONArray.remove(int)` 是 API 19，列表一满就 NoSuchMethodError 被吞；改为边拷贝边限长
- **更新检查自指**：四语 `app_version` 全停在 0.4.1，APK 已是 0.5.1，更新页提示"升级到已装版本"；改为从 `versionName` 生成，只写一处
- **台名带逗号被截断**：M3U 解析用 `lastIndexOf(',')`，`新闻,高清` 会变成 `高清`；改为引号外的第一个逗号
- **切语言重置大字体**：API 17+ 分支用空 `Configuration` 包 context，覆盖掉用户的 `fontScale`；改为拷贝原配置
- **播放失败永远转圈**：失败分支不藏加载条；现在失败即收
- **应用页空提示错乱**：空状态显示的是影音页文案；新增 `page_apps_empty`，删掉描述旧实现（自动列出播放器应用）的 `page_media_empty`
- **列数两处硬编码**：影音页 3 列与应用页 6 列散在两处，改为 `MediaAdapter.columns()` 单一来源
- **页序对齐首页重设计 v2**：最近 / 应用 / 影音，默认落「最近」
- **中英文**：补 `settings_log` 三语缺失；CrashActivity、设置页 Toast、收藏/历史标签、播放器内核名、传输日志、剧集/源兜底名等 9 处 Java 硬编码中文全部进四语资源

测试：新增 42 个用例（Robolectric 全组件启动冒烟 + lint `NewApi` / `UnspecifiedImmutableFlag` 门禁），CI 上跑。

## 0.5.0 (beta 重建版)

- 从稳定版 0.2.4 重建：业务代码与 0.2.4 完全一致（修白屏）
- 仅保留基础设施升级：固定签名（可覆盖安装）、CI 发布流程、带版本号文件名
- 影音壳/推送/更新器等 beta 功能将在 0.2.4 基线验证稳定后逐批加回
