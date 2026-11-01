package com.moco.comicsearch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * 轻量配置 + 搜索历史存储。
 */
public class Prefs {

    public static final String DEFAULT_BASE_URL = "https://comic18j-moco.work";

    private static final String FILE = "comic_search_prefs";
    private static final String KEY_BASE = "base_url";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_ADBLOCK = "adblock";
    private static final String KEY_USER = "logged_user";
    private static final String KEY_ICON = "icon_style";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_VIEW = "view_mode";
    private static final String KEY_HOME_REC = "home_recommend";

    private static final int MAX_HISTORY = 15;

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String getBaseUrl() {
        String v = sp.getString(KEY_BASE, DEFAULT_BASE_URL);
        return (v == null || v.trim().isEmpty()) ? DEFAULT_BASE_URL : v;
    }

    public void setBaseUrl(String url) {
        sp.edit().putString(KEY_BASE, normalize(url)).apply();
    }

    public boolean adBlock() {
        return sp.getBoolean(KEY_ADBLOCK, true);
    }

    public String getLoggedUser() {
        String v = sp.getString(KEY_USER, "");
        return v == null ? "" : v;
    }

    public void setLoggedUser(String user) {
        sp.edit().putString(KEY_USER, user == null ? "" : user).apply();
    }

    public int getIconStyle() {
        return sp.getInt(KEY_ICON, 0);
    }

    public void setIconStyle(int style) {
        sp.edit().putInt(KEY_ICON, style).apply();
    }

    /** 0 跟随系统 / 1 明亮 / 2 黑暗 */
    public int getThemeMode() {
        return sp.getInt(KEY_THEME, 0);
    }

    public void setThemeMode(int mode) {
        sp.edit().putInt(KEY_THEME, mode).apply();
    }

    /** 0 列表方式 / 1 封面方式 */
    public int getViewMode() {
        return sp.getInt(KEY_VIEW, 0);
    }

    public void setViewMode(int mode) {
        sp.edit().putInt(KEY_VIEW, mode).apply();
    }

    /** 主页是否显示站点推荐，默认开启。 */
    public boolean homeRecommend() {
        return sp.getBoolean(KEY_HOME_REC, true);
    }

    public void setHomeRecommend(boolean on) {
        sp.edit().putBoolean(KEY_HOME_REC, on).apply();
    }

    public void setAdBlock(boolean on) {
        sp.edit().putBoolean(KEY_ADBLOCK, on).apply();
    }

    public List<String> getHistory() {
        List<String> out = new ArrayList<>();
        String raw = sp.getString(KEY_HISTORY, null);
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "");
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    public void addHistory(String keyword) {
        if (keyword == null) {
            return;
        }
        String q = keyword.trim();
        if (q.isEmpty()) {
            return;
        }
        List<String> list = getHistory();
        list.remove(q);
        list.add(0, q);
        while (list.size() > MAX_HISTORY) {
            list.remove(list.size() - 1);
        }
        saveHistory(list);
    }

    public void removeHistory(String keyword) {
        List<String> list = getHistory();
        list.remove(keyword);
        saveHistory(list);
    }

    public void clearHistory() {
        sp.edit().remove(KEY_HISTORY).apply();
    }

    private void saveHistory(List<String> list) {
        JSONArray arr = new JSONArray();
        for (String s : list) {
            arr.put(s);
        }
        sp.edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    /** 补全协议、去掉结尾斜杠。 */
    public static String normalize(String url) {
        if (url == null) {
            return DEFAULT_BASE_URL;
        }
        String v = url.trim();
        if (v.isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        if (!v.startsWith("http://") && !v.startsWith("https://")) {
            v = "https://" + v;
        }
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }
}
