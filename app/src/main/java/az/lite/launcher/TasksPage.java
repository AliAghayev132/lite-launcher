package az.lite.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Gündəlik səhifəsi: bu günün tapşırıqları (tamamlanmayanlar növbəti günə keçir,
 * "hər gün" olanlar hər səhər yenidən açılır) + sərbəst qeydlər.
 */
final class TasksPage {

    static final class Task {
        String text;
        boolean done;
        boolean daily;
        String day;      // yaradıldığı gün (yyyyMMdd)
        String doneDay;  // tamamlandığı gün
    }

    private final Context c;
    private final FrameLayout root;
    private final SharedPreferences prefs;
    private final List<Task> tasks = new ArrayList<>();
    private final Handler h = new Handler(Looper.getMainLooper());

    final ScrollView view;
    private final LinearLayout list;
    private final TextView dateView, countView, clearDone;
    private final EditText input, notes;
    private String loadedDay;

    private final Runnable saveNotes = new Runnable() {
        @Override public void run() {
            prefs.edit().putString("notes", notes.getText().toString()).apply();
        }
    };

    TasksPage(Context ctx, FrameLayout root) {
        this.c = ctx;
        this.root = root;
        prefs = ctx.getSharedPreferences("tasks", Context.MODE_PRIVATE);

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
        title.setText(L.t("Gündəlik"));
        title.setTextColor(Ui.TEXT);
        title.setTypeface(Ui.BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        col.addView(title);

        dateView = new TextView(c);
        dateView.setTextColor(Ui.ACCENT);
        dateView.setTypeface(Ui.MEDIUM);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        dateView.setPadding(0, dp(2), 0, dp(6));
        col.addView(dateView);

        // başlıq sətri: "Tapşırıqlar" + say
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        head.addView(Ui.section(c, "Tapşırıqlar"), new LinearLayout.LayoutParams(0, -2, 1));
        countView = new TextView(c);
        countView.setTextColor(Ui.TEXT2);
        countView.setTextSize(12);
        countView.setPadding(0, 0, 0, dp(8));
        head.addView(countView);
        col.addView(head);

        // yeni tapşırıq
        LinearLayout add = new LinearLayout(c);
        add.setOrientation(LinearLayout.HORIZONTAL);
        input = Ui.input(c, "Yeni tapşırıq…");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        add.addView(input, new LinearLayout.LayoutParams(0, dp(50), 1));
        TextView plus = Ui.button(c, "+", true);
        plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        plus.setOnClickListener(v -> addFromInput());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(50), dp(50));
        plp.leftMargin = dp(8);
        add.addView(plus, plp);
        col.addView(add);
        input.setOnEditorActionListener((v, id, ev) -> {
            boolean go = id == EditorInfo.IME_ACTION_DONE
                    || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN);
            if (go) {
                addFromInput();
                return true;
            }
            return false;
        });

        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, -2);
        llp.topMargin = dp(10);
        col.addView(list, llp);

        clearDone = new TextView(c);
        clearDone.setText(L.t("Tamamlananları təmizlə"));
        clearDone.setTextColor(Ui.TEXT2);
        clearDone.setTextSize(13);
        clearDone.setPadding(0, dp(10), 0, dp(4));
        clearDone.setOnClickListener(v -> {
            Iterator<Task> it = tasks.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (t.done && !t.daily) it.remove();
            }
            save();
            render();
        });
        col.addView(clearDone);

        // qeydlər
        col.addView(Ui.section(c, "Qeydlər"));
        notes = new EditText(c);
        notes.setHint(L.t("Bura yaz…"));
        notes.setTextColor(Ui.TEXT);
        notes.setHintTextColor(Ui.TEXT2);
        notes.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        notes.setGravity(Gravity.TOP | Gravity.START);
        notes.setMinLines(8);
        notes.setLineSpacing(dp(3), 1f);
        notes.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        notes.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        notes.setBackground(Ui.outline(Ui.SURFACE2, Ui.LINE, dp(1)));
        notes.setPadding(dp(14), dp(12), dp(14), dp(12));
        notes.setText(prefs.getString("notes", ""));
        notes.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable e) {
                h.removeCallbacks(saveNotes);
                h.postDelayed(saveNotes, 500);
            }
        });
        col.addView(notes, new LinearLayout.LayoutParams(-1, -2));

        load();
        refresh();
    }

    private int dp(int v) { return Ui.dp(c, v); }

    static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
    }

    private static String yesterday() {
        return new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date(System.currentTimeMillis() - 86400000L));
    }

    /** Səhifə göstəriləndə / gün dəyişəndə çağırılır. */
    void refresh() {
        String d = today();
        if (!d.equals(loadedDay)) newDay(d);
        String s = new SimpleDateFormat("EEEE, d MMMM", L.locale()).format(new Date());
        if (!s.isEmpty()) s = s.substring(0, 1).toUpperCase(L.locale()) + s.substring(1);
        dateView.setText(s);
        render();
    }

    /** Yeni gün: dünən tamamlananlar gizlənir, "hər gün" tapşırıqları yenidən açılır. */
    private void newDay(String d) {
        loadedDay = d;
        boolean changed = false;
        Iterator<Task> it = tasks.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.daily) {
                if (t.done && !d.equals(t.doneDay)) {
                    t.done = false;
                    changed = true;
                }
            } else if (t.done && !d.equals(t.doneDay)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) save();
    }

    void onPause() {
        h.removeCallbacks(saveNotes);
        saveNotes.run();
    }

    private void addFromInput() {
        String s = input.getText().toString().trim();
        if (s.isEmpty()) return;
        Task t = new Task();
        t.text = s;
        t.day = today();
        tasks.add(t);
        input.setText("");
        save();
        render();
    }

    private void render() {
        list.removeAllViews();
        String td = today(), yd = yesterday();
        int done = 0, total = 0;
        List<Task> ordered = new ArrayList<>();
        for (Task t : tasks) if (!t.done) ordered.add(t);
        for (Task t : tasks) if (t.done) ordered.add(t);
        for (final Task t : ordered) {
            total++;
            if (t.done) done++;
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(50));
            row.setPadding(dp(14), dp(8), dp(14), dp(8));
            row.setBackground(Ui.pressable(Ui.SURFACE2, Ui.SURFACE3));
            row.setClickable(true);

            View box = new View(c);
            box.setBackground(t.done ? Ui.solid(Ui.ACCENT) : Ui.outline(0, Ui.TEXT2, dp(2)));
            row.addView(box, new LinearLayout.LayoutParams(dp(18), dp(18)));

            TextView tv = new TextView(c);
            tv.setText(t.text);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tv.setTypeface(Ui.MEDIUM);
            tv.setTextColor(t.done ? Ui.TEXT2 : Ui.TEXT);
            tv.setPaintFlags(t.done ? tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                    : tv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1);
            tlp.leftMargin = dp(14);
            row.addView(tv, tlp);

            String tag = null;
            if (t.daily) tag = L.t("hər gün");
            else if (!t.done && t.day != null && !td.equals(t.day)) tag = L.t(yd.equals(t.day) ? "dünəndən" : "köhnə");
            if (tag != null) {
                TextView tg = new TextView(c);
                tg.setText(tag);
                tg.setTextSize(11);
                tg.setTextColor(t.daily ? Ui.ACCENT : 0xFFFF9F0A);
                tg.setPadding(dp(8), 0, 0, 0);
                row.addView(tg);
            }

            row.setOnClickListener(v -> {
                t.done = !t.done;
                t.doneDay = t.done ? today() : null;
                save();
                render();
            });
            row.setOnLongClickListener(v -> {
                menu(t);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(4);
            list.addView(row, lp);
        }
        if (total == 0) {
            TextView e = new TextView(c);
            e.setText(L.t("Hələ tapşırıq yoxdur"));
            e.setTextColor(Ui.TEXT2);
            e.setTextSize(14);
            e.setPadding(0, dp(6), 0, dp(6));
            list.addView(e);
        } else if (done == total) {
            TextView e = new TextView(c);
            e.setText(L.t("Hamısı tamamlandı 🎉"));
            e.setTextColor(Ui.ACCENT);
            e.setTextSize(14);
            e.setPadding(0, dp(6), 0, 0);
            list.addView(e);
        }
        countView.setText(total == 0 ? "" : done + " / " + total);
        boolean anyClear = false;
        for (Task t : tasks) if (t.done && !t.daily) anyClear = true;
        clearDone.setVisibility(anyClear ? View.VISIBLE : View.GONE);
    }

    private void menu(final Task t) {
        String rep = t.daily ? "Təkrarı söndür" : "Hər gün təkrarla";
        Sheet.menu(root, t.text, new String[]{"Redaktə et", rep, "Yuxarı", "Aşağı", "!Sil"}, i -> {
            int idx = tasks.indexOf(t);
            if (i == 0) {
                edit(t);
                return;
            } else if (i == 1) {
                t.daily = !t.daily;
            } else if (i == 2 || i == 3) {
                int to = i == 2 ? idx - 1 : idx + 1;
                if (to < 0 || to >= tasks.size()) return;
                tasks.remove(idx);
                tasks.add(to, t);
            } else {
                tasks.remove(t);
            }
            save();
            render();
        });
    }

    private void edit(final Task t) {
        final EditText e = Ui.input(c, "Yeni tapşırıq…");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setText(t.text);
        e.setSelection(e.length());
        new Sheet(root, L.t("Redaktə et")).add(e)
                .button("Ləğv et", false, null)
                .button("Yadda saxla", true, () -> {
                    String s = e.getText().toString().trim();
                    if (s.isEmpty()) return;
                    t.text = s;
                    save();
                    render();
                }).show();
        e.requestFocus();
    }

    private void load() {
        tasks.clear();
        try {
            JSONArray a = new JSONArray(prefs.getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Task t = new Task();
                t.text = o.optString("x");
                t.done = o.optBoolean("d");
                t.daily = o.optBoolean("r");
                t.day = o.optString("c", today());
                t.doneDay = o.has("dd") ? o.optString("dd") : null;
                tasks.add(t);
            }
        } catch (Exception ignored) {
        }
    }

    private void save() {
        JSONArray a = new JSONArray();
        try {
            for (Task t : tasks) {
                JSONObject o = new JSONObject();
                o.put("x", t.text);
                if (t.done) o.put("d", true);
                if (t.daily) o.put("r", true);
                o.put("c", t.day);
                if (t.doneDay != null) o.put("dd", t.doneDay);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs.edit().putString("list", a.toString()).apply();
    }
}
