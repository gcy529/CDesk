package com.c.launcher;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 播放列表解析是影音壳的唯一入口链路：解析错了，后面全是空的。
 * 用 Robolectric 跑是因为 org.json 在纯 JVM 单测里是 android.jar 的桩实现，会抛 Stub!。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PlaylistParserTest {

    // ---------- M3U ----------

    @Test
    public void m3u_extractsNameGroupAndUrl() {
        String src = "#EXTM3U\n"
                + "#EXTINF:-1 tvg-name=\"CCTV1\" group-title=\"央视\",CCTV-1 综合\n"
                + "http://example.com/1.m3u8\n";
        List<PlaylistParser.Item> items = PlaylistParser.parse(src);
        assertEquals(1, items.size());
        assertEquals("CCTV-1 综合", items.get(0).name);
        assertEquals("央视", items.get(0).group);
        assertEquals("http://example.com/1.m3u8", items.get(0).url);
    }

    /**
     * M3U 的标题是「属性之后第一个不在引号内的逗号」之后的全部内容。
     * 原实现用 lastIndexOf(',')，台名里带逗号就被截断。
     */
    @Test
    public void m3u_keepsCommaInsideChannelName() {
        String src = "#EXTINF:-1 group-title=\"央视\",新闻,高清\n"
                + "http://example.com/1.m3u8\n";
        List<PlaylistParser.Item> items = PlaylistParser.parse(src);
        assertEquals(1, items.size());
        assertEquals("新闻,高清", items.get(0).name);
    }

    @Test
    public void m3u_commaInsideQuotedAttributeDoesNotSplitTitle() {
        String src = "#EXTINF:-1 group-title=\"央视,卫视\" tvg-name=\"X\",综合频道\n"
                + "http://example.com/1.m3u8\n";
        List<PlaylistParser.Item> items = PlaylistParser.parse(src);
        assertEquals(1, items.size());
        assertEquals("综合频道", items.get(0).name);
        assertEquals("央视,卫视", items.get(0).group);
    }

    @Test
    public void m3u_bareUrlWithoutExtinfStillListed() {
        List<PlaylistParser.Item> items =
                PlaylistParser.parse("#EXTM3U\nhttp://example.com/a.m3u8\n");
        assertEquals(1, items.size());
        assertEquals("http://example.com/a.m3u8", items.get(0).url);
    }

    // ---------- TXT #genre# ----------

    @Test
    public void genreFormat_assignsGroups() {
        String src = "央视,#genre#\n"
                + "CCTV1,http://example.com/1\n"
                + "CCTV2,http://example.com/2\n"
                + "卫视,#genre#\n"
                + "湖南,http://example.com/3\n";
        List<PlaylistParser.Item> items = PlaylistParser.parse(src);
        assertEquals(3, items.size());
        assertEquals("央视", items.get(0).group);
        assertEquals("央视", items.get(1).group);
        assertEquals("卫视", items.get(2).group);
        assertEquals("湖南", items.get(2).name);
    }

    // ---------- JSON ----------

    @Test
    public void json_bareArray() {
        String src = "[{\"name\":\"A\",\"url\":\"http://example.com/1\",\"group\":\"G\"}]";
        List<PlaylistParser.Item> items = PlaylistParser.parse(src);
        assertEquals(1, items.size());
        assertEquals("A", items.get(0).name);
        assertEquals("G", items.get(0).group);
    }

    @Test
    public void json_objectWithChannelsKey() {
        String src = "{\"channels\":[{\"title\":\"B\",\"src\":\"http://example.com/2\"}]}";
        List<PlaylistParser.Item> items = PlaylistParser.parse(src);
        assertEquals(1, items.size());
        assertEquals("B", items.get(0).name);
        assertEquals("http://example.com/2", items.get(0).url);
    }

    @Test
    public void json_malformedDoesNotThrow() {
        assertTrue(PlaylistParser.parse("{不是合法 JSON").isEmpty());
        assertTrue(PlaylistParser.parse("[{\"name\":").isEmpty());
    }

    // ---------- 名称,地址 兜底 ----------

    @Test
    public void pairsFormat() {
        List<PlaylistParser.Item> items =
                PlaylistParser.parse("电影A,http://example.com/a.mp4\n电影B,http://example.com/b.mp4\n");
        assertEquals(2, items.size());
        assertEquals("电影A", items.get(0).name);
    }

    @Test
    public void bareUrlLine() {
        List<PlaylistParser.Item> items =
                PlaylistParser.parse("http://example.com/stream.m3u8\n");
        assertEquals(1, items.size());
        assertEquals("stream", items.get(0).name);
    }

    // ---------- 边界 ----------

    @Test
    public void nullAndEmptyAreSafe() {
        assertTrue(PlaylistParser.parse(null).isEmpty());
        assertTrue(PlaylistParser.parse("").isEmpty());
        assertTrue(PlaylistParser.parse("   \n  \n").isEmpty());
    }

    @Test
    public void stripsUtf8Bom() {
        List<PlaylistParser.Item> items =
                PlaylistParser.parse("\uFEFF电影A,http://example.com/a.mp4\n");
        assertEquals(1, items.size());
        assertEquals("电影A", items.get(0).name);
    }

    @Test
    public void handlesWindowsLineEndings() {
        List<PlaylistParser.Item> items =
                PlaylistParser.parse("央视,#genre#\r\nCCTV1,http://example.com/1\r\n");
        assertEquals(1, items.size());
        assertEquals("CCTV1", items.get(0).name);
    }

    // ---------- 音乐 JSON ----------

    @Test
    public void musicJson_appendsArtist() {
        String src = "{\"list\":[{\"name\":\"歌名\",\"artist\":\"歌手\",\"url\":\"http://example.com/a.mp3\"}]}";
        List<PlaylistParser.Item> items = PlaylistParser.parseMusicJson(src);
        assertEquals(1, items.size());
        assertEquals("歌名 - 歌手", items.get(0).name);
    }

    @Test
    public void musicJson_skipsEntriesWithoutUrl() {
        String src = "[{\"name\":\"无地址\"},{\"name\":\"有地址\",\"url\":\"http://example.com/a.mp3\"}]";
        List<PlaylistParser.Item> items = PlaylistParser.parseMusicJson(src);
        assertEquals(1, items.size());
    }

    @Test
    public void musicJson_nullSafe() {
        assertTrue(PlaylistParser.parseMusicJson(null).isEmpty());
        assertTrue(PlaylistParser.parseMusicJson("").isEmpty());
    }
}