package com.vortex.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class HistoryManager {
    private static final String PREFS_NAME = "vortex_history";
    private static final String KEY_HISTORY = "history";
    private static final int MAX_HISTORY = 500;
    private final SharedPreferences prefs;

    public HistoryManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void addEntry(String title, String url) {
        if (url == null || url.startsWith("file://")) return;
        List<HistoryEntry> list = getHistory();
        // Remove duplicate if exists
        list.removeIf(h -> h.url.equals(url));
        list.add(0, new HistoryEntry(title, url, System.currentTimeMillis()));
        // Trim
        if (list.size() > MAX_HISTORY) {
            list = list.subList(0, MAX_HISTORY);
        }
        save(list);
    }

    public List<HistoryEntry> getHistory() {
        List<HistoryEntry> list = new ArrayList<>();
        String json = prefs.getString(KEY_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new HistoryEntry(
                    o.optString("title", ""),
                    o.optString("url", ""),
                    o.optLong("time", 0)
                ));
            }
        } catch (JSONException e) { /* ignore */ }
        return list;
    }

    public List<HistoryEntry> search(String query) {
        List<HistoryEntry> results = new ArrayList<>();
        String q = query.toLowerCase();
        for (HistoryEntry h : getHistory()) {
            if (h.title.toLowerCase().contains(q) || h.url.toLowerCase().contains(q)) {
                results.add(h);
            }
        }
        return results;
    }

    public void clearHistory() {
        prefs.edit().putString(KEY_HISTORY, "[]").apply();
    }

    public void removeEntry(String url) {
        List<HistoryEntry> list = getHistory();
        list.removeIf(h -> h.url.equals(url));
        save(list);
    }

    private void save(List<HistoryEntry> list) {
        JSONArray arr = new JSONArray();
        for (HistoryEntry h : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("title", h.title);
                o.put("url", h.url);
                o.put("time", h.timestamp);
                arr.put(o);
            } catch (JSONException e) { /* ignore */ }
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    public static class HistoryEntry {
        public String title;
        public String url;
        public long timestamp;

        public HistoryEntry(String title, String url, long timestamp) {
            this.title = title;
            this.url = url;
            this.timestamp = timestamp;
        }
    }
}
