package com.c.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 性能体检 —— 倒逼厂商负优化的诊断工具。
 *
 * 厂商 ROM（澎湃、Flyme、ColorOS、HarmonyOS 等）常对第三方应用做隐性负优化：
 * 锁 CPU 频率、下线核心、压内存、降存储 I/O、拖启动、杀后台。这些行为
 * 普通用户看不见，厂商也不会承认。本工具实测各项硬件指标，与硬件基准对比，
 * 把"负优化"量化成健康分和证据，让用户看得见、厂商赖不掉。
 *
 * 检测项（全部使用原生 android.* / 系统文件，无第三方依赖）：
 *  1. CPU 频率上限 —— 读 cpufreq，scaling_max_freq 低于 cpu_max_freq 即被锁频
 *  2. CPU 可用核心 —— 统计 online 核心数，对比 availableProcessors
 *  3. 内存可用 —— 读 /proc/meminfo，可用内存过低说明厂商压内存
 *  4. 存储写入速度 —— 实测 1MB 写入，低于阈值即 I/O 被降速
 *  5. 启动耗时 —— 从 MainActivity 记录的冷启动时间，过慢即被拖启动
 *  6. 后台服务存活 —— SpeedService 是否还在，被杀即厂商杀后台
 *  7. 省电豁免 —— 电池优化状态，未豁免即被省电限制
 *
 * 兼容性：所有文件读取都包 try/catch，读不到就返回"未知"，不误报。
 */
public class PerfDiag {

    public static final int STATUS_OK = 0;
    public static final int STATUS_WARN = 1;
    public static final int STATUS_BAD = 2;
    public static final int STATUS_UNKNOWN = 3;

    /** 单条检测结果 */
    public static class Check {
        public String name;        // 检测项名称
        public int status;         // STATUS_OK / WARN / BAD / UNKNOWN
        public String detail;      // 实测值 + 对比基准
        public String suggestion;   // 建议（可为空）
    }

    /** 完整体检报告 */
    public static class Report {
        public int score;         // 健康分 0-100
        public List<Check> checks = new ArrayList<>();
    }

    private static final String PREF_STARTUP = "perf";
    private static final String KEY_STARTUP_MS = "startup_ms";

