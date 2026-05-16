package com.vortex.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class BookmarkManager {
    private static final String PREFS_NAME = "vortex_bookmarks";
    private static final String KEY_BOOKMARKS = "bookmarks";
    private final SharedPreferences prefs;

    public BookmarkManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void addBookmark(String title, String url) {
        List<Bookmark> list = getBookmarks();
        // Don't add duplicates
        for (Bookmark b : list) {
            if (b.url.equals(url)) return;
        }
        list.add(0, new Bookmark(title, url, System.currentTimeMillis()));
        save(list);
    }

    public void removeBookmark(String url) {
        List<Bookmark> list = getBookmarks();
        list.removeIf(b -> b.url.equals(url));
        save(list);
    }

    public boolean isBookmarked(String url) {
        if (url == null) return false;
        for (Bookmark b : getBookmarks()) {
            if (b.url.equals(url)) return true;
        }
        return false;
    }

    public List<Bookmark> getBookmarks() {
        List<Bookmark> list = new ArrayList<>();
        String json = prefs.getString(KEY_BOOKMARKS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Bookmark(
                    o.optString("title", ""),
                    o.optString("url", ""),
                    o.optLong("time", 0)
                ));
            }
        } catch (JSONException e) { /* ignore */ }
        return list;
    }

    public List<Bookmark> search(String query) {
        List<Bookmark> results = new ArrayList<>();
        String q = query.toLowerCase();
        for (Bookmark b : getBookmarks()) {
            if (b.title.toLowerCase().contains(q) || b.url.toLowerCase().contains(q)) {
                results.add(b);
            }
        }
        return results;
    }

    private void save(List<Bookmark> list) {
        JSONArray arr = new JSONArray();
        for (Bookmark b : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("title", b.title);
                o.put("url", b.url);
                o.put("time", b.timestamp);
                arr.put(o);
            } catch (JSONException e) { /* ignore */ }
        }
        prefs.edit().putString(KEY_BOOKMARKS, arr.toString()).apply();
    }

    public static class Bookmark {
        public String title;
        public String url;
        public long timestamp;

        public Bookmark(String title, String url, long timestamp) {
            this.title = title;
            this.url = url;
            this.timestamp = timestamp;
        }
    }
}
