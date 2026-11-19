package az.lite.launcher;

import android.content.pm.ApplicationInfo;
import android.os.Build;

import java.util.Locale;

/**
 * Tətbiqləri avtomatik qovluqlara bölmək üçün qaydalar.
 * Əvvəl paket adı / ad üzrə açar sözlər, sonra Android-in öz kateqoriyası, sonda "Digər".
 */
final class Groups {

    private Groups() { }

    /** {qovluq adı, glyph açarı, açar sözlər...} — sıra vacibdir (ilk uyğun gələn qalib). */
    private static final String[][] RULES = {
            {"Microsoft", "work", "com.microsoft.", "skype", "outlook", "onedrive", "teams"},
            {"AI", "bolt", "openai", "chatgpt", "anthropic", "claude", "gemini", "android.apps.bard", "perplexity",
                    "deepseek", "copilot", "grok", "character.ai", "poe"},
            {"Pul", "money", "bank", "abb", "kapital", "leobank", "birbank", "pasha", "unibank", "rabite", "xalq",
                    "accessbank", "yelo", "expressbank", "paypal", "emanat", "million", "m10", "pay", "wallet", "binance", "crypto",
                    "revolut", "wise", "finance", "money", "trading", "invest", "exchange", "coin", "tbc", "bybit",
                    "okx", "metamask", "trust"},
            {"Sosial", "chat", "whatsapp", "telegram", "org.thunderdog", "instagram", "facebook", "messenger", "katana",
                    "orca", "tiktok", "musically", "zhiliaoapp", "twitter", "com.x.", "snapchat", "discord", "reddit",
                    "threads", "barcelona", "viber", "signal", "imo", "pinterest", "vkontakte", "tinder", "bumble",
                    "wechat", "jp.naver.line", "kik", "clubhouse", "bereal", "linkedin"},
            {"Yemək", "food", "wolt", "food", "deliveryclient", "burger", "kfc", "mcdonald", "pizza", "domino",
                    "papajohns", "starbucks", "yemek", "restaurant", "glovo", "ubercab.eats", "deli", "coffee", "chicken"},
            {"Taksi", "car", "uber", "mtakso", "olacabs", "yango", "yandex.taxi", "taxi", "taksi", "taxsee", "maxim",
                    "indriver", "indrive", "189", "careem", "parking"},
            {"İdman", "sport", "fit", "workout", "gym", "sport", "strava", "nike", "adidas", "health", "step",
                    "running", "yoga", "pedometer", "calorie", "diet", "height", "abs", "muscle", "sleep", "meditat",
                    "mi.health", "wearable"},
            {"Dev", "code", "github", "gitlab", "termux", "code", "ssh", "postman", "jetbrains", "studio", "adb",
                    "developer", "dev.", "git", "terminal", "json", "api", "server", "firebase", "vercel", "replit",
                    "stackoverflow", "figma"},
            {"Media", "play", "youtube", "spotify", "music", "netflix", "video", "player", "tv", "podcast", "radio",
                    "shazam", "soundcloud", "deezer", "inshot", "capcut", "vlc", "mx", "twitch", "kino", "film",
                    "movie", "anime", "audio", "fm", "gallery", "photo", "camera", "editor", "lightroom", "picsart",
                    "snapseed", "vsco", "canva", "remini"},
            {"Oyun", "game", "game", "steam", "playgames", "games", "supercell", "roblox", "pubg", "tencent",
                    "minecraft", "mojang", "chess", "puzzle", "clash", "garena", "epicgames", "ubisoft", "com.ea.", "com.king.",
                    "miniclip", "zynga", "gameloft", "rockstar", "nintendo", "playstation", "xbox"},
            {"Alış-veriş", "cart", "amazon", "aliexpress", "alibaba", "temu", "shein", "trendyol", "umico", "tap.az",
                    "tapaz", "ebay", "iherb", "shop", "market", "wildberries", "ozon", "hepsiburada", "joom",
                    "lalafo", "bazar", "kontakt", "irshad", "irşad", "baku electronics", "zara", "hm.", "lcwaikiki",
                    "boyner", "koton", "defacto", "araz", "bravo", "bolmart", "oba"},
            {"Səyahət", "plane", "booking", "airbnb", "azal", "airline", "airways", "flight", "trip", "hotel",
                    "skyscanner", "travel", "tripadvisor", "expedia", "kayak", "pegasus", "turkish", "flyone",
                    "wizz", "ryanair", "visa", "tour"},
            {"Təhsil", "edu", "duolingo", "edu", "school", "university", "learn", "coursera", "udemy", "dictionary",
                    "translate", "lugat", "quiz", "khan", "brainly", "photomath", "study", "academy", "lesson",
                    "language", "english", "ielts", "toefl", "kitab", "kindle"},
            {"İş", "work", "evernote", "notion", "adobe", "pdf", "docs", "sheets", "slides", "drive", "dropbox",
                    "slack", "zoom", "fiverr", "upwork", "job", "boss", "hh.ru", "office", "scanner", "camscanner",
                    "notes", "todo", "task", "trello", "asana", "calendar", "mail", "gmail", "meet", "keep",
                    "wps"},
            {"Lite", "bolt", "az.lite."},
            {"Dövlət", "home", "mygov", "notariat", "egov"},
            {"Google", "globe", "com.google.", "com.android.chrome", "android.apps"},
            {"Alətlər", "tools", "com.android.", "com.miui.", "com.xiaomi.", "com.mi.", "settings", "file",
                    "calculator", "clock", "contacts", "dialer", "phone", "mms", "messag", "recorder", "compass",
                    "security", "cleaner", "vpn", "authenticator", "auth", "battery", "backup", "launcher",
                    "keyboard", "bluetooth", "wifi", "torch", "flashlight", "qr", "barcode", "weather", "remote",
                    "theme", "wallpaper", "speed", "share", "transfer", "xender", "link to windows", "phonelink",
                    "password", "bitwarden", "lastpass", "proton", "nord", "1.1.1.1", "cloudflare"},
    };

