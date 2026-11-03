package com.moco.comicsearch;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 站点 HTML 解析。所有方法都是纯函数，方便用真实页面做单元测试。
 */
public final class Parsers {

    private static final Pattern ALBUM_ID = Pattern.compile("/album/(\\d+)");
    private static final Pattern PHOTO_ID = Pattern.compile("/photo/(\\d+)");
    private static final Pattern SCRAMBLE = Pattern.compile("scramble_id\\s*=\\s*(\\d+)");
    private static final Pattern AID = Pattern.compile("var\\s+aid\\s*=\\s*(\\d+)");
    private static final Pattern DIGITS = Pattern.compile("(\\d+)");

    private Parsers() {
    }

    // ------------------------------------------------------------ 搜索结果

    public static List<Models.Album> parseSearch(String html, String baseUrl) {
        List<Models.Album> out = new ArrayList<>();
        if (html == null || html.isEmpty()) {
            return out;
        }
        Document doc = Jsoup.parse(html, baseUrl);
        Elements cols = doc.select("div.list-col");
        for (Element col : cols) {
            Element link = col.selectFirst("a[href*=/album/]");
            if (link == null) {
                continue;
            }
            String id = match(ALBUM_ID, link.attr("href"));
            if (id.isEmpty()) {
                continue;
            }
            Models.Album a = new Models.Album();
            a.id = id;

            Element img = col.selectFirst("img[data-original]");
            if (img != null) {
                a.cover = img.attr("data-original");
            }

            Element titleEl = col.selectFirst("span.video-title");
            if (titleEl != null) {
                a.title = titleEl.text().trim();
            }
            if (a.title.isEmpty() && img != null) {
                a.title = img.attr("title").trim();
            }

            Element likes = col.selectFirst("span[id^=albim_likes_]");
            if (likes != null) {
                a.likes = likes.text().trim();
            }

            Element author = col.selectFirst("div.title-truncate:not(.tags) > a");
            if (author != null) {
                a.author = author.text().trim();
            }

            Element cat = col.selectFirst(".label-category");
            if (cat != null) {
                a.category = cat.text().trim();
            }
            Element sub = col.selectFirst(".label-sub");
            if (sub != null) {
                a.sub = sub.text().trim();
            }

            for (Element tag : col.select("div.tags a.tag")) {
                String t = tag.text().trim();
                if (!t.isEmpty() && !a.tags.contains(t)) {
                    a.tags.add(t);
                }
            }
            out.add(a);
        }
        return out;
    }

    // ------------------------------------------------------------ 详情页

