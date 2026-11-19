package az.lite.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Şəkillər səhifəsi: şəkil seç → istədiyin nisbətdə kəs → ölçüsünü seç (tam en, 2/3, yarım, 1/3)
 * → səhifəyə yerləşir. Şəkillər sətir-sətir düzülür, çox olsa aşağı sürüşür.
 */
final class PhotosPage {

    static final class Photo {
        String id;
        float width = 1f;      // ekran eninin hissəsi
        float aspect = 1f;     // en / hündürlük
        RectF crop;            // mənbədə normallaşdırılmış kəsik
    }

    interface Picker { void pick(); }

    private static final float[] SIZES = {1f, 2f / 3f, 0.5f, 1f / 3f};
    private static final String[] SIZE_NAMES = {"Tam en", "Üçdə iki", "Yarım", "Üçdə bir"};
    private static final float[] ASPECTS = {0f, 1f, 4f / 3f, 3f / 4f, 16f / 9f, 9f / 16f, 3f / 2f, 2f / 3f};
    private static final String[] ASPECT_NAMES = {"Sərbəst", "1:1", "4:3", "3:4", "16:9", "9:16", "3:2", "2:3"};

    private final Context c;
    private final FrameLayout root;
    private final Picker picker;
    private final SharedPreferences prefs;
    private final File dir;
    private final List<Photo> photos = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache;

    final ScrollView view;
    private final Flow flow;
    private final TextView hint;

    PhotosPage(Context ctx, FrameLayout root, Picker picker) {
        this.c = ctx;
        this.root = root;
        this.picker = picker;
        prefs = ctx.getSharedPreferences("photos", Context.MODE_PRIVATE);
        dir = new File(ctx.getFilesDir(), "photos");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        cache = new LruCache<String, Bitmap>(maxKb) {
            @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount() / 1024; }
        };