    /** 启动耗时记录入口：MainActivity onCreate 最开始调用一次 */
    public static void markStart(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_STARTUP, Context.MODE_PRIVATE);
        if (!sp.getBoolean(KEY_STARTUP_MS + "_done", false)) {
            sp.edit().putLong(KEY_STARTUP_MS, System.currentTimeMillis()).apply();
        }
    }

    /** 启动耗时记录出口：onResume 第一次进入时调用，算出冷启动耗时 */
    public static void recordStartup(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_STARTUP, Context.MODE_PRIVATE);
        long start = sp.getLong(KEY_STARTUP_MS, 0);
        if (start > 0) {
            sp.edit().putLong(KEY_STARTUP_MS + "_elapsed", System.currentTimeMillis() - start)
                .putBoolean(KEY_STARTUP_MS + "_done", true).apply();
        }
    }

    private static long getStartupElapsed(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_STARTUP, Context.MODE_PRIVATE);
        return sp.getLong(KEY_STARTUP_MS + "_elapsed", 0);
    }

    /** 执行完整体检，返回报告 */
    public static Report diagnose(Context ctx) {
        Report r = new Report();
        r.score = 100;

        // 1) CPU 频率上限
        Check cpuFreq = checkCpuFreq();
        r.checks.add(cpuFreq);
        r.score = scoreAdjust(r.score, cpuFreq);

        // 2) CPU 可用核心
        Check cpuCores = checkCpuCores();
        r.checks.add(cpuCores);
        r.score = scoreAdjust(r.score, cpuCores);

        // 3) 内存可用
        Check mem = checkMemory(ctx);
        r.checks.add(mem);
        r.score = scoreAdjust(r.score, mem);

        // 4) 存储写入速度
        Check storage = checkStorage(ctx);
        r.checks.add(storage);
        r.score = scoreAdjust(r.score, storage);

        // 5) 启动耗时
        Check startup = checkStartup(ctx);
        r.checks.add(startup);
        r.score = scoreAdjust(r.score, startup);

        // 6) 后台服务存活
        Check bg = checkBackground(ctx);
        r.checks.add(bg);
        r.score = scoreAdjust(r.score, bg);

        // 7) 省电豁免
        Check battery = checkBattery(ctx);
        r.checks.add(battery);
        r.score = scoreAdjust(r.score, battery);

        if (r.score < 0) r.score = 0;
        return r;
    }

    /** 按检查项严重程度扣分 */
    private static int scoreAdjust(int score, Check c) {
        if (c.status == STATUS_BAD) return score - 15;
        if (c.status == STATUS_WARN) return score - 8;
        return score;
    }

    // ---------- 各项检测 ----------

    /** 1) CPU 频率：scaling_max_freq 低于 cpu_max_freq 说明被锁频 */
    private static Check checkCpuFreq() {
        Check c = new Check();
        c.name = "CPU 频率";
        try {
            String maxPath = "/sys/devices/system/cpu/cpu0/cpufreq/cpu_max_freq";
            String scalingPath = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq";
            long max = readLong(maxPath);
            long scaling = readLong(scalingPath);
            if (max <= 0 || scaling <= 0) {
                c.status = STATUS_UNKNOWN;
                c.detail = "当前系统未开放 cpufreq 文件";
                return c;
            }
            double ratio = (double) scaling / max;
            c.detail = String.format(Locale.US, "上限 %d MHz / 硬件 %d MHz（%d%%）",
                    scaling, max, Math.round(ratio * 100));
            if (ratio < 0.7) {
                c.status = STATUS_BAD;
                c.suggestion = "CPU 上限被锁至硬件的 " + Math.round(ratio * 100)
                        + "%，可尝试关闭厂商的「性能模式限制」或省电策略";
            } else if (ratio < 0.9) {
                c.status = STATUS_WARN;
                c.suggestion = "CPU 上限低于硬件峰值，检查是否启用了省电模式";
            } else {
                c.status = STATUS_OK;
            }
        } catch (Throwable t) {
            c.status = STATUS_UNKNOWN;
            c.detail = "无法读取 cpufreq";
        }
        return c;
    }

    /** 2) CPU 可用核心：online 核心数 vs availableProcessors */
    private static Check checkCpuCores() {
        Check c = new Check();
        c.name = "CPU 核心";
        int avail = Runtime.getRuntime().availableProcessors();
        c.detail = "可用核心 " + avail + " / 逻辑核心 " + avail;
        // 通过 /sys/devices/system/cpu/cpu*/online 统计物理在线核心
        try {
            int online = 0;
            for (int i = 0; i < avail; i++) {
                String p = "/sys/devices/system/cpu/cpu" + i + "/online";
                String v = readFile(p);
                if ("1".equals(v.trim())) online++;
            }
            if (online > 0) {
                c.detail = "在线物理核心 " + online + " / 逻辑核心 " + avail;
                if (online < avail / 2 && avail > 2) {
                    c.status = STATUS_WARN;
                    c.suggestion = "部分 CPU 核心被下线，检查厂商的省电核心策略";
                } else {
                    c.status = STATUS_OK;
                }
            } else {
                c.status = STATUS_UNKNOWN;
                c.detail = "无法读取核心 online 状态";
            }
        } catch (Throwable t) {
            c.status = STATUS_UNKNOWN;
            c.detail = "无法读取核心状态";
        }
        return c;
    }

    /** 3) 内存：读 /proc/meminfo 的 MemAvailable */
    private static Check checkMemory(Context ctx) {
        Check c = new Check();
        c.name = "内存可用";
        try {
            String meminfo = readFile("/proc/meminfo");
            long memTotal = 0, memAvail = 0;
            for (String line : meminfo.split("\n")) {
                if (line.startsWith("MemTotal:")) memTotal = parseKb(line);
                else if (line.startsWith("MemAvailable:")) memAvail = parseKb(line);
            }
            if (memTotal > 0 && memAvail > 0) {
                double ratio = (double) memAvail / memTotal;
                c.detail = String.format(Locale.US, "可用 %d MB / 总计 %d MB（%d%%）",
                        memAvail / 1024, memTotal / 1024, Math.round(ratio * 100));
                if (ratio < 0.15) {
                    c.status = STATUS_BAD;
                    c.suggestion = "可用内存极低，厂商可能在压内存或清理后台，建议关闭其他后台应用";
                } else if (ratio < 0.25) {
                    c.status = STATUS_WARN;
                    c.suggestion = "可用内存偏低，建议清理后台释放内存";
                } else {
                    c.status = STATUS_OK;
                }
            } else {
                c.status = STATUS_UNKNOWN;
                c.detail = "无法读取内存信息";
            }
        } catch (Throwable t) {
            c.status = STATUS_UNKNOWN;
            c.detail = "无法读取内存信息";
        }
        return c;
    }

    /** 4) 存储写入速度：实测 1MB 写入 */
    private static Check checkStorage(Context ctx) {
        Check c = new Check();
        c.name = "存储速度";
        File test = new File(ctx.getCacheDir(), "perfdiag_bench.bin");
        try {
            long t0 = System.nanoTime();
            FileOutputStream fos = new FileOutputStream(test);
            byte[] buf = new byte[65536];
            for (int i = 0; i < buf.length; i++) buf[i] = (byte) (i & 0xFF);
            int total = 1024 * 1024; // 1MB
            for (int w = 0; w < total; w += buf.length) {
                fos.write(buf, 0, Math.min(buf.length, total - w));
            }
            fos.flush();
            fos.close();
            long elapsed = System.nanoTime() - t0;
            double mbPerSec = (1.0 / (elapsed / 1_000_000_000.0));
            c.detail = String.format(Locale.US, "写入 %.1f MB/s", mbPerSec);
            if (mbPerSec < 10) {
                c.status = STATUS_BAD;
                c.suggestion = "存储写入极慢，厂商可能对 I/O 降速，检查是否启用了省电或后台限制";
            } else if (mbPerSec < 30) {
                c.status = STATUS_WARN;
                c.suggestion = "存储写入偏慢，检查存储模式或后台限制";
            } else {
                c.status = STATUS_OK;
            }
        } catch (Throwable t) {
            c.status = STATUS_UNKNOWN;
            c.detail = "无法完成存储测速";
        } finally {
            try { test.delete(); } catch (Throwable ignored) { }
        }
        return c;
    }

    /** 5) 启动耗时：从 SharedPreferences 读取 */
    private static Check checkStartup(Context ctx) {
        Check c = new Check();
        c.name = "启动耗时";
        long ms = getStartupElapsed(ctx);
        if (ms <= 0) {
            c.status = STATUS_UNKNOWN;
            c.detail = "暂无启动记录（首次启动后自动记录）";
            return c;
        }
        c.detail = String.format(Locale.US, "冷启动 %d ms", ms);
        if (ms > 2000) {
            c.status = STATUS_BAD;
            c.suggestion = "冷启动过慢，厂商可能在拖启动或限制后台初始化";
        } else if (ms > 1000) {
            c.status = STATUS_WARN;
            c.suggestion = "启动偏慢，检查是否有后台限制";
        } else {
            c.status = STATUS_OK;
        }
        return c;
    }

    /** 6) 后台服务存活：检查 SpeedService 是否在运行 */
    private static Check checkBackground(Context ctx) {
        Check c = new Check();
        c.name = "后台存活";
        try {
            android.app.ActivityManager am = (android.app.ActivityManager)
                    ctx.getSystemService(Context.ACTIVITY_SERVICE);
            boolean alive = false;
            if (am != null) {
                for (android.app.ActivityManager.RunningServiceInfo si : am.getRunningServices(64)) {
                    if (SpeedService.class.getName().equals(si.service.getClassName())) {
                        alive = true;
                        break;
                    }
                }
            }
            if (alive) {
                c.status = STATUS_OK;
                c.detail = "网速悬浮窗服务运行中";
            } else {
                c.status = STATUS_BAD;
                c.detail = "网速悬浮窗服务未运行，可能被厂商杀后台";
                c.suggestion = "前往守护页开启电池优化豁免，或将应用加入后台白名单";
            }
        } catch (Throwable t) {
            c.status = STATUS_UNKNOWN;
            c.detail = "无法读取服务状态";
        }
        return c;
    }

    /** 7) 省电豁免：复用 GuardianActivity 的反射逻辑 */
    private static Check checkBattery(Context ctx) {
        Check c = new Check();
        c.name = "省电豁免";
        try {
            String pn = ctx.getPackageName();
            Object pm = ctx.getSystemService(Context.POWER_SERVICE);
            boolean ignored = (Boolean) pm.getClass()
                    .getMethod("isIgnoringBatteryOptimizations", String.class)
                    .invoke(pm, pn);
            if (ignored) {
                c.status = STATUS_OK;
                c.detail = "已豁免电池优化";
            } else {
                c.status = STATUS_BAD;
                c.detail = "未豁免电池优化，省电策略会限制后台";
                c.suggestion = "前往守护页申请电池优化豁免";
            }
        } catch (Throwable t) {
            c.status = STATUS_UNKNOWN;
            c.detail = "无法查询电池优化状态";
        }
        return c;
    }

    // ---------- 工具方法 ----------

    private static String readFile(String path) {
        try {
            RandomAccessFile raf = new RandomAccessFile(path, "r");
            byte[] buf = new byte[4096];
            int n = raf.read(buf);
            raf.close();
            return new String(buf, 0, n > 0 ? n : 0).trim();
        } catch (Throwable t) {
            return "";
        }
    }

    private static long readLong(String path) {
        try {
            return Long.parseLong(readFile(path));
        } catch (Throwable t) {
            return 0;
        }
    }

    private static long parseKb(String line) {
        String[] parts = line.split(":");
        if (parts.length < 2) return 0;
        String[] nums = parts[1].trim().split("\\s+");
        try {
            return Long.parseLong(nums[0]);
        } catch (Throwable t) {
            return 0;
        }
    }
}