    public static Models.Album parseAlbum(String html, String baseUrl) {
        Models.Album a = new Models.Album();
        if (html == null || html.isEmpty()) {
            return a;
        }
        Document doc = Jsoup.parse(html, baseUrl);

        Element h1 = doc.selectFirst("h1#book-name");
        if (h1 != null) {
            a.title = h1.text().trim();
        }
        if (a.title.isEmpty()) {
            Element og = doc.selectFirst("meta[property=og:title]");
            if (og != null) {
                a.title = og.attr("content").trim();
            }
        }

        // 封面：优先封面区域里的 itemprop=image，避开页面上的广告图
        for (Element img : doc.select("#album_photo_cover img[itemprop=image]")) {
            String src = img.attr("src");
            if (!src.isEmpty() && !src.contains("blank") && !src.endsWith(".gif")) {
                a.cover = src;
                break;
            }
        }
        if (a.cover.isEmpty()) {
            for (Element img : doc.select("img[itemprop=image]")) {
                String src = img.attr("src");
                if (src.contains("/media/albums/") && !src.contains("blank")) {
                    a.cover = src;
                    break;
                }
            }
        }
        if (a.cover.isEmpty()) {
            for (Element img : doc.select("img[src*=/media/albums/]")) {
                String src = img.attr("src");
                if (!src.contains("blank") && !src.endsWith(".gif")) {
                    a.cover = src;
                    break;
                }
            }
        }

        Element pages = doc.selectFirst(".pagecount");
        if (pages != null) {
            a.pages = match(DIGITS, pages.text());
        }

        Element reading = doc.selectFirst("a.reading[href]");
        if (reading != null) {
            a.readId = match(PHOTO_ID, reading.attr("href"));
        }

        Element desc = doc.selectFirst("meta[property=og:description]");
        if (desc != null) {
            a.description = desc.attr("content").trim();
        }

        // 作者 / 分类标签
        Element authorEl = doc.selectFirst("span[itemprop=author] a");
        if (authorEl != null) {
            a.author = authorEl.text().trim();
        }
        for (Element tag : doc.select("span[itemprop=genre][data-type=tags] a")) {
            String t = tag.text().trim();
            if (!t.isEmpty() && !a.tags.contains(t)) {
                a.tags.add(t);
            }
        }
        // 兜底：从 tag-block 里按标题文字找
        if (a.author.isEmpty() || a.tags.isEmpty()) {
            for (Element block : doc.select("div.tag-block:has(h2.h2_info)")) {
                Element h2 = block.selectFirst("h2.h2_info");
                String label = h2 == null ? "" : h2.text().trim();
                List<String> values = new ArrayList<>();
                for (Element link : block.select("a")) {
                    String t = link.text().trim();
                    if (!t.isEmpty() && !values.contains(t)) {
                        values.add(t);
                    }
                }
                if (label.startsWith("作者") && a.author.isEmpty() && !values.isEmpty()) {
                    a.author = values.get(0);
                } else if (label.startsWith("分类标签")) {
                    for (String v : values) {
                        if (!a.tags.contains(v)) {
                            a.tags.add(v);
                        }
                    }
                }
            }
        }

        // 章节（多话本子才有）
        for (Element link : doc.select("#episode-block a[href*=/photo/], .episode a[href*=/photo/]")) {
            String cid = match(PHOTO_ID, link.attr("href"));
            if (cid.isEmpty()) {
                continue;
            }
            Models.Chapter c = new Models.Chapter();
            c.id = cid;
            c.name = link.text().trim();
            if (c.name.isEmpty()) {
                c.name = "第 " + (a.chapters.size() + 1) + " 话";
            }
            a.chapters.add(c);
        }
        if (a.chapters.isEmpty() && !a.readId.isEmpty()) {
            Models.Chapter c = new Models.Chapter();
            c.id = a.readId;
            c.name = "开始阅读";
            a.chapters.add(c);
        }
        return a;
    }

    // ------------------------------------------------------------ 阅读页

    public static Models.ChapterContent parseChapter(String html, String baseUrl) {
        Models.ChapterContent content = new Models.ChapterContent();
        if (html == null || html.isEmpty()) {
            return content;
        }
        Document doc = Jsoup.parse(html, baseUrl);

        String scramble = match(SCRAMBLE, html);
        if (!scramble.isEmpty()) {
            try {
                content.scrambleId = Integer.parseInt(scramble);
            } catch (NumberFormatException ignored) {
            }
        }
        String aid = match(AID, html);
        if (!aid.isEmpty()) {
            content.aid = aid;
        }

        // 每一页包在 <div class="scramble-page" id="00001.webp"> 里
        Elements containers = doc.select("div.scramble-page, div[id~=\\.(webp|jpg|png|jpeg)$]");
        for (Element box : containers) {
            String name = box.id();
            Element img = box.selectFirst("img[data-original]");
            if (img == null) {
                img = box.selectFirst("img[data-src]");
            }
            if (img == null) {
                continue;
            }
            String url = img.attr("data-original");
            if (url.isEmpty()) {
                url = img.attr("data-src");
            }
            if (url.isEmpty() || url.contains("blank")) {
                continue;
            }
            content.pages.add(new Models.PageImage(url, name));
        }

        // 兜底：直接抓 media/photos 下的图
        if (content.pages.isEmpty()) {
            for (Element img : doc.select("img[data-original]")) {
                String url = img.attr("data-original");
                if (url.contains("/media/photos/")) {
                    String name = url.substring(url.lastIndexOf('/') + 1);
                    content.pages.add(new Models.PageImage(url, name));
                }
            }
        }
        return content;
    }

    // ------------------------------------------------------------ 官方发布页分流

    private static final Pattern HOSTNAME = Pattern.compile(
            "^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$");

