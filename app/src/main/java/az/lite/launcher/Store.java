package az.lite.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Ana səhifənin məzmunu (tətbiqlər, qovluqlar, xana mövqeləri) və vidcet siyahısı. */
final class Store {

    static final int DEFAULT_COLOR = 0xFF1F1F1F;
    static final int DEFAULT_TEXT = 0xFFFFFFFF;

    static final class Item {
        boolean folder;
        String app;                 // tətbiqdirsə: ComponentName.flattenToString()
        int pos = -1;               // ana ekran xanası (sətir * 4 + sütun)
        String name = "";
        int color = DEFAULT_COLOR;
        int textColor = DEFAULT_TEXT;
        String icon;                // Glyphs açarı; null → içindəki tətbiqlərin mini ikonları
        final List<String> apps = new ArrayList<>();
    }

    final List<Item> items = new ArrayList<>();
    final List<Integer> widgets = new ArrayList<>();
    /** Sürətli panel tətbiqləri (maks 9). Boşdursa ən çox açılanlar göstərilir. */
    final List<String> quick = new ArrayList<>();
    /** HomeGrid-in ayırdığı xanalar (sürətli düymə) — ora heç nə qoyulmur. */
    Set<Integer> reserved = new HashSet<>();
    private final SharedPreferences prefs;

    Store(Context c) {
        prefs = c.getSharedPreferences("home", Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        items.clear();
        try {
            JSONArray arr = new JSONArray(prefs.getString("items", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Item it = new Item();
                it.folder = o.optBoolean("f");
                it.pos = o.optInt("p", -1);
                if (it.folder) {
                    it.name = o.optString("n", "Qovluq");
                    it.color = o.optInt("c", DEFAULT_COLOR);
                    it.textColor = o.optInt("t", DEFAULT_TEXT);
                    if (o.has("i")) it.icon = o.optString("i");
                    else if (o.has("e")) it.icon = Glyphs.fromEmoji(o.optString("e")); // köhnə versiya
                    // köhnə yarımşəffaf standart rəngi solid rəngə çevir
                    if (it.color == 0x66000000) it.color = DEFAULT_COLOR;
                    JSONArray a = o.optJSONArray("a");
                    if (a != null) for (int j = 0; j < a.length(); j++) it.apps.add(a.getString(j));
                } else {
                    it.app = o.optString("k");
                }
                items.add(it);
            }
        } catch (JSONException ignored) {
        }
        quick.clear();
        for (String k : prefs.getString("quick", "").split("\n")) if (!k.isEmpty()) quick.add(k);
        widgets.clear();
        for (String s : prefs.getString("widgets", "").split(",")) {
            try { if (!s.isEmpty()) widgets.add(Integer.parseInt(s)); } catch (NumberFormatException ignored) { }
        }
    }

    void save() {
        JSONArray arr = new JSONArray();
        try {
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("p", it.pos);
                if (it.folder) {
                    o.put("f", true);
                    o.put("n", it.name);
                    o.put("c", it.color);
                    o.put("t", it.textColor);
                    if (it.icon != null) o.put("i", it.icon);
                    JSONArray a = new JSONArray();
                    for (String k : it.apps) a.put(k);
                    o.put("a", a);
                } else {
                    o.put("k", it.app);
                }
                arr.put(o);
            }
        } catch (JSONException ignored) {
        }
        StringBuilder w = new StringBuilder();
        for (int id : widgets) {
            if (w.length() > 0) w.append(',');
            w.append(id);
        }
        StringBuilder q = new StringBuilder();
        for (String k : quick) q.append(k).append('\n');
        prefs.edit().putString("items", arr.toString()).putString("widgets", w.toString())
                .putString("quick", q.toString()).apply();
    }

    List<Item> folders() {
        List<Item> out = new ArrayList<>();
        for (Item it : items) if (it.folder) out.add(it);
        return out;
    }

    int indexOfApp(String key) {
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (!it.folder && key.equals(it.app)) return i;
        }
        return -1;
    }

    Item itemAt(int pos) {
        for (Item it : items) if (it.pos == pos) return it;
        return null;
    }

    /** Ən kiçik boş xana. */
    int firstFree() {
        Set<Integer> used = new HashSet<>(reserved);
        for (Item it : items) used.add(it.pos);
        int p = 0;
        while (used.contains(p)) p++;
        return p;
    }

    /** cell boşdursa ora, yoxsa ilk boş xanaya qoyur. */
    void place(Item it, int cell) {
        it.pos = (cell >= 0 && itemAt(cell) == null && !reserved.contains(cell)) ? cell : firstFree();
    }

    void addToHome(String key, int cell) {
        if (indexOfApp(key) >= 0) return;
        Item it = new Item();
        it.app = key;
        place(it, cell);
        items.add(it);
    }

    /** Tətbiqi qovluğa qoyur; ana ekranda ayrıca dururdusa oradan götürülür. */
    void addToFolder(String key, Item folder) {
        int i = indexOfApp(key);
        if (i >= 0) items.remove(i);
        for (Item other : items) if (other.folder && other != folder) other.apps.remove(key); // tətbiq yalnız bir qovluqda
        if (!folder.apps.contains(key)) folder.apps.add(key);
    }

    /** Silinmiş tətbiqləri təmizləyir. Dəyişiklik olubsa true. */
    boolean retainInstalled(Set<String> installed) {
        boolean changed = quick.retainAll(installed);
        Iterator<Item> it = items.iterator();
        while (it.hasNext()) {
            Item item = it.next();
            if (item.folder) {
                if (item.apps.retainAll(installed)) changed = true;
            } else if (!installed.contains(item.app)) {
                it.remove();
                changed = true;
            }
        }
        return changed;
    }
}