    static String[] info(String folder) {
        for (String[] r : RULES) if (r[0].equals(folder)) return r;
        return null;
    }

    static String glyphFor(String folder) {
        String[] r = info(folder);
        return r != null ? r[1] : "apps";
    }

    /** Qovluq adı. */
    static String classify(String pkg, String label, ApplicationInfo ai) {
        String p = pkg.toLowerCase(Locale.ROOT);
        String l = label.toLowerCase(Locale.ROOT);
        // birbaşa paket istisnaları
        if (p.startsWith("com.google.android.youtube") || p.equals("com.google.android.apps.youtube.music")) return "Media";
        if (p.equals("com.google.android.apps.maps")) return "Səyahət";
        if (p.equals("com.google.android.apps.photos") || p.equals("com.google.android.googlequicksearchbox")) return "Google";
        if (p.equals("com.google.android.apps.docs") || p.equals("com.google.android.gm")
                || p.equals("com.google.android.calendar") || p.equals("com.google.android.keep")) return "İş";
        if (p.equals("com.google.android.apps.translate")) return "Təhsil";
        if (p.equals("com.android.vending")) return "Google";
        if (p.startsWith("az.lite.")) return "Lite";
        if (p.contains("photomath")) return "Təhsil";
        if (p.contains("pdf") || l.contains("pdf")) return "İş";
        if (p.startsWith("com.google.android.apps.docs")) return "İş";
        if (p.contains("teamviewer") || p.contains("anydesk") || p.contains("rarlab") || l.equals("rar")
                || l.contains("wo mic") || l.contains("alarm")) return "Alətlər";
        if (p.contains("wattpad")) return "Təhsil";
        if (l.contains("mygov") || l.contains("notariat") || l.contains("sima") || l.contains("asan")
                || p.contains("egov") || p.contains(".gov.") || l.contains("vergi") || l.contains("dsmf")) return "Dövlət";

        for (String[] r : RULES) {
            for (int i = 2; i < r.length; i++) {
                String k = r[i];
                if (k.length() <= 3) { // qısa sözlər yalnız tam söz/paket hissəsi kimi
                    if (word(l, k) || segment(p, k)) return r[0];
                } else if (p.contains(k) || l.contains(k)) {
                    return r[0];
                }
            }
            // Android kateqoriyası yalnız açar söz tapılmayanda, uyğun qrupda yoxlanır
        }
        if (ai != null && Build.VERSION.SDK_INT >= 26) {
            switch (ai.category) {
                case ApplicationInfo.CATEGORY_GAME: return "Oyun";
                case ApplicationInfo.CATEGORY_AUDIO:
                case ApplicationInfo.CATEGORY_VIDEO:
                case ApplicationInfo.CATEGORY_IMAGE: return "Media";
                case ApplicationInfo.CATEGORY_SOCIAL: return "Sosial";
                case ApplicationInfo.CATEGORY_MAPS: return "Səyahət";
                case ApplicationInfo.CATEGORY_PRODUCTIVITY: return "İş";
                case ApplicationInfo.CATEGORY_NEWS: return "Xəbər";
                default: break;
            }
        }
        if (ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) return "Alətlər";
        return "Digər";
    }

    private static boolean word(String s, String k) {
        for (String w : s.split("[^\\p{L}\\p{N}.]+")) if (w.equals(k)) return true;
        return false;
    }

    private static boolean segment(String pkg, String k) {
        for (String w : pkg.split("\\.")) if (w.equals(k)) return true;
        return false;
    }

    /** "Yemək" == "yemek" == "YEMEK" — mövcud qovluqları tapmaq üçün. */
    static String norm(String s) {
        return s.toLowerCase(new Locale("az")).trim()
                .replace('ə', 'e').replace('ı', 'i').replace('i', 'i').replace('ö', 'o')
                .replace('ü', 'u').replace('ş', 's').replace('ç', 'c').replace('ğ', 'g')
                .replace("̇", "").replace("-", "").replace(" ", "");
    }
}
