package com.vortex.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NotesManager {
    private static final String PREFS_NAME = "vortex_notes";
    private static final String KEY_NOTES = "notes";
    private final SharedPreferences prefs;

    public NotesManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void addNote(String content, String sourceUrl, String sourceTitle) {
        List<Note> list = getNotes();
        list.add(0, new Note(UUID.randomUUID().toString(), content, sourceUrl, sourceTitle, System.currentTimeMillis()));
        save(list);
    }

    public void updateNote(String id, String content) {
        List<Note> list = getNotes();
        for (Note n : list) {
            if (n.id.equals(id)) {
                n.content = content;
                n.timestamp = System.currentTimeMillis();
                break;
            }
        }
        save(list);
    }

    public void deleteNote(String id) {
        List<Note> list = getNotes();
        list.removeIf(n -> n.id.equals(id));
        save(list);
    }

    public List<Note> getNotes() {
        List<Note> list = new ArrayList<>();
        String json = prefs.getString(KEY_NOTES, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Note(
                    o.optString("id", UUID.randomUUID().toString()),
                    o.optString("content", ""),
                    o.optString("sourceUrl", ""),
                    o.optString("sourceTitle", ""),
                    o.optLong("time", 0)
                ));
            }
        } catch (JSONException e) { /* ignore */ }
        return list;
    }

    private void save(List<Note> list) {
        JSONArray arr = new JSONArray();
        for (Note n : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", n.id);
                o.put("content", n.content);
                o.put("sourceUrl", n.sourceUrl);
                o.put("sourceTitle", n.sourceTitle);
                o.put("time", n.timestamp);
                arr.put(o);
            } catch (JSONException e) { /* ignore */ }
        }
        prefs.edit().putString(KEY_NOTES, arr.toString()).apply();
    }

    public static class Note {
        public String id;
        public String content;
        public String sourceUrl;
        public String sourceTitle;
        public long timestamp;

        public Note(String id, String content, String sourceUrl, String sourceTitle, long timestamp) {
            this.id = id;
            this.content = content;
            this.sourceUrl = sourceUrl;
            this.sourceTitle = sourceTitle;
            this.timestamp = timestamp;
        }
    }
}
