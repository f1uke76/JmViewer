package com.moco.comicsearch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 收藏、下载记录的本地存储（JSON 存 SharedPreferences）。 */
public class Store {

    private static final String FILE = "comic_store";
    private static final String KEY_FAV = "favorites";
    private static final String KEY_DOWNLOAD = "downloads";

    private final SharedPreferences sp;

    public Store(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ 收藏

    public List<Models.Album> favorites() {
        return read(KEY_FAV);
    }

    public boolean isFavorite(String id) {
        for (Models.Album a : favorites()) {
            if (a.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public void addFavorite(Models.Album album) {
        List<Models.Album> list = favorites();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(album.id)) {
                list.remove(i);
                break;
            }
        }
        list.add(0, album);
        write(KEY_FAV, list);
    }

    public void removeFavorite(String id) {
        List<Models.Album> list = favorites();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
        write(KEY_FAV, list);
    }

    // ------------------------------------------------------------ 下载

    public List<Models.Album> downloads() {
        return read(KEY_DOWNLOAD);
    }

    public boolean isDownloaded(String id) {
        for (Models.Album a : downloads()) {
            if (a.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public void addDownload(Models.Album album) {
        List<Models.Album> list = downloads();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(album.id)) {
                list.remove(i);
                break;
            }
        }
        list.add(0, album);
        write(KEY_DOWNLOAD, list);
    }

    public void removeDownload(String id) {
        List<Models.Album> list = downloads();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
        write(KEY_DOWNLOAD, list);
    }

    // ------------------------------------------------------------ 阅读历史

    private static final String KEY_HISTORY = "read_history";
    private static final int MAX_HISTORY = 100;

    public List<Models.History> history() {
        List<Models.History> out = new ArrayList<>();
        String raw = sp.getString(KEY_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                Models.History h = new Models.History();
                h.id = o.optString("id");
                h.title = o.optString("title");
                h.cover = o.optString("cover");
                h.readId = o.optString("readId");
                h.chapterName = o.optString("chapterName");
                h.index = o.optInt("index", 0);
                h.time = o.optLong("time", 0);
                JSONArray cs = o.optJSONArray("chapters");
                if (cs != null) {
                    for (int j = 0; j < cs.length(); j++) {
                        JSONObject co = cs.optJSONObject(j);
                        if (co == null) {
                            continue;
                        }
                        Models.Chapter c = new Models.Chapter();
                        c.id = co.optString("id");
                        c.name = co.optString("name");
                        if (!c.id.isEmpty()) {
                            h.chapters.add(c);
                        }
                    }
                }
                if (!h.id.isEmpty()) {
                    out.add(h);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** 记录一次阅读：同一作品只保留最新一条，并置顶。 */
    public void addHistory(Models.History item) {
        if (item == null || item.id.isEmpty()) {
            return;
        }
        List<Models.History> list = history();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(item.id)) {
                list.remove(i);
                break;
            }
        }
        list.add(0, item);
        while (list.size() > MAX_HISTORY) {
            list.remove(list.size() - 1);
        }
        writeHistory(list);
    }

    public void removeHistory(String id) {
        List<Models.History> list = history();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
        writeHistory(list);
    }

    public void clearReadHistory() {
        sp.edit().remove(KEY_HISTORY).apply();
    }

    // ------------------------------------------------------------ 导入 / 导出

    public String rawFavorites() {
        return sp.getString(KEY_FAV, "[]");
    }

    public void putRawFavorites(String raw) {
        sp.edit().putString(KEY_FAV, raw == null || raw.isEmpty() ? "[]" : raw).apply();
    }

    public String rawReadHistory() {
        return sp.getString(KEY_HISTORY, "[]");
    }

    public void putRawReadHistory(String raw) {
        sp.edit().putString(KEY_HISTORY, raw == null || raw.isEmpty() ? "[]" : raw).apply();
    }

    private void writeHistory(List<Models.History> list) {
        JSONArray arr = new JSONArray();
        for (Models.History h : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", h.id);
                o.put("title", h.title);
                o.put("cover", h.cover);
                o.put("readId", h.readId);
                o.put("chapterName", h.chapterName);
                o.put("index", h.index);
                o.put("time", h.time);
                JSONArray cs = new JSONArray();
                for (Models.Chapter c : h.chapters) {
                    JSONObject co = new JSONObject();
                    co.put("id", c.id);
                    co.put("name", c.name);
                    cs.put(co);
                }
                o.put("chapters", cs);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        sp.edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    // ------------------------------------------------------------ 序列化

    private List<Models.Album> read(String key) {
        List<Models.Album> out = new ArrayList<>();
        String raw = sp.getString(key, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                Models.Album a = new Models.Album();
                a.id = o.optString("id");
                a.title = o.optString("title");
                a.cover = o.optString("cover");
                a.author = o.optString("author");
                a.readId = o.optString("readId");
                a.pages = o.optString("pages");
                JSONArray tags = o.optJSONArray("tags");
                if (tags != null) {
                    for (int j = 0; j < tags.length(); j++) {
                        a.tags.add(tags.optString(j));
                    }
                }
                out.add(a);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void write(String key, List<Models.Album> list) {
        JSONArray arr = new JSONArray();
        for (Models.Album a : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", a.id);
                o.put("title", a.title);
                o.put("cover", a.cover);
                o.put("author", a.author);
                o.put("readId", a.readId);
                o.put("pages", a.pages);
                JSONArray tags = new JSONArray();
                for (String t : a.tags) {
                    tags.put(t);
                }
                o.put("tags", tags);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        sp.edit().putString(key, arr.toString()).apply();
    }
}
