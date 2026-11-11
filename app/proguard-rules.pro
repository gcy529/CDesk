# 入口类不能被混淆：Manifest 里按名字反射加载
-keep class com.c.launcher.MainActivity { *; }
-keep class com.c.launcher.TransferActivity { *; }
-keep class com.c.launcher.SpeedService { *; }
-keep class com.c.launcher.SpeedOverlay { *; }
-keep class com.c.launcher.AutoContrast { *; }
-keep class com.c.launcher.ThemeManager { *; }
-keep class com.c.launcher.AppAdapter { *; }
-keep class com.c.launcher.JsSource { *; }

# Rhino JS 引擎：反射调用 NativeFunction / Context，不能混淆方法名
-keep class org.mozilla.javascript.** { *; }
