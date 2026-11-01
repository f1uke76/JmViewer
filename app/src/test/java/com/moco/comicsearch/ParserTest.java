package com.moco.comicsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 用抓下来的真实页面验证解析规则。 */
public class ParserTest {

    private static final String BASE = "https://comic18j-moco.work";

    private String load(String name) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull("缺少测试样本 " + name, in);
            byte[] buf = new byte[in.available()];
            int read = 0;
            while (read < buf.length) {
                int n = in.read(buf, read, buf.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return new String(buf, 0, read, StandardCharsets.UTF_8);
        }
    }

    @Test
    public void search_page_yields_albums() throws Exception {
        List<Models.Album> list = Parsers.parseSearch(load("search.html"), BASE);
        assertTrue("搜索结果数量异常: " + list.size(), list.size() > 10);

        Models.Album first = list.get(0);
        assertEquals("1480638", first.id);
        assertTrue(first.title.contains("TURING TEST"));
        assertTrue(first.cover.startsWith("http"));
        assertFalse(first.author.isEmpty());
        assertFalse(first.tags.isEmpty());
        assertFalse(first.likes.isEmpty());

        for (Models.Album a : list) {
            assertFalse("id 为空", a.id.isEmpty());
            assertFalse("标题为空: " + a.id, a.title.isEmpty());
        }
    }

    @Test
    public void album_page_yields_detail() throws Exception {
        Models.Album a = Parsers.parseAlbum(load("album.html"), BASE);
        assertTrue(a.title.contains("TURING TEST"));
        assertEquals("22", a.pages);
        assertEquals("1480638", a.readId);
        assertTrue(a.cover.contains("1480638"));
        assertFalse(a.author.isEmpty());
        assertFalse(a.tags.isEmpty());
        assertFalse(a.chapters.isEmpty());
        assertEquals("1480638", a.chapters.get(0).id);
    }

    @Test
    public void chapter_page_yields_pages_and_scramble_id() throws Exception {
        Models.ChapterContent c = Parsers.parseChapter(load("photo.html"), BASE);
        assertEquals("1480638", c.aid);
        assertEquals(220980, c.scrambleId);
        assertTrue("页数异常: " + c.pages.size(), c.pages.size() >= 10);

        Models.PageImage p0 = c.pages.get(0);
        assertTrue(p0.url.contains("/media/photos/1480638/"));
        assertEquals("00001.webp", p0.name);
    }

    @Test
    public void numeric_query_lands_on_album_page() throws Exception {
        String html = load("album_by_id.html");
        assertTrue("应识别为详情页", Parsers.isAlbumPage(html));
        assertEquals("1464578", Parsers.albumIdFrom(html));

        Models.Album a = Parsers.parseAlbum(html, BASE);
        assertFalse("标题为空", a.title.isEmpty());
        assertEquals("1464578", a.readId);
        assertFalse(a.chapters.isEmpty());
    }

    @Test
    public void search_page_is_not_album_page() throws Exception {
        assertFalse(Parsers.isAlbumPage(load("search.html")));
    }

    @Test
    public void official_page_yields_mirrors() throws Exception {
        List<Models.Mirror> mirrors = Parsers.parseMirrors(load("official.html"));
        assertEquals("分流数量异常: " + mirrors.size(), 7, mirrors.size());

        boolean hasCurrent = false;
        for (Models.Mirror m : mirrors) {
            assertFalse("域名为空", m.host.isEmpty());
            assertFalse("应排除带路径的链接: " + m.host, m.host.contains("/"));
            assertFalse("应排除邮箱: " + m.host, m.host.contains("gmail") || m.host.contains("＠"));
            assertFalse("应排除社交链接: " + m.host, m.host.contains("discord") || m.host.contains("t.me"));
            if ("comic18j-moco.work".equals(m.host)) {
                hasCurrent = true;
                assertEquals("內地網域", m.label);
            }
        }
        assertTrue("应包含当前站点域名", hasCurrent);
        assertEquals("18comic.vip", mirrors.get(0).host);
        assertEquals("comic18j-nico.site", mirrors.get(5).host);
        assertEquals("comic18j-ada.work", mirrors.get(6).host);
    }

    @Test
    public void homepage_yields_recommendations() throws Exception {
        List<Models.Album> list = Parsers.parseSearch(load("home.html"), BASE);
        assertTrue("首页推荐数量异常: " + list.size(), list.size() > 10);
        for (Models.Album a : list) {
            assertFalse("id 为空", a.id.isEmpty());
            assertFalse("标题为空: " + a.id, a.title.isEmpty());
        }
        assertFalse(list.get(0).cover.isEmpty());
    }

    @Test
    public void scramble_slice_count_matches_site_js() {
        // 站点 get_num(): aid=1480638 >= 421926 -> md5(aid+name) 末位 %8
        int s = Scramble.getNum("1480638", "00001.webp");
        assertEquals(12, s);
        assertTrue(Scramble.getNum("100", "00001.webp") >= 2);
    }
}
