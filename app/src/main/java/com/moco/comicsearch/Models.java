package com.moco.comicsearch;

import java.util.ArrayList;
import java.util.List;

/** 站点数据模型。 */
public class Models {

    public static class Album {
        public String id = "";
        public String title = "";
        public String cover = "";
        public String author = "";
        public String category = "";
        public String sub = "";
        public String likes = "";
        public String pages = "";
        public String description = "";
        public String readId = "";                  // /photo/{id} 里的 id
        public String extra = "";
        /** 下载进度 0-100；-1 表示不显示进度条。 */
        public int progress = -1;                   // 附加说明（如缓存大小）
        public final List<String> tags = new ArrayList<>();
        public final List<Chapter> chapters = new ArrayList<>();

        public boolean hasChapters() {
            return chapters.size() > 1;
        }
    }

    public static class Chapter {
        public String id = "";
        public String name = "";
    }

    public static class PageImage {
        public String url = "";
        public String name = "";   // 形如 00001.webp，解扰要用它的数字部分

        public PageImage() {
        }

        public PageImage(String url, String name) {
            this.url = url;
            this.name = name;
        }
    }

    public static class ChapterContent {
        public String aid = "";
        public int scrambleId = 0;
        public final List<PageImage> pages = new ArrayList<>();
    }

    /** 官方发布页上的一条分流线路。 */
    public static class Mirror {
        public String label = "";      // 分组名，如「內地網域」「分流1」
        public String note = "";       // 分组小字说明
        public String host = "";       // 域名，如 comic18j-moco.work
        public boolean reachable = false;

        public String displayName() {
            return label.isEmpty() ? host : label + " · " + host;
        }
    }

    /** 本地阅读历史：只记录元信息，图片仍走 ImageLoader 的磁盘缓存。 */
    public static class History {
        public String id = "";          // 作品 id（去重用）
        public String title = "";       // 作品名
        public String cover = "";       // 封面
        public String readId = "";      // 章节 id（/photo/{id}）
        public String chapterName = ""; // 章节名
        public int index = 0;           // 章节序号
        public long time = 0;           // 最后阅读时间
        public final List<Chapter> chapters = new ArrayList<>();
    }
}
