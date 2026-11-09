package com.c.launcher;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 四语文案一致性。纯文件解析，不起 Robolectric，跑得快。
 *
 * 查三件事：
 *  1. key 齐全——缺一个 key 就会静默回落到默认（中文），英文用户看到中文按钮；
 *  2. 占位符一致——%1$s 数量/序号错位会直接导致运行时崩溃或串词；
 *  3. EN 里不混中文——专名（公众号名）走白名单。
 */
public class StringResourceParityTest {

    /** 专有名词：微信公众号名本身就是中文，英文译名只是括号补充，允许保留 */
    private static final List<String> EN_CJK_ALLOWLIST = new ArrayList<String>();
    static {
        EN_CJK_ALLOWLIST.add("wechat_name");
    }

    private static final String[] LOCALES = { "values-en", "values-zh-rTW", "values-zh-rHK" };
    private static final Pattern FORMAT = Pattern.compile("%(?:\\d+\\$)?[a-zA-Z]");
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    @Test
    public void everyLocaleHasEveryDefaultKey() throws Exception {
        Map<String, String> base = read("values");
        assertTrue("默认 strings.xml 不应为空", base.size() > 0);
        List<String> problems = new ArrayList<String>();
        for (int i = 0; i < LOCALES.length; i++) {
            Map<String, String> loc = read(LOCALES[i]);
            for (String key : base.keySet()) {
                if (!loc.containsKey(key)) problems.add(LOCALES[i] + " 缺 " + key);
            }
            for (String key : loc.keySet()) {
                if (!base.containsKey(key)) problems.add(LOCALES[i] + " 多出默认没有的 " + key);
            }
        }
        report(problems, "文案 key 不齐");
    }

    @Test
    public void formatPlaceholdersMatchAcrossLocales() throws Exception {
        Map<String, String> base = read("values");
        List<String> problems = new ArrayList<String>();
        for (int i = 0; i < LOCALES.length; i++) {
            Map<String, String> loc = read(LOCALES[i]);
            for (Map.Entry<String, String> e : base.entrySet()) {
                String translated = loc.get(e.getKey());
                if (translated == null) continue;   // 缺 key 由上一个用例报
                List<String> want = placeholders(e.getValue());
                List<String> got = placeholders(translated);
                if (!want.equals(got)) {
                    problems.add(LOCALES[i] + " " + e.getKey() + " 占位符 " + got + " ≠ 默认 " + want);
                }
            }
        }
        report(problems, "占位符错位");
    }

    @Test
    public void englishStringsContainNoChinese() throws Exception {
        Map<String, String> en = read("values-en");
        List<String> problems = new ArrayList<String>();
        for (Map.Entry<String, String> e : en.entrySet()) {
            if (EN_CJK_ALLOWLIST.contains(e.getKey())) continue;
            if (CJK.matcher(e.getValue()).find()) {
                problems.add("values-en " + e.getKey() + " = " + e.getValue());
            }
        }
        report(problems, "英文文案里混了中文");
    }

    private static List<String> placeholders(String s) {
        List<String> out = new ArrayList<String>();
        Matcher m = FORMAT.matcher(s);
        while (m.find()) out.add(m.group());
        // 用排序后的多重集比较：%1$s 与 %s 混用时顺序不同不算错，数量不同才算
        java.util.Collections.sort(out);
        return out;
    }

    private static void report(List<String> problems, String title) {
        if (problems.isEmpty()) return;
        StringBuilder sb = new StringBuilder(title).append("（").append(problems.size()).append(" 处）:\n");
        for (int i = 0; i < problems.size(); i++) sb.append("  - ").append(problems.get(i)).append('\n');
        fail(sb.toString());
    }

    /** Gradle 单测的工作目录通常是模块目录，但从根目录跑也要能找到 */
    private static Map<String, String> read(String dir) throws Exception {
        File f = new File("src/main/res/" + dir + "/strings.xml");
        if (!f.isFile()) f = new File("app/src/main/res/" + dir + "/strings.xml");
        assertTrue("找不到 " + dir + "/strings.xml，查找路径: " + f.getAbsolutePath(), f.isFile());
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);
        NodeList nodes = doc.getElementsByTagName("string");
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            out.put(el.getAttribute("name"), el.getTextContent());
        }
        return out;
    }
}