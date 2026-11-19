package az.lite.launcher;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Qovluqlar üçün öz vektor ikonlarımız (res/drawable/g_*.xml). */
final class Glyphs {

    static final String[] KEYS = {"folder", "money", "bank", "chat", "food", "car", "plane", "game", "sport", "work", "code", "play", "music", "camera", "cart", "book", "edu", "heart", "star", "home", "tools", "globe", "lock", "phone", "cloud", "health", "bolt", "user", "map", "apps"};

    private static final int[] RES = {
            R.drawable.g_folder,
            R.drawable.g_money,
            R.drawable.g_bank,
            R.drawable.g_chat,
            R.drawable.g_food,
            R.drawable.g_car,
            R.drawable.g_plane,
            R.drawable.g_game,
            R.drawable.g_sport,
            R.drawable.g_work,
            R.drawable.g_code,
            R.drawable.g_play,
            R.drawable.g_music,
            R.drawable.g_camera,
            R.drawable.g_cart,
            R.drawable.g_book,
            R.drawable.g_edu,
            R.drawable.g_heart,
            R.drawable.g_star,
            R.drawable.g_home,
            R.drawable.g_tools,
            R.drawable.g_globe,
            R.drawable.g_lock,
            R.drawable.g_phone,
            R.drawable.g_cloud,
            R.drawable.g_health,
            R.drawable.g_bolt,
            R.drawable.g_user,
            R.drawable.g_map,
            R.drawable.g_apps
    };

    private static final Map<String, Drawable> CACHE = new HashMap<>();

    static int res(String key) {
        if (key == null) return 0;
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return RES[i];
        return 0;
    }

    /** Rəngə boyanmış ikon (keşlənir). */
    static Drawable get(Context c, String key, int color) {
        int r = res(key);
        if (r == 0) return null;
        // Hər çağırışa ayrıca nüsxə: eyni Drawable bir neçə View-da paylaşılanda
        // ölçüləri (bounds) bir-birini pozurdu — ikon yerindən sürüşürdü.
        Drawable d = c.getDrawable(r).mutate();
        d.setTint(color);
        return d;
    }

    // açar söz (sözün əvvəli) → ikon
    private static final String[][] RULES = {
            {"pul", "money"}, {"maliyy", "money"}, {"finans", "money"}, {"ödəniş", "money"}, {"odenis", "money"},
            {"bank", "bank"},
            {"sosial", "chat"}, {"social", "chat"}, {"mesaj", "chat"}, {"chat", "chat"},
            {"yem", "food"}, {"food", "food"}, {"kafe", "food"},
            {"taksi", "car"}, {"taxi", "car"}, {"maşın", "car"}, {"masin", "car"}, {"nəqliyyat", "car"},
            {"səyah", "plane"}, {"seyah", "plane"}, {"travel", "plane"},
            {"xəritə", "map"}, {"xerite", "map"}, {"map", "map"},
            {"oyun", "game"}, {"game", "game"},
            {"idman", "sport"}, {"sport", "sport"}, {"fitness", "sport"},
            {"musiq", "music"}, {"music", "music"},
            {"film", "play"}, {"video", "play"}, {"kino", "play"}, {"media", "play"},
            {"foto", "camera"}, {"şəkil", "camera"}, {"sekil", "camera"}, {"kamera", "camera"},
            {"alış", "cart"}, {"alis", "cart"}, {"shop", "cart"}, {"market", "cart"},
            {"iş", "work"}, {"is", "work"}, {"work", "work"}, {"ofis", "work"}, {"office", "work"}, {"microsoft", "work"},
            {"təhsil", "edu"}, {"tehsil", "edu"}, {"universitet", "edu"}, {"dərs", "book"}, {"ders", "book"}, {"kitab", "book"},
            {"sağlam", "health"}, {"saglam", "health"}, {"aptek", "health"}, {"health", "health"},
            {"ev", "home"}, {"home", "home"},
            {"kod", "code"}, {"dev", "code"}, {"proqram", "code"},
            {"ai", "bolt"}, {"süni", "bolt"},
            {"alət", "tools"}, {"alet", "tools"}, {"tool", "tools"}, {"sistem", "tools"},
            {"google", "globe"}, {"web", "globe"}, {"internet", "globe"},
            {"təhlükə", "lock"}, {"tehluke", "lock"}, {"şifrə", "lock"}, {"sifre", "lock"},
            {"bulud", "cloud"}, {"cloud", "cloud"},
    };

    static String suggest(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String word : n.split("[\\s_\\-]+")) {
            if (word.isEmpty()) continue;
            for (String[] r : RULES) {
                if (word.equals(r[0]) || (r[0].length() > 2 && word.startsWith(r[0]))) return r[1];
            }
        }
        return null;
    }

    /** Köhnə versiyadakı emoji ikonlarını yeni ikonlara köçürmək üçün. */
    static String fromEmoji(String e) {
        if (e == null) return null;
        switch (e) {
            case "💰": case "🧾": return "money";
            case "🏦": return "bank";
            case "💬": return "chat";
            case "📱": return "phone";
            case "🍔": case "☕": return "food";
            case "🚕": return "car";
            case "✈️": return "plane";
            case "🎮": return "game";
            case "⚽": return "sport";
            case "🎵": return "music";
            case "🎬": return "play";
            case "📷": return "camera";
            case "🛒": return "cart";
            case "💼": case "📎": return "work";
            case "📚": case "📰": return "book";
            case "🎓": return "edu";
            case "💊": return "health";
            case "🏠": return "home";
            case "💻": return "code";
            case "🛠️": case "⚙️": return "tools";
            case "🔎": case "🌐": return "globe";
            case "🔒": return "lock";
            case "❤️": return "heart";
            case "🔥": case "🤖": return "bolt";
            case "🧩": return "apps";
            case "📁": return "folder";
            default: return "star";
        }
    }

    private Glyphs() { }
}
