package az.lite.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tətbiq açılma sayğacı. Hər açılış +1 xal; xal 7 gündə yarıya enir,
 * ona görə siyahı həmişə son vərdişləri göstərir. Hər şey yalnız telefonda saxlanılır.
 */
final class Usage {

    private static final double HALF_LIFE_MS = 7d * 24 * 3600 * 1000;
    private static final int MAX_KEYS = 60;

    private final SharedPreferences prefs;
    private final Map<String, double[]> data = new HashMap<>(); // key -> {score, timeMs}

    Usage(Context c) {
        prefs = c.getSharedPreferences("usage", Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            Object v = e.getValue();
            if (!(v instanceof String)) continue;
            String[] p = ((String) v).split(";");
            if (p.length != 2) continue;
            try {
                data.put(e.getKey(), new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1])});
            } catch (NumberFormatException ignored) { }
        }
    }

    private static double decayed(double[] d, long now) {
        return d[0] * Math.pow(0.5, (now - d[1]) / HALF_LIFE_MS);
    }

    void hit(String key) {
        long now = System.currentTimeMillis();
        double[] d = data.get(key);
        double score = (d == null ? 0 : decayed(d, now)) + 1;
        data.put(key, new double[]{score, now});
        SharedPreferences.Editor ed = prefs.edit().putString(key, score + ";" + now);
        if (data.size() > MAX_KEYS) { // ən zəifləri at
            final Map<String, Double> sc = new HashMap<>();
            for (Map.Entry<String, double[]> e : data.entrySet()) sc.put(e.getKey(), decayed(e.getValue(), now));
            List<String> weak = new ArrayList<>(sc.keySet());
            Collections.sort(weak, (a, b) -> Double.compare(sc.get(b), sc.get(a)));
            for (int i = MAX_KEYS; i < weak.size(); i++) {
                data.remove(weak.get(i));
                ed.remove(weak.get(i));
            }
        }
        ed.apply();
    }

    /** Ən çox açılan n tətbiq (yalnız hələ quraşdırılmış olanlar). */
    List<String> top(int n, final Set<String> installed) {
        final long now = System.currentTimeMillis();
        final Map<String, Double> score = new HashMap<>();
        for (Map.Entry<String, double[]> e : data.entrySet()) {
            if (installed != null && !installed.contains(e.getKey())) continue;
            double s = decayed(e.getValue(), now);
            if (s >= 0.3) score.put(e.getKey(), s);
        }
        List<String> keys = new ArrayList<>(score.keySet());
        Collections.sort(keys, (a, b) -> Double.compare(score.get(b), score.get(a)));
        return keys.size() > n ? keys.subList(0, n) : keys;
    }
}
