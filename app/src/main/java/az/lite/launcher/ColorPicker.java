package az.lite.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ətraflı rəng seçici (modal panel): böyük palitra (12 çalar × 7 ton + boz tonlar),
 * doyğunluq/parlaqlıq kvadratı, çalar zolağı, HEX yazmaq və son istifadə olunanlar.
 */
final class ColorPicker {

    interface Cb { void on(int color); }

    private static final int TRANSPARENT = 0x00000000;

    static void show(final FrameLayout root, String title, int initial, boolean allowTransparent, final Cb done) {
        final Context c = root.getContext();
        final int[] color = {initial};
        final float[] hsv = new float[3];
        Color.colorToHSV(initial | 0xFF000000, hsv);

        final Sheet s = new Sheet(root, L.t(title));
        ScrollView sv = new ScrollView(c);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalScrollBarEnabled(false);
        final LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);

        // önizləmə + HEX
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        final View preview = new View(c);
        top.addView(preview, new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 44)));
        final EditText hex = Ui.input(c, "#RRGGBB");
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, -2, 1);
        hlp.leftMargin = Ui.dp(c, 12);
        top.addView(hex, hlp);
        col.addView(top);

        final List<View> swatches = new ArrayList<>();
        final List<Integer> swatchColors = new ArrayList<>();
        final SVView svView = new SVView(c);
        final HueBar hue = new HueBar(c);
        final boolean[] typing = {false};

        final Runnable paint = () -> {
            preview.setBackground(color[0] == TRANSPARENT
                    ? Ui.outline(0, Ui.TEXT2, Ui.dp(c, 1)) : Ui.outline(color[0], Ui.LINE, Ui.dp(c, 1)));
            if (!typing[0]) {
                hex.setText(color[0] == TRANSPARENT ? L.t("Şəffaf")
                        : String.format(Locale.ROOT, "#%06X", color[0] & 0xFFFFFF));
            }
            svView.setHsv(hsv);
            hue.setHue(hsv[0]);
            for (int i = 0; i < swatches.size(); i++) {
                int sc = swatchColors.get(i);
                boolean sel = sc == color[0];
                swatches.get(i).setBackground(sc == TRANSPARENT
                        ? Ui.outline(0, sel ? Ui.ACCENT : Ui.TEXT2, Ui.dp(c, sel ? 3 : 1))
                        : Ui.outline(sc, sel ? Ui.ACCENT : 0x22FFFFFF, Ui.dp(c, sel ? 3 : 1)));
            }
        };

        final Cb setFromPalette = v -> {
            color[0] = v;
            if (v != TRANSPARENT) Color.colorToHSV(v, hsv);
            paint.run();
        };

        // doyğunluq / parlaqlıq kvadratı + çalar zolağı
        col.addView(Ui.section(c, "Dəqiq rəng"));
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 150));
        col.addView(svView, svlp);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, Ui.dp(c, 30));
        hl.topMargin = Ui.dp(c, 10);
        col.addView(hue, hl);
        svView.cb = (sat, val) -> {
            hsv[1] = sat;
            hsv[2] = val;
            color[0] = Color.HSVToColor(hsv);
            paint.run();
        };
        hue.cb = h -> {
            hsv[0] = h;
            if (color[0] == TRANSPARENT) { hsv[1] = 1f; hsv[2] = 1f; }
            color[0] = Color.HSVToColor(hsv);
            paint.run();
        };

        // son istifadə olunanlar
        final SharedPreferences prefs = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        List<Integer> recent = new ArrayList<>();
        for (String x : prefs.getString("recent_colors", "").split(",")) {
            try { if (!x.isEmpty()) recent.add((int) Long.parseLong(x)); } catch (NumberFormatException ignored) { }
        }
        if (!recent.isEmpty()) {
            col.addView(Ui.section(c, "Son istifadə"));
            col.addView(swatchRow(c, recent, 12, swatches, swatchColors, setFromPalette));
        }

        // böyük palitra
        col.addView(Ui.section(c, "Palitra"));
        final float[][] tones = {
                {0.18f, 1f}, {0.38f, 1f}, {0.6f, 1f}, {0.85f, 1f}, {1f, 0.82f}, {1f, 0.6f}, {1f, 0.38f}
        };
        for (float[] t : tones) {
            List<Integer> row = new ArrayList<>();
            for (int h = 0; h < 12; h++) row.add(Color.HSVToColor(new float[]{h * 30f, t[0], t[1]}));
            col.addView(swatchRow(c, row, 12, swatches, swatchColors, setFromPalette));
        }
        List<Integer> extra = new ArrayList<>();
        for (int h : new int[]{15, 45, 75, 105, 135, 165, 195, 225, 255, 285, 315, 345}) {
            extra.add(Color.HSVToColor(new float[]{h, 0.75f, 0.92f}));
        }
        col.addView(swatchRow(c, extra, 12, swatches, swatchColors, setFromPalette));
        List<Integer> grays = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            int v = Math.round(255f * i / 11f);
            grays.add(Color.rgb(v, v, v));
        }
        col.addView(swatchRow(c, grays, 12, swatches, swatchColors, setFromPalette));
        List<Integer> darks = new ArrayList<>();
        int[] dk = {0xFF121212, 0xFF1F1F1F, 0xFF2A2A2A, 0xFF3A3A3C, 0xFF1C2833, 0xFF17202A,
                0xFF1B2631, 0xFF0B3D2E, 0xFF3B1F2B, 0xFF2E1A47, 0xFF4A3B2A, 0xFF5D4037};
        for (int d : dk) darks.add(d);
        col.addView(swatchRow(c, darks, 12, swatches, swatchColors, setFromPalette));

        if (allowTransparent) {
            TextView tr = Ui.row(c, L.t("Şəffaf"));
            tr.setOnClickListener(v -> setFromPalette.on(TRANSPARENT));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
            tl.topMargin = Ui.dp(c, 10);
            col.addView(tr, tl);
        }

        hex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence a, int b, int d, int e) { }
            @Override public void onTextChanged(CharSequence a, int b, int d, int e) { }
            @Override
            public void afterTextChanged(Editable e) {
                if (!hex.hasFocus()) return;
                Integer v = parseHex(e.toString());
                if (v == null) return;
                typing[0] = true;
                color[0] = v;
                Color.colorToHSV(v, hsv);
                paint.run();
                typing[0] = false;
            }
        });

        int max = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.66f);
        s.add(sv, new LinearLayout.LayoutParams(-1, max));
        paint.run();
        s.button("Ləğv et", false, null);
        s.button("Seç", true, () -> {
            remember(prefs, color[0]);
            done.on(color[0]);
        });
        s.show();
    }

    private static void remember(SharedPreferences prefs, int color) {
        List<String> out = new ArrayList<>();
        out.add(Long.toString(color & 0xFFFFFFFFL));
        for (String x : prefs.getString("recent_colors", "").split(",")) {
            if (!x.isEmpty() && !out.contains(x) && out.size() < 12) out.add(x);
        }
        prefs.edit().putString("recent_colors", android.text.TextUtils.join(",", out)).apply();
    }

    static Integer parseHex(String s) {
        s = s.trim().replace("#", "");
        if (s.length() == 3) {
            s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        }
        if (s.length() != 6 && s.length() != 8) return null;
        try {
            long v = Long.parseLong(s, 16);
            return s.length() == 6 ? (int) (0xFF000000L | v) : (int) v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static View swatchRow(Context c, List<Integer> colors, int perRow, List<View> all,
                                  List<Integer> allColors, final Cb onPick) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < perRow; i++) {
            View v = new View(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 28), 1);
            lp.rightMargin = lp.bottomMargin = Ui.dp(c, 3);
            if (i < colors.size()) {
                final int col = colors.get(i);
                v.setOnClickListener(x -> onPick.on(col));
                all.add(v);
                allColors.add(col);
            } else {
                v.setVisibility(View.INVISIBLE);
            }
            row.addView(v, lp);
        }
        return row;
    }

    // ------------------------------------------------------------------ fərdi görünüşlər

    interface SV { void on(float s, float v); }
    interface H { void on(float h); }

    /** Doyğunluq (üfüqi) × parlaqlıq (şaquli) kvadratı. */
    static final class SVView extends View {
        SV cb;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float h = 0, sat = 1, val = 1;
        private float lastHue = -1;
        private int lastW, lastH;

        SVView(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Ui.dp(c, 2));
            setLayerType(LAYER_TYPE_SOFTWARE, null); // köhnə Android-də ComposeShader üçün
        }

        void setHsv(float[] hsv) {
            h = hsv[0];
            sat = hsv[1];
            val = hsv[2];
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth(), hh = getHeight();
            if (w == 0 || hh == 0) return;
            if (h != lastHue || w != lastW || hh != lastH) {
                lastHue = h;
                lastW = w;
                lastH = hh;
                Shader sx = new LinearGradient(0, 0, w, 0, Color.WHITE,
                        Color.HSVToColor(new float[]{h, 1f, 1f}), Shader.TileMode.CLAMP);
                Shader sy = new LinearGradient(0, 0, 0, hh, Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP);
                p.setShader(new ComposeShader(sx, sy, PorterDuff.Mode.MULTIPLY));
            }
            cv.drawRect(0, 0, w, hh, p);
            float x = sat * w, y = (1 - val) * hh;
            float r = Ui.dp(getContext(), 9);
            ring.setColor(val > 0.5f ? Color.BLACK : Color.WHITE);
            cv.drawCircle(x, y, r, ring);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            float s = Math.max(0, Math.min(1, e.getX() / Math.max(1, getWidth())));
            float v = 1 - Math.max(0, Math.min(1, e.getY() / Math.max(1, getHeight())));
            sat = s;
            val = v;
            if (cb != null) cb.on(s, v);
            invalidate();
            return true;
        }
    }

    /** Çalar (hue) zolağı. */
    static final class HueBar extends View {
        H cb;
        private final Paint p = new Paint();
        private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float hue;
        private int lastW;

        HueBar(Context c) {
            super(c);
            mark.setStyle(Paint.Style.STROKE);
            mark.setStrokeWidth(Ui.dp(c, 3));
            mark.setColor(Color.WHITE);
        }

        void setHue(float h) {
            hue = h;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            if (w == 0) return;
            if (w != lastW) {
                lastW = w;
                int[] cols = new int[13];
                for (int i = 0; i < 13; i++) cols[i] = Color.HSVToColor(new float[]{i * 30f % 360f, 1f, 1f});
                cols[12] = Color.HSVToColor(new float[]{359.9f, 1f, 1f});
                p.setShader(new LinearGradient(0, 0, w, 0, cols, null, Shader.TileMode.CLAMP));
            }
            cv.drawRect(0, 0, w, h, p);
            float x = hue / 360f * w;
            float half = Ui.dp(getContext(), 3);
            cv.drawRect(x - half, 1, x + half, h - 1, mark);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            float x = Math.max(0, Math.min(1, e.getX() / Math.max(1, getWidth())));
            hue = Math.min(359.9f, x * 360f);
            if (cb != null) cb.on(hue);
            invalidate();
            return true;
        }
    }

    private ColorPicker() { }
}