        view = new ScrollView(c);
        view.setBackgroundColor(0xB3000000);
        view.setFillViewport(true);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(20), dp(14), dp(60));
        view.addView(col);

        TextView title = new TextView(c);
        title.setText(L.t("Şəkillər"));
        title.setTextColor(Ui.TEXT);
        title.setTypeface(Ui.BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setPadding(0, 0, 0, dp(14));
        col.addView(title);

        flow = new Flow(c, dp(8));
        col.addView(flow, new LinearLayout.LayoutParams(-1, -2));

        hint = new TextView(c);
        hint.setText(L.t("Hələ şəkil yoxdur. Əlavə et, istədiyin ölçüdə kəs, yerləşdir."));
        hint.setTextColor(Ui.TEXT2);
        hint.setTextSize(14);
        hint.setPadding(0, dp(8), 0, dp(16));
        col.addView(hint);

        TextView add = new TextView(c);
        add.setText(L.t("+  ŞƏKİL ƏLAVƏ ET"));
        add.setTextColor(Ui.ON_ACCENT);
        add.setTypeface(Ui.BOLD);
        add.setTextSize(13);
        add.setLetterSpacing(0.06f);
        add.setGravity(Gravity.CENTER);
        add.setBackground(Ui.pressable(Ui.ACCENT, 0xFFE0BC00));
        add.setOnClickListener(v -> picker.pick());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(48));
        alp.topMargin = dp(12);
        col.addView(add, alp);

        load();
        render();
    }

    private int dp(int v) { return Ui.dp(c, v); }

    // ------------------------------------------------------------------ yeni şəkil

    void onPicked(final Uri uri) {
        worker.execute(() -> {
            final Bitmap b = decode(uri);
            ui.post(() -> {
                if (b == null) {
                    Ui.toast(root, L.t("Şəkil açılmadı"));
                    return;
                }
                cropSheet(b, null, null);
            });
        });
    }

    /** Uri-dən en çox ~1600px-lik bitmap (EXIF fırlanması nəzərə alınır). */
    private Bitmap decode(Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            if (o.outWidth <= 0) return null;
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= 1600) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap b;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                b = BitmapFactory.decodeStream(in, null, o2);
            }
            if (b == null) return null;
            int rot = 0;
            if (Build.VERSION.SDK_INT >= 24) {
                try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                    android.media.ExifInterface ex = new android.media.ExifInterface(in);
                    int or = ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
                    if (or == 6) rot = 90;
                    else if (or == 3) rot = 180;
                    else if (or == 8) rot = 270;
                } catch (Exception ignored) { }
            }
            int max = Math.max(b.getWidth(), b.getHeight());
            float sc = max > 1600 ? 1600f / max : 1f;
            if (rot != 0 || sc < 1f) {
                Matrix m = new Matrix();
                m.postScale(sc, sc);
                m.postRotate(rot);
                Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (r != b) b.recycle();
                b = r;
            }
            return b;
        } catch (Throwable e) {
            return null;
        }
    }

    /** Kəsmə paneli. existing == null → yeni şəkil. */
    private void cropSheet(Bitmap src, final Photo existing, RectF initial) {
        final Sheet s = new Sheet(root, L.t("Kəs"));
        final CropView cv = new CropView(c);
        int h = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.46f);
        s.add(cv, new LinearLayout.LayoutParams(-1, h));
        cv.setBitmap(src, initial);

        final float[] size = {existing != null ? existing.width : 1f};
        final List<TextView> aspectChips = new ArrayList<>();
        final List<TextView> sizeChips = new ArrayList<>();

        s.add(chipRow(ASPECT_NAMES, aspectChips, i -> {
            cv.setAspect(ASPECTS[i]);
            select(aspectChips, i);
        }));
        select(aspectChips, 0);
        s.add(Ui.section(c, "Ölçü"));
        s.add(chipRow(SIZE_NAMES, sizeChips, i -> {
            size[0] = SIZES[i];
            select(sizeChips, i);
        }));
        int si = 0;
        for (int i = 0; i < SIZES.length; i++) if (Math.abs(SIZES[i] - size[0]) < 0.01f) si = i;
        select(sizeChips, si);

        s.onDismiss(() -> { /* bitmap GC-yə qalır */ });
        s.button("Ləğv et", false, null);
        s.button("Fırlat", false, true, () -> {
            Bitmap b = cv.bitmap();
            Matrix m = new Matrix();
            m.postRotate(90);
            Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            cv.setBitmap(r, null);
        });
        s.button("Hazır", true, () -> {
            final Bitmap b = cv.bitmap();
            final RectF px = new RectF(cv.crop);
            final RectF norm = cv.normalized();
            final Photo p = existing != null ? existing : new Photo();
            if (p.id == null) p.id = "p" + System.currentTimeMillis();
            p.width = size[0];
            p.crop = norm;
            p.aspect = Math.max(0.05f, px.width() / Math.max(1f, px.height()));
            worker.execute(() -> {
                boolean ok = saveFiles(p, b, px);
                ui.post(() -> {
                    if (!ok) {
                        Ui.toast(root, L.t("Şəkil açılmadı"));
                        return;
                    }
                    cache.remove(p.id);
                    if (!photos.contains(p)) photos.add(p);
                    save();
                    render();
                    view.post(() -> {
                        if (existing == null) view.fullScroll(View.FOCUS_DOWN);
                    });
                });
            });
        });
        s.show();
    }

    private boolean saveFiles(Photo p, Bitmap src, RectF px) {
        try {
            try (FileOutputStream o = new FileOutputStream(new File(dir, p.id + "_src.jpg"))) {
                src.compress(Bitmap.CompressFormat.JPEG, 90, o);
            }
            int l = Math.max(0, Math.round(px.left)), t = Math.max(0, Math.round(px.top));
            int w = Math.min(src.getWidth() - l, Math.round(px.width()));
            int h = Math.min(src.getHeight() - t, Math.round(px.height()));
            if (w < 1 || h < 1) return false;
            Bitmap cut = Bitmap.createBitmap(src, l, t, w, h);
            if (w > 1080) {
                Bitmap sc = Bitmap.createScaledBitmap(cut, 1080, Math.max(1, Math.round(h * 1080f / w)), true);
                if (sc != cut) cut.recycle();
                cut = sc;
            }
            try (FileOutputStream o = new FileOutputStream(new File(dir, p.id + ".jpg"))) {
                cut.compress(Bitmap.CompressFormat.JPEG, 90, o);
            }
            cut.recycle();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    interface IntCb { void on(int i); }

    private View chipRow(String[] names, List<TextView> out, IntCb onPick) {
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        hs.addView(row);
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView t = new TextView(c);
            t.setText(L.t(names[i]));
            t.setTypeface(Ui.MEDIUM);
            t.setTextSize(14);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(dp(38));
            t.setPadding(dp(14), 0, dp(14), 0);
            t.setOnClickListener(v -> onPick.on(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(6);
            row.addView(t, lp);
            out.add(t);
        }
        return hs;
    }

    private void select(List<TextView> chips, int sel) {
        for (int i = 0; i < chips.size(); i++) {
            TextView t = chips.get(i);
            boolean on = i == sel;
            t.setBackground(on ? Ui.solid(Ui.ACCENT) : Ui.pressable(Ui.SURFACE2, Ui.SURFACE3));
            t.setTextColor(on ? Ui.ON_ACCENT : Ui.TEXT);
        }
    }

    // ------------------------------------------------------------------ göstərmək

    private void render() {
        flow.removeAllViews();
        for (final Photo p : photos) {
            final ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(Ui.SURFACE2);
            iv.setLayoutParams(new Flow.LP(p.width, p.aspect));
            iv.setOnClickListener(v -> viewFull(p));
            iv.setOnLongClickListener(v -> {
                menu(p);
                return true;
            });
            flow.addView(iv);
            Bitmap b = cache.get(p.id);
            if (b != null) {
                iv.setImageBitmap(b);
            } else {
                final int target = (int) (c.getResources().getDisplayMetrics().widthPixels * p.width);
                worker.execute(() -> {
                    final Bitmap bm = load(p.id, target);
                    ui.post(() -> {
                        if (bm == null) return;
                        cache.put(p.id, bm);
                        iv.setImageBitmap(bm);
                    });
                });
            }
        }
        hint.setVisibility(photos.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private Bitmap load(String id, int targetW) {
        File f = new File(dir, id + ".jpg");
        if (!f.exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= Math.max(1, targetW)) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        try {
            return BitmapFactory.decodeFile(f.getPath(), o2);
        } catch (Throwable e) {
            return null;
        }
    }

    private void viewFull(Photo p) {
        final Sheet s = new Sheet(root, null);
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setAdjustViewBounds(true);
        Bitmap b = cache.get(p.id);
        if (b != null) iv.setImageBitmap(b);
        iv.setOnClickListener(v -> s.dismiss());
        int max = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.75f);
        iv.setMaxHeight(max);
        s.add(iv, new LinearLayout.LayoutParams(-1, -2));
        s.show();
    }

    private void menu(final Photo p) {
        Sheet.menu(root, L.t("Şəkil"), new String[]{"Ölçü", "Yenidən kəs", "Əvvələ", "Sonraya", "!Sil"}, i -> {
            int idx = photos.indexOf(p);
            if (i == 0) {
                Sheet.menu(root, L.t("Ölçü"), SIZE_NAMES, k -> {
                    p.width = SIZES[k];
                    cache.remove(p.id);
                    save();
                    render();
                });
                return;
            } else if (i == 1) {
                recrop(p);
                return;
            } else if (i == 2 || i == 3) {
                int to = i == 2 ? idx - 1 : idx + 1;
                if (to < 0 || to >= photos.size()) return;
                photos.remove(idx);
                photos.add(to, p);
            } else {
                photos.remove(p);
                cache.remove(p.id);
                //noinspection ResultOfMethodCallIgnored
                new File(dir, p.id + ".jpg").delete();
                //noinspection ResultOfMethodCallIgnored
                new File(dir, p.id + "_src.jpg").delete();
            }
            save();
            render();
        });
    }

    private void recrop(final Photo p) {
        worker.execute(() -> {
            File f = new File(dir, p.id + "_src.jpg");
            Bitmap b = null;
            try { b = BitmapFactory.decodeFile(f.getPath()); } catch (Throwable ignored) { }
            final Bitmap fb = b;
            ui.post(() -> {
                if (fb == null) {
                    Ui.toast(root, L.t("Şəkil açılmadı"));
                    return;
                }
                cropSheet(fb, p, p.crop);
            });
        });
    }

    // ------------------------------------------------------------------ yaddaş

    private void load() {
        photos.clear();
        try {
            JSONArray a = new JSONArray(prefs.getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Photo p = new Photo();
                p.id = o.getString("id");
                p.width = (float) o.optDouble("w", 1);
                p.aspect = (float) o.optDouble("a", 1);
                JSONArray r = o.optJSONArray("r");
                if (r != null && r.length() == 4) {
                    p.crop = new RectF((float) r.getDouble(0), (float) r.getDouble(1),
                            (float) r.getDouble(2), (float) r.getDouble(3));
                }
                photos.add(p);
            }
        } catch (Exception ignored) {
        }
    }

    private void save() {
        JSONArray a = new JSONArray();
        try {
            for (Photo p : photos) {
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("w", p.width);
                o.put("a", p.aspect);
                if (p.crop != null) {
                    JSONArray r = new JSONArray();
                    r.put(p.crop.left).put(p.crop.top).put(p.crop.right).put(p.crop.bottom);
                    o.put("r", r);
                }
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs.edit().putString("list", a.toString()).apply();
    }

    void trim() { cache.evictAll(); }

    // ------------------------------------------------------------------ düzülüş

    /** Uşaqları sətir-sətir düzür: hər biri eninin hissəsi (LP.frac) və nisbəti (LP.aspect) ilə. */
    static final class Flow extends ViewGroup {
        static final class LP extends ViewGroup.LayoutParams {
            final float frac, aspect;
            LP(float frac, float aspect) {
                super(-1, -2);
                this.frac = frac;
                this.aspect = aspect;
            }
        }

        private final int gap;

        Flow(Context c, int gap) {
            super(c);
            this.gap = gap;
        }

        private int childW(LP lp, int w) {
            return Math.max(1, Math.round(lp.frac * (w + gap) - gap));
        }

        @Override
        protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws);
            float used = 0;
            int y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                LP lp = (LP) v.getLayoutParams();
                if (used > 0 && used + lp.frac > 1.01f) {
                    y += rowH + gap;
                    used = 0;
                    rowH = 0;
                }
                int cw = childW(lp, w);
                int ch = Math.max(1, Math.round(cw / lp.aspect));
                v.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
                rowH = Math.max(rowH, ch);
                used += lp.frac;
            }
            setMeasuredDimension(w, getChildCount() == 0 ? 0 : y + rowH);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int w = r - l;
            float used = 0;
            int x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                LP lp = (LP) v.getLayoutParams();
                if (used > 0 && used + lp.frac > 1.01f) {
                    y += rowH + gap;
                    used = 0;
                    rowH = 0;
                    x = 0;
                }
                v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
                x += v.getMeasuredWidth() + gap;
                rowH = Math.max(rowH, v.getMeasuredHeight());
                used += lp.frac;
            }
        }

        @Override
        protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof LP; }

        @Override
        protected ViewGroup.LayoutParams generateDefaultLayoutParams() { return new LP(1f, 1f); }
    }
}