    /**
     * 解析官方发布页上的分流线路。
     * 页面结构：div.wrap > div 为分组，组内 <p> 是线路名、<small> 是说明、<span> 是域名。
     * 只收纯域名，带路径的 APP 下载 / Discord / TG 链接和邮箱会被自动排除。
     */
    public static List<Models.Mirror> parseMirrors(String html) {
        List<Models.Mirror> out = new ArrayList<>();
        if (html == null || html.isEmpty()) {
            return out;
        }
        Document doc = Jsoup.parse(html);
        Elements groups = doc.select("div.wrap > div");
        if (groups.isEmpty()) {
            groups = doc.select("div.wrap div");
        }
        for (Element group : groups) {
            Element p = group.selectFirst("p");
            String label = p == null ? "" : p.text().trim();
            Element small = group.selectFirst("small");
            String note = small == null ? "" : small.text().trim();
            if (label.contains("APP") || label.contains("郵件") || label.contains("邮箱")
                    || label.contains("DC") || label.contains("TG")) {
                continue;
            }
            for (Element span : group.select("span")) {
                String host = span.text().trim();
                if (!HOSTNAME.matcher(host).matches()) {
                    continue;
                }
                boolean dup = false;
                for (Models.Mirror m : out) {
                    if (m.host.equalsIgnoreCase(host)) {
                        dup = true;
                        break;
                    }
                }
                if (dup) {
                    continue;
                }
                Models.Mirror m = new Models.Mirror();
                m.label = label;
                m.note = note;
                m.host = host;
                out.add(m);
            }
        }
        return out;
    }

    /**
     * 用编号搜索时站点会 302 到 /album/{id}/，这里判断拿到的到底是列表页还是详情页。
     */
    /**
     * 从作品详情页的评论区提取评论内容。
     * 优先找评论区容器，再取每条评论的正文节点；结构变化时退化为整块文本。
     */
    public static List<String> parseComments(String html) {
        List<String> out = new ArrayList<>();
        if (html == null || html.isEmpty()) {
            return out;
        }
        try {
            Document doc = Jsoup.parse(html);
            Element area = doc.selectFirst("#comment_area, #comments, .comment-area,"
                    + " .comment-list, .comments");
            Elements blocks = area == null ? new Elements()
                    : area.select(".comment-item, .comment, li");
            if (blocks.isEmpty()) {
                blocks = doc.select("#comment_area .comment-item, .comment-item,"
                        + " .comment-list .comment, .comments .comment");
            }
            for (Element e : blocks) {
                String text = commentText(e);
                if (isUsefulComment(text) && !out.contains(text)) {
                    out.add(text);
                }
            }
            if (out.isEmpty() && area != null) {
                for (Element p : area.select("p, .content, .text, .body")) {
                    String text = p.text().trim();
                    if (isUsefulComment(text) && !out.contains(text)) {
                        out.add(text);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String commentText(Element e) {
        Element body = e.selectFirst(".comment-content, .content, .text, .body, p");
        String text = body == null ? e.text() : body.text();
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private static boolean isUsefulComment(String text) {
        if (text == null || text.length() < 2) {
            return false;
        }
        return !"评论".equals(text) && !"发表评论".equals(text) && !"暂无评论".equals(text);
    }

    public static boolean isAlbumPage(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        Document doc = Jsoup.parse(html);
        // 详情页独有 h1#book-name；列表页有 div.list-col
        if (doc.selectFirst("div.list-col") != null) {
            return false;
        }
        return doc.selectFirst("h1#book-name") != null;
    }

    /** 从详情页里取作品编号（canonical / og:url）。 */
    public static String albumIdFrom(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        Document doc = Jsoup.parse(html);
        Element canonical = doc.selectFirst("link[rel=canonical]");
        String href = canonical == null ? "" : canonical.attr("href");
        if (href.isEmpty()) {
            Element og = doc.selectFirst("meta[property=og:url]");
            if (og != null) {
                href = og.attr("content");
            }
        }
        String id = match(ALBUM_ID, href);
        if (!id.isEmpty()) {
            return id;
        }
        Element aidInput = doc.selectFirst("input#album_id, #album_id");
        if (aidInput != null) {
            return match(DIGITS, aidInput.attr("value"));
        }
        return "";
    }

    /** 是否有下一页（搜索结果翻页）。 */    public static boolean hasNextPage(String html) {
        if (html == null) {
            return false;
        }
        Document doc = Jsoup.parse(html);
        Element next = doc.selectFirst("a[rel=next], .pagination a:contains(下一页), li.next:not(.disabled) a");
        return next != null;
    }

    private static String match(Pattern p, String s) {
        if (s == null) {
            return "";
        }
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : "";
    }
}
