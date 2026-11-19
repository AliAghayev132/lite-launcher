package az.lite.launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lite Launcher — 3 səhifə:
 *  0) vidcetlər   1) ana ekran (sərbəst xanalar, qovluqlar)   2) axtarış + bütün tətbiqlər
 * Native dialog/menyu yoxdur — hamısı öz Sheet panellərimizdir. İnternet icazəsi yoxdur.
 */
public class MainActivity extends Activity implements Pager.Listener, HomeGrid.Callbacks {

    static final class App {
        final String label;
        final String search;
        final ComponentName component;
        final String key;

        App(String label, ComponentName component) {
            this.label = label;
            this.search = label.toLowerCase(Locale.ROOT);
            this.component = component;
            this.key = component.flattenToString();
        }
    }

    static final String P_TASKS = "tasks", P_PHOTOS = "photos", P_WIDGETS = "widgets",
            P_HOME = "home", P_APPS = "apps";
    private static final String[] ALL_PAGES = {P_TASKS, P_PHOTOS, P_WIDGETS, P_HOME, P_APPS};
    private static final int REQ_BIND = 11, REQ_CONFIG = 12, REQ_PHOTO = 13;
    private static final int HOST_ID = 0x4C4C;


    private final List<App> allApps = new ArrayList<>();
    private final List<App> shownApps = new ArrayList<>();
    private final Map<String, App> appsByKey = new HashMap<>();
    private final LruCache<String, Drawable> iconCache = new LruCache<>(300);
    private final Set<String> pendingIcons = new HashSet<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService iconWorker = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());

    private Store store;
    private Usage usage;
    private TextView calcResult;
    private View topApps;
    private LinearLayout topRow;
    private String calcValue;
    private FrameLayout root;
    private Pager pager;
    private HomeGrid homeGrid;
    private GridView appGrid;
    private EditText search;
    private TextView homeHint, widgetHint;
    private LinearLayout widgetList;
    private LinearLayout dotsBar;
    /** Açıq səhifələr (soldan sağa). */
    private final List<String> pages = new ArrayList<>();
    private final Map<String, View> pageViews = new HashMap<>();
    private TasksPage tasksPage;
    private PhotosPage photosPage;
    private android.content.SharedPreferences settings;
    private BaseAdapter appAdapter, folderAdapter;

    private AppWidgetHost widgetHost;
    private AppWidgetManager widgetManager;
    private int pendingWidgetId = -1;

    private boolean refreshPosted, needsReload;
    private int loadGeneration;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            iconCache.evictAll();
            if (hasWindowFocus()) loadApps(); else needsReload = true;
        }
    };

    // ================================================================ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        L.init(this);
        settings = getSharedPreferences("settings", MODE_PRIVATE);
        pendingWidgetId = settings.getInt("pending_widget", -1);
        setContentView(R.layout.activity_main);
        store = new Store(this);
        usage = new Usage(this);

        root = findViewById(R.id.root);
        pager = findViewById(R.id.pager);
        homeGrid = findViewById(R.id.home_grid);
        appGrid = findViewById(R.id.app_grid);
        search = findViewById(R.id.search);
        calcResult = findViewById(R.id.calc_result);
        topApps = findViewById(R.id.top_apps);
        topRow = findViewById(R.id.top_row);
        homeHint = findViewById(R.id.home_hint);
        widgetHint = findViewById(R.id.widget_hint);
        widgetList = findViewById(R.id.widget_list);
        dotsBar = findViewById(R.id.dots);
        applyTexts();

        pager.setListener(this);
        homeGrid.setCallbacks(this);
        store.reserved = homeGrid.reserved;
        homeGrid.setQuickView(makeQuickView());
        homeGrid.setItems(store.items);
        setupDrawer();
        setupWidgets();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);

        tasksPage = new TasksPage(this, root);
        photosPage = new PhotosPage(this, root, this::pickPhoto);
        pageViews.put(P_WIDGETS, findViewById(R.id.page_widgets));
        pageViews.put(P_HOME, findViewById(R.id.page_home));
        pageViews.put(P_APPS, findViewById(R.id.page_apps));
        pageViews.put(P_TASKS, tasksPage.view);
        pageViews.put(P_PHOTOS, photosPage.view);
        applyPages(P_HOME, false);

        updateHint();
        loadApps();
    }

    /** XML-dəki mətnləri seçilmiş dilə çevirir. */
    private void applyTexts() {
        ((TextView) findViewById(R.id.widgets_title)).setText(L.t("Vidcetlər"));
        widgetHint.setText(L.t("Hələ vidcet yoxdur. Vidcetə uzun bas → sil və ya yerini dəyiş."));
        ((TextView) findViewById(R.id.widget_add)).setText(L.t("+  VİDCET ƏLAVƏ ET"));
        homeHint.setText(L.t("Boş xanaya uzun bas → qovluq və ya tətbiq\nSağa sürüşdür → vidcetlər\nSola sürüşdür → bütün tətbiqlər"));
        search.setHint(L.t("Axtar…"));
        ((TextView) findViewById(R.id.top_label)).setText(L.t("ƏN ÇOX AÇILANLAR"));
    }

    // ================================================================ səhifələr

    private List<String> pageOrder() {
        List<String> out = new ArrayList<>();
        for (String x : settings.getString("page_order", "").split(",")) {
            for (String id : ALL_PAGES) if (id.equals(x) && !out.contains(x)) out.add(x);
        }
        for (String id : ALL_PAGES) if (!out.contains(id)) out.add(id);
        return out;
    }

    private Set<String> pagesOff() {
        Set<String> off = new HashSet<>();
        for (String x : settings.getString("page_off", "").split(",")) if (!x.isEmpty()) off.add(x);
        off.remove(P_HOME);
        return off;
    }

    private static String pageName(String id) {
        switch (id) {
            case P_TASKS: return L.t("Gündəlik");
            case P_PHOTOS: return L.t("Şəkillər");
            case P_WIDGETS: return L.t("Vidcetlər");
            case P_APPS: return L.t("Bütün tətbiqlər");
            default: return L.t("Ana ekran");
        }
    }

    /** Səhifələri sıraya görə Pager-ə qoyur; keep → hansı səhifədə qalsın. */
    private void applyPages(String keep, boolean animate) {
        Set<String> off = pagesOff();
        pages.clear();
        for (String id : pageOrder()) if (!off.contains(id)) pages.add(id);
        pager.removeAllViews();
        for (String id : pages) {
            View v = pageViews.get(id);
            if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
            pager.addView(v, new ViewGroup.LayoutParams(-1, -1));
        }
        dotsBar.removeAllViews();
        for (int i = 0; i < pages.size(); i++) {
            View d = new View(this);
            d.setBackgroundResource(R.drawable.dot);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(6), dp(6));
            if (i > 0) lp.leftMargin = dp(6);
            dotsBar.addView(d, lp);
        }
        int idx = pages.indexOf(keep);
        if (idx < 0) idx = pages.indexOf(P_HOME);
        pager.setPage(idx, animate);
        onPageChanged(idx);
    }

    private int pidx(String id) { return pages.indexOf(id); }

    private String currentPage() {
        int p = pager.getPage();
        return p >= 0 && p < pages.size() ? pages.get(p) : P_HOME;
    }

    /** Səhifəyə keç; bağlıdırsa əvvəlcə aç. */
    private void goTo(String id) {
        if (pidx(id) < 0) {
            Set<String> off = pagesOff();
            off.remove(id);
            settings.edit().putString("page_off", android.text.TextUtils.join(",", off)).apply();
            applyPages(currentPage(), false);
        }
        pager.setPage(pidx(id), true);
    }

    private void pickPhoto() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, L.t("Şəkil")), REQ_PHOTO);
        } catch (Exception e) {
            Ui.toast(root, "Şəkil seçmək üçün tətbiq yoxdur");
        }
    }

    // ================================================================ kömək

    /** Lite Launcher ikonuna basanda: nə etmək olar — qısa bələdçi. */
    private void showHelp() {
        final Sheet s = new Sheet(root, "Lite Launcher");
        ScrollView sv = new ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);
        for (String[] sec : Help.sections()) {
            col.addView(Ui.section(this, sec[0]));
            TextView t = new TextView(this);
            t.setText(sec[1]);
            t.setTextColor(Ui.TEXT);
            t.setTextSize(14);
            t.setLineSpacing(dp(3), 1f);
            col.addView(t);
        }
        int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.68f);
        s.add(sv, new LinearLayout.LayoutParams(-1, max));
        s.button("Parametrlər", false, this::openSettings);
        s.button("Bağla", true, null).show();
    }

    // ================================================================ parametrlər

    private void openSettings() {
        final Sheet s = new Sheet(root, L.t("Parametrlər"));
        s.add(Ui.section(this, "Dil"));
        LinearLayout langs = new LinearLayout(this);
        langs.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < L.CODES.length; i++) {
            final String code = L.CODES[i];
            boolean on = code.equals(L.lang());
            TextView t = new TextView(this);
            t.setText(L.NAMES[i]);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(Ui.MEDIUM);
            t.setTextSize(14);
            t.setMinHeight(dp(46));
            t.setTextColor(on ? Ui.ON_ACCENT : Ui.TEXT);
            t.setBackground(on ? Ui.solid(Ui.ACCENT) : Ui.pressable(Ui.SURFACE2, Ui.SURFACE3));
            t.setOnClickListener(v -> {
                if (code.equals(L.lang())) return;
                L.set(this, code);
                Sheet.closeAll();
                recreate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.leftMargin = dp(6);
            langs.addView(t, lp);
        }
        s.add(langs);

        s.add(Ui.section(this, "Səhifələr"));
        TextView note = new TextView(this);
        note.setText(L.t("Səhifələri aç/bağla və sırasını dəyiş. Ana ekran həmişə açıqdır."));
        note.setTextColor(Ui.TEXT2);
        note.setTextSize(12);
        note.setPadding(0, 0, 0, dp(8));
        s.add(note);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        s.add(list);
        renderPageRows(list);
        s.button("Bağla", true, null).show();
    }

    private void renderPageRows(final LinearLayout list) {
        list.removeAllViews();
        final List<String> order = pageOrder();
        final Set<String> off = pagesOff();
        for (int i = 0; i < order.size(); i++) {
            final String id = order.get(i);
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(Ui.SURFACE2);

            boolean on = !off.contains(id);
            TextView name = Ui.row(this, pageName(id));
            name.setBackground(Ui.pressable(Ui.SURFACE2, Ui.SURFACE3));
            name.setTextColor(on ? Ui.TEXT : Ui.TEXT2);
            name.setCompoundDrawables(Ui.checkbox(this, on), null, null, null);
            name.setCompoundDrawablePadding(dp(14));
            name.setOnClickListener(v -> {
                if (P_HOME.equals(id)) return;
                Set<String> o = pagesOff();
                if (!o.remove(id)) o.add(id);
                settings.edit().putString("page_off", android.text.TextUtils.join(",", o)).apply();
                applyPages(currentPage(), false);
                renderPageRows(list);
            });
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

            String[] arrows = {"«", "‹", "›", "»"};
            for (int a = 0; a < arrows.length; a++) {
                final int which = a;
                TextView b = new TextView(this);
                b.setText(arrows[a]);
                b.setGravity(Gravity.CENTER);
                b.setTextSize(20);
                b.setTextColor(Ui.TEXT);
                b.setBackground(Ui.pressable(Ui.SURFACE3, Ui.LINE));
                b.setOnClickListener(v -> {
                    List<String> o = pageOrder();
                    o.remove(id);
                    int to;
                    if (which == 0) to = 0;
                    else if (which == 1) to = Math.max(0, idx - 1);
                    else if (which == 2) to = Math.min(o.size(), idx + 1);
                    else to = o.size();
                    o.add(to, id);
                    settings.edit().putString("page_order", android.text.TextUtils.join(",", o)).apply();
                    applyPages(currentPage(), false);
                    renderPageRows(list);
                });
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(38), dp(50));
                blp.leftMargin = dp(2);
                row.addView(b, blp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(4);
            list.addView(row, lp);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        try { widgetHost.startListening(); } catch (Exception ignored) { }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try { widgetHost.stopListening(); } catch (Exception ignored) { }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tasksPage != null) tasksPage.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("pending_widget", pendingWidgetId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tasksPage != null) tasksPage.refresh();
        if (needsReload) {
            needsReload = false;
            loadApps();
        }
    }

    /** Home düyməsi: ana ekrana qayıt. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Sheet.closeAll();
        homeGrid.cancelDrag();
        pager.setPage(pidx(P_HOME), true);
        resetSearch();
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        if (Sheet.closeTop()) return;
        if (!P_HOME.equals(currentPage())) pager.setPage(pidx(P_HOME), true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(packageReceiver); } catch (Exception ignored) { }
        worker.shutdownNow();
        iconWorker.shutdownNow();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND) {
            iconCache.evictAll();
            if (photosPage != null) photosPage.trim();
        }
    }

    @Override
    public void onPageChanged(int page) {
        for (int i = 0; i < dotsBar.getChildCount(); i++) {
            View d = dotsBar.getChildAt(i);
            d.setAlpha(i == page ? 1f : 0.35f);
            ViewGroup.LayoutParams lp = d.getLayoutParams();
            lp.width = dp(i == page ? 14 : 6);
            d.setLayoutParams(lp);
        }
        String id = page >= 0 && page < pages.size() ? pages.get(page) : P_HOME;
        if (!P_APPS.equals(id)) resetSearch();
        else updateTopApps(search.length() == 0);
        if (!P_HOME.equals(id)) homeGrid.cancelDrag();
        if (P_TASKS.equals(id) && tasksPage != null) tasksPage.refresh();
        if (!P_TASKS.equals(id) && !P_APPS.equals(id)) hideKeyboard();
    }

    // ================================================================ ana ekran (HomeGrid callbacks)

    @Override
    public View createItemView(ViewGroup parent) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_app, parent, false);
        Holder h = new Holder();
        h.icon = v.findViewById(R.id.icon);
        h.label = v.findViewById(R.id.label);
        v.setTag(R.id.icon, h);
        return v;
    }

    @Override
    public void bindItemView(View v, Store.Item it) {
        if (it.folder) bindFolder(v, it); else bindApp(v, it.app);
    }

    @Override
    public void onItemClick(Store.Item it) {
        if (it.folder) openFolder(it); else launchKey(it.app);
    }

    @Override
    public void onItemMenu(Store.Item it) {
        if (it.folder) {
            Sheet.menu(root, it.name, new String[]{"Redaktə et", "Tətbiq əlavə et", "!Qovluğu sil"}, i -> {
                if (i == 0) editFolder(it, null, null, -1);
                else if (i == 1) pickApps(it);
                else confirmDeleteFolder(it);
            });
        } else {
            App a = appsByKey.get(it.app);
            Sheet.menu(root, a != null ? a.label : L.t("Tətbiq"),
                    new String[]{"Qovluğa köçür", "Tətbiq məlumatı", "!Ana ekrandan götür", "!Telefondan sil"}, i -> {
                        if (i == 0) chooseFolder(it.app);
                        else if (i == 1) openAppInfo(it.app);
                        else if (i == 3) uninstall(it.app);
                        else {
                            store.items.remove(it);
                            saveAndRebuild();
                        }
                    });
        }
    }

    @Override
    public void onEmptyLongPress(int cell) {
        Sheet.menu(root, L.t("Boş xana"), new String[]{"Yeni qovluq", "Tətbiq qoy", "Vidcetlər",
                "Hamısını qovluqlara böl", "Parametrlər", "Kömək"}, i -> {
            if (i == 0) editFolder(null, null, null, cell);
            else if (i == 1) pickOneApp(cell);
            else if (i == 2) goTo(P_WIDGETS);
            else if (i == 3) autoGroup();
            else if (i == 4) openSettings();
            else showHelp();
        });
    }

    @Override
    public void onItemsMoved() {
        store.save();
    }

    private void confirmDeleteFolder(final Store.Item f) {
        Sheet.confirm(root, "\"" + f.name + "\"" + L.t(" silinsin?"),
                L.t("Tətbiqlər telefondan silinmir, yalnız qovluq yığışdırılır."), "Sil", () -> {
                    store.items.remove(f);
                    saveAndRebuild();
                });
    }

    private void updateHint() {
        homeHint.setVisibility(store.items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Boş xanaya tək tətbiq qoymaq. */
    private void pickOneApp(final int cell) {
        final Sheet s = new Sheet(root, L.t("Tətbiq seç"));
        final List<App> shown = new ArrayList<>(allApps);
        final EditText q = Ui.input(this, "Axtar…");
        s.add(q);
        final ListView list = plainList();
        final BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int p) { return shown.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override
            public View getView(int p, View cv, ViewGroup parent) {
                TextView t = cv instanceof TextView ? (TextView) cv : rowForList();
                t.setText(shown.get(p).label);
                return t;
            }
        };
        list.setAdapter(ad);
        s.add(list, listParams(340));
        filterOnType(q, shown, ad);
        list.setOnItemClickListener((p, v, pos, id) -> {
            s.dismiss();
            store.addToHome(shown.get(pos).key, cell);
            saveAndRebuild();
        });
        s.button("Bağla", false, null).show();
    }

    // ================================================================ qovluq

    private void openFolder(final Store.Item f) {
        final Sheet s = new Sheet(root, f.name);
        if (f.apps.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(L.t("Qovluq boşdur. \"Tətbiq əlavə et\" ilə doldur."));
            t.setTextColor(Ui.TEXT2);
            t.setTextSize(15);
            s.add(t);
        } else {
            final GridView g = new GridView(this);
            g.setNumColumns(4);
            g.setVerticalSpacing(dp(6));
            g.setSelector(new GradientDrawable());
            g.setOverScrollMode(View.OVER_SCROLL_NEVER);
            folderAdapter = new BaseAdapter() {
                @Override public int getCount() { return f.apps.size(); }
                @Override public Object getItem(int p) { return f.apps.get(p); }
                @Override public long getItemId(int p) { return p; }
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View v = convertView != null ? convertView : createItemView(parent);
                    bindApp(v, f.apps.get(position));
                    return v;
                }
            };
            g.setAdapter(folderAdapter);
            g.setOnItemClickListener((p, v, pos, id) -> {
                String key = f.apps.get(pos);
                s.dismiss();
                launchKey(key);
            });
            g.setOnItemLongClickListener((p, v, pos, id) -> {
                folderAppMenu(s, f, f.apps.get(pos));
                return true;
            });
            int rows = Math.min(4, (f.apps.size() + 3) / 4);
            s.add(g, new LinearLayout.LayoutParams(-1, rows * dp(96)));
        }
        s.button("Redaktə", false, () -> editFolder(f, null, null, -1));
        s.button("+ Tətbiq", true, () -> pickApps(f));
        s.onDismiss(() -> folderAdapter = null).show();
    }

    private void folderAppMenu(final Sheet folderSheet, final Store.Item f, final String key) {
        App a = appsByKey.get(key);
        final String[] items = {"Sola çək", "Sağa çək", "Başqa qovluğa köçür", "Ana ekrana çıxar",
                "Tətbiq məlumatı", "!Qovluqdan götür", "!Telefondan sil"};
        Sheet.menu(root, a != null ? a.label : L.t("Tətbiq"), items, i -> {
            int idx = f.apps.indexOf(key);
            switch (i) {
                case 0:
                case 1: {
                    int to = i == 0 ? idx - 1 : idx + 1;
                    if (idx < 0 || to < 0 || to >= f.apps.size()) return;
                    f.apps.remove(idx);
                    f.apps.add(to, key);
                    break;
                }
                case 2:
                    folderSheet.dismiss();
                    chooseFolder(key);
                    return;
                case 3:
                    f.apps.remove(key);
                    store.addToHome(key, -1);
                    break;
                case 4:
                    openAppInfo(key);
                    return;
                case 6:
                    folderSheet.dismiss();
                    uninstall(key);
                    return;
                default:
                    f.apps.remove(key);
            }
            saveAndRebuild();
            if (folderAdapter != null) folderAdapter.notifyDataSetChanged();
            if (f.apps.isEmpty()) folderSheet.dismiss();
        });
    }

    /** Qovluğa bir neçə tətbiqi birdən seçmək. */
    interface KeysCb { void on(List<String> keys); }

    private void pickApps(final Store.Item f) {
        pickMulti(f.name + L.t(" — tətbiqlər"), f.apps, 0, sel -> {
            f.apps.retainAll(sel);
            for (String k : sel) if (!f.apps.contains(k)) store.addToFolder(k, f);
            saveAndRebuild();
        });
    }

    /** Çox seçimli tətbiq siyahısı. max > 0 olsa ən çox max seçim. Nəticə seçim sırası ilə qayıdır. */
    private void pickMulti(String title, List<String> initial, final int max, final KeysCb done) {
        final List<String> selected = new ArrayList<>(initial);
        final List<App> shown = new ArrayList<>(allApps);
        final Sheet s = new Sheet(root, title);
        final EditText q = Ui.input(this, "Tətbiq axtar…");
        s.add(q);
        final ListView list = plainList();
        final BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int p) { return shown.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override
            public View getView(int p, View cv, ViewGroup parent) {
                TextView t = cv instanceof TextView ? (TextView) cv : rowForList();
                App a = shown.get(p);
                boolean on = selected.contains(a.key);
                t.setText(a.label);
                t.setTextColor(on ? Ui.ACCENT : Ui.TEXT);
                t.setCompoundDrawables(Ui.checkbox(MainActivity.this, on), null, null, null);
                t.setCompoundDrawablePadding(dp(14));
                return t;
            }
        };
        list.setAdapter(ad);
        s.add(list, listParams(360));
        filterOnType(q, shown, ad);
        list.setOnItemClickListener((p, v, pos, id) -> {
            String k = shown.get(pos).key;
            if (!selected.remove(k)) {
                if (max > 0 && selected.size() >= max) {
                    Ui.toast(root, L.t("Ən çox ") + max + L.t(" tətbiq"));
                    return;
                }
                selected.add(k);
            }
            ad.notifyDataSetChanged();
        });
        s.button("Ləğv et", false, null);
        s.button("Hazır", true, () -> done.on(selected));
        s.show();
    }

    // ================================================================ sürətli panel

    private View makeQuickView() {
        FrameLayout wrap = new FrameLayout(this);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackgroundColor(Ui.SURFACE2);
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(Glyphs.get(this, "bolt", Ui.ACCENT));
        bar.addView(ic, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView t = new TextView(this);
        t.setText(L.t("Sürətli"));
        t.setTextColor(Ui.TEXT);
        t.setTypeface(Ui.MEDIUM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(dp(8), 0, 0, 0);
        bar.addView(t);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(52), Gravity.CENTER);
        lp.leftMargin = lp.rightMargin = dp(10);
        wrap.addView(bar, lp);
        return wrap;
    }

    private List<String> quickApps() {
        List<String> out = new ArrayList<>();
        for (String k : store.quick) if (appsByKey.containsKey(k)) out.add(k);
        if (out.isEmpty()) out.addAll(usage.top(9, appsByKey.keySet()));
        return out.size() > 9 ? out.subList(0, 9) : out;
    }

    @Override
    public void onQuickClick() {
        final List<String> keys = quickApps();
        final Sheet s = new Sheet(root, null);
        if (keys.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(L.t("Sürətli tətbiq yoxdur.\n\"Seç\" ilə 9-a qədər tətbiq əlavə et."));
            t.setTextColor(Ui.TEXT2);
            t.setTextSize(15);
            s.add(t);
        } else {
            GridView g = new GridView(this);
            g.setNumColumns(3);
            g.setVerticalSpacing(dp(4));
            g.setSelector(new GradientDrawable());
            g.setOverScrollMode(View.OVER_SCROLL_NEVER);
            g.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return keys.size(); }
                @Override public Object getItem(int p) { return keys.get(p); }
                @Override public long getItemId(int p) { return p; }
                @Override
                public View getView(int p, View cv, ViewGroup parent) {
                    View v = cv != null ? cv : createItemView(parent);
                    bindApp(v, keys.get(p));
                    return v;
                }
            });
            g.setOnItemClickListener((p, v, pos, id) -> {
                s.dismiss();
                launchKey(keys.get(pos));
            });
            int rows = (keys.size() + 2) / 3;
            s.add(g, new LinearLayout.LayoutParams(-1, rows * dp(92)));
            if (store.quick.isEmpty()) {
                TextView hint = new TextView(this);
                hint.setText(L.t("Avtomatik: ən çox açdıqların"));
                hint.setTextColor(Ui.TEXT2);
                hint.setTextSize(12);
                s.add(hint);
            }
        }
        s.button("Seç", !keys.isEmpty() ? false : true, this::pickQuick);
        s.show();
    }

    @Override
    public void onQuickLongPress() {
        pickQuick();
    }

    private void pickQuick() {
        List<String> init = new ArrayList<>(store.quick);
        if (init.isEmpty()) init.addAll(quickApps());
        pickMulti(L.t("Sürətli tətbiqlər (maks 9)"), init, 9, sel -> {
            store.quick.clear();
            store.quick.addAll(sel);
            store.save();
            Ui.toast(root, sel.size() + L.t(" sürətli tətbiq"));
        });
    }

    // ================================================================ avtomatik qovluqlar

    private void autoGroup() {
        final Set<String> placed = new HashSet<>();
        for (Store.Item it : store.items) {
            if (it.folder) placed.addAll(it.apps); else placed.add(it.app);
        }
        final List<App> todo = new ArrayList<>();
        for (App a : allApps) if (!placed.contains(a.key)) todo.add(a);
        if (todo.isEmpty()) {
            Ui.toast(root, "Bütün tətbiqlər artıq yerləşdirilib");
            return;
        }
        final PackageManager pm = getPackageManager();
        worker.execute(() -> {
            final Map<String, List<String>> plan = new LinkedHashMap<>();
            for (App a : todo) {
                String pkg = a.component.getPackageName();
                ApplicationInfo ai = null;
                try { ai = pm.getApplicationInfo(pkg, 0); } catch (Exception ignored) { }
                String g = Groups.classify(pkg, a.label, ai);
                List<String> l = plan.get(g);
                if (l == null) { l = new ArrayList<>(); plan.put(g, l); }
                l.add(a.key);
            }
            ui.post(() -> confirmAutoGroup(plan, todo.size()));
        });
    }

    /** Bütün Lite tətbiqləri "Lite" qovluğunda. Yeni quraşdırılan Lite tətbiqi avtomatik ora düşür. */
    private void ensureLiteFolder() {
        android.content.SharedPreferences p = getSharedPreferences("lite_seen", MODE_PRIVATE);
        java.util.Set<String> seen = new HashSet<>(p.getStringSet("k", new HashSet<String>()));
        List<String> fresh = new ArrayList<>();
        for (App a : allApps) {
            if (a.component.getPackageName().startsWith("az.lite.") && !seen.contains(a.key)) fresh.add(a.key);
        }
        if (fresh.isEmpty()) return;
        Store.Item f = folderNamed("Lite");
        if (f == null) {
            f = new Store.Item();
            f.folder = true;
            f.name = "Lite";
            f.icon = "bolt";
            store.place(f, -1);
            store.items.add(f);
        }
        for (String k : fresh) {
            if (store.indexOfApp(k) < 0) store.addToFolder(k, f); // ana ekranda ayrıca durursa toxunma
            seen.add(k);
        }
        p.edit().putStringSet("k", seen).apply();
        saveAndRebuild();
    }

    private Store.Item folderNamed(String name) {
        String n = Groups.norm(name);
        for (Store.Item f : store.folders()) if (Groups.norm(f.name).equals(n)) return f;
        return null;
    }

    private void confirmAutoGroup(final Map<String, List<String>> plan, int total) {
        StringBuilder b = new StringBuilder();
        int newFolders = 0;
        for (Map.Entry<String, List<String>> e : plan.entrySet()) {
            boolean exists = folderNamed(e.getKey()) != null || folderNamed(L.t(e.getKey())) != null;
            if (!exists) newFolders++;
            b.append(L.t(e.getKey())).append(" · ").append(e.getValue().size()).append(exists ? "" : L.t("  (yeni)")).append('\n');
            StringBuilder names = new StringBuilder();
            for (String k : e.getValue()) {
                App a = appsByKey.get(k);
                if (a == null) continue;
                if (names.length() > 0) names.append(", ");
                names.append(a.label);
            }
            b.append("   ").append(names).append("\n\n");
        }
        b.append(L.t("\nAna ekrandakı ayrıca tətbiqlərə toxunulmur."));
        final ScrollView sv = new ScrollView(this);
        TextView t = new TextView(this);
        t.setText(b.toString());
        t.setTextColor(Ui.TEXT);
        t.setTextSize(15);
        t.setLineSpacing(dp(3), 1f);
        sv.addView(t);
        new Sheet(root, total + L.t(" tətbiq → ") + plan.size() + L.t(" qovluq"))
                .add(sv, new LinearLayout.LayoutParams(-1, dp(420)))
                .button("Ləğv et", false, null)
                .button("Böl", true, () -> applyAutoGroup(plan))
                .show();
    }

    private void applyAutoGroup(Map<String, List<String>> plan) {
        for (Map.Entry<String, List<String>> e : plan.entrySet()) {
            Store.Item f = folderNamed(e.getKey());
            if (f == null) f = folderNamed(L.t(e.getKey()));
            if (f == null) {
                f = new Store.Item();
                f.folder = true;
                f.name = L.t(e.getKey());
                f.icon = Groups.glyphFor(e.getKey());
                store.place(f, -1);
                store.items.add(f);
            }
            for (String k : e.getValue()) store.addToFolder(k, f);
        }
        saveAndRebuild();
        Ui.toast(root, "Qovluqlara bölündü");
    }

    /** Qovluq seçicisi (çox qovluq olanda axtarışla). */
    private void chooseFolder(final String key) {
        final List<Store.Item> folders = store.folders();
        Collections.sort(folders, (a, b) -> a.name.compareToIgnoreCase(b.name));
        final List<Store.Item> shown = new ArrayList<>(folders);
        final Sheet s = new Sheet(root, L.t("Qovluq seç"));
        final EditText q = Ui.input(this, "Qovluq axtar…");
        if (folders.size() > 5) s.add(q);

        final ListView list = plainList();
        final BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return shown.size() + 1; }
            @Override public Object getItem(int p) { return p; }
            @Override public long getItemId(int p) { return p; }
            @Override
            public View getView(int p, View cv, ViewGroup parent) {
                TextView t = cv instanceof TextView ? (TextView) cv : rowForList();
                if (p == shown.size()) {
                    String typed = q.getText().toString().trim();
                    t.setText(typed.isEmpty() ? L.t("+  Yeni qovluq") : L.t("+  Yeni qovluq: ") + typed);
                    t.setTextColor(Ui.ACCENT);
                    t.setCompoundDrawables(null, null, null, null);
                } else {
                    Store.Item f = shown.get(p);
                    t.setText(f.name);
                    t.setTextColor(Ui.TEXT);
                    Drawable g = Glyphs.get(MainActivity.this, f.icon != null ? f.icon : "folder", Ui.TEXT);
                    if (g != null) g.setBounds(0, 0, dp(20), dp(20));
                    t.setCompoundDrawables(g, null, null, null);
                    t.setCompoundDrawablePadding(dp(14));
                }
                return t;
            }
        };
        list.setAdapter(ad);
        s.add(list, listParams(Math.min(360, (shown.size() + 1) * 52)));
        q.addTextChangedListener(new SimpleWatcher(() -> {
            String f = q.getText().toString().trim().toLowerCase(Locale.ROOT);
            shown.clear();
            for (Store.Item it : folders) {
                if (f.isEmpty() || it.name.toLowerCase(Locale.ROOT).contains(f)) shown.add(it);
            }
            ad.notifyDataSetChanged();
        }));
        list.setOnItemClickListener((p, v, pos, id) -> {
            s.dismiss();
            if (pos == shown.size()) {
                String typed = q.getText().toString().trim();
                editFolder(null, key, typed.isEmpty() ? null : typed, -1);
            } else {
                Store.Item f = shown.get(pos);
                store.addToFolder(key, f);
                saveAndRebuild();
                Ui.toast(root, L.t("Əlavə olundu: ") + f.name);
            }
        });
        s.button("Bağla", false, null).show();
    }

    /** Qovluq yarat / redaktə et. existing == null → yeni. cell → yeni qovluğun xanası. */
    private void editFolder(final Store.Item existing, final String addKey, String initialName, final int cell) {
        final boolean isNew = existing == null;
        final Store.Item f = isNew ? new Store.Item() : existing;
        if (isNew) f.folder = true;

        final int[] color = {f.color};
        final int[] textColor = {f.textColor};
        final String[] icon = {isNew ? "folder" : f.icon};
        final boolean[] userPicked = {!isNew};

        final Sheet s = new Sheet(root, L.t(isNew ? "Yeni qovluq" : "Qovluğu redaktə et"));

        // önizləmə + ad yan-yana
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        final IconView preview = new IconView(this);
        top.addView(preview, new LinearLayout.LayoutParams(dp(60), dp(60)));
        final EditText name = Ui.input(this, "Qovluğun adı");
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setText(isNew ? (initialName != null ? initialName : "") : f.name);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, -2, 1);
        nlp.leftMargin = dp(14);
        top.addView(name, nlp);
        s.add(top);

        // bir sətirdə: İkon · Rəng · Yazı — basanda ayrıca panel açılır
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        final ImageView iconChip = new ImageView(this);
        final View colorChip = new View(this);
        final View textChip = new View(this);
        chips.addView(chip(iconChip, "İkon"), chipParams(false));
        chips.addView(chip(colorChip, "Rəng"), chipParams(true));
        chips.addView(chip(textChip, "Yazı"), chipParams(true));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = dp(16);
        s.add(chips, clp);

        final Runnable update = () -> {
            preview.showFolder(color[0], Glyphs.get(this, icon[0], textColor[0]), miniIcons(f));
            if (icon[0] == null) {
                iconChip.setImageDrawable(Glyphs.get(this, "apps", Ui.TEXT2));
            } else {
                iconChip.setImageDrawable(Glyphs.get(this, icon[0], Ui.TEXT));
            }
            colorChip.setBackground(color[0] == 0
                    ? Ui.outline(0, Ui.TEXT2, dp(1)) : Ui.outline(color[0], Ui.LINE, dp(1)));
            textChip.setBackground(Ui.outline(textColor[0], Ui.LINE, dp(1)));
        };

        iconChip.setOnClickListener(v -> pickGlyph(icon[0], k -> {
            icon[0] = k;
            userPicked[0] = true;
            update.run();
        }));
        colorChip.setOnClickListener(v -> ColorPicker.show(root, "Qovluq rəngi", color[0], true, c -> {
            color[0] = c;
            update.run();
        }));
        textChip.setOnClickListener(v -> ColorPicker.show(root, "İkon və ad rəngi", textColor[0], false, c -> {
            textColor[0] = c;
            update.run();
        }));

        name.addTextChangedListener(new SimpleWatcher(() -> {
            if (!userPicked[0]) {
                String sug = Glyphs.suggest(name.getText().toString());
                icon[0] = sug != null ? sug : "folder";
            }
            update.run();
        }));
        if (isNew && initialName != null && !userPicked[0]) {
            String sug = Glyphs.suggest(initialName);
            if (sug != null) icon[0] = sug;
        }
        update.run();

        s.button("Ləğv et", false, null);
        s.button("Yadda saxla", true, () -> {
            String n = name.getText().toString().trim();
            f.name = n.isEmpty() ? L.t("Qovluq") : n;
            f.color = color[0];
            f.textColor = textColor[0];
            f.icon = icon[0];
            if (isNew) {
                store.place(f, cell);
                store.items.add(f);
            }
            if (addKey != null) store.addToFolder(addKey, f);
            saveAndRebuild();
            if (isNew && addKey == null) pickApps(f);
        });
        s.show();
    }

    /** Kiçik seçim xanası: üstdə nümunə (kvadrat), altda ad. */
    private View chip(View sample, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(6), dp(10), dp(6), dp(8));
        box.setBackground(Ui.pressable(Ui.SURFACE2, Ui.SURFACE3));
        box.setClickable(true);
        box.setOnClickListener(v -> sample.performClick());
        if (sample instanceof ImageView) {
            ((ImageView) sample).setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            sample.setPadding(dp(6), dp(6), dp(6), dp(6));
            sample.setBackgroundColor(Ui.SURFACE3);
        }
        box.addView(sample, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView t = new TextView(this);
        t.setText(L.t(label));
        t.setTextColor(Ui.TEXT2);
        t.setTextSize(12);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(6), 0, 0);
        box.addView(t);
        return box;
    }

    private LinearLayout.LayoutParams chipParams(boolean gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        if (gap) lp.leftMargin = dp(8);
        return lp;
    }

    interface StrCb { void on(String s); }

    /** İkon seçimi ayrıca paneldə. Birinci xana (null) = içindəki tətbiqlərin mini ikonları. */
    private void pickGlyph(final String current, final StrCb done) {
        final Sheet s = new Sheet(root, L.t("İkon"));
        final GridView g = new GridView(this);
        g.setNumColumns(6);
        g.setHorizontalSpacing(dp(4));
        g.setVerticalSpacing(dp(4));
        g.setSelector(new GradientDrawable());
        g.setOverScrollMode(View.OVER_SCROLL_NEVER);
        g.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return Glyphs.KEYS.length + 1; }
            @Override public Object getItem(int p) { return p; }
            @Override public long getItemId(int p) { return p; }
            @Override
            public View getView(int p, View cv, ViewGroup parent) {
                ImageView iv = cv instanceof ImageView ? (ImageView) cv : new ImageView(MainActivity.this);
                iv.setLayoutParams(new AbsListView.LayoutParams(-1, dp(52)));
                iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                iv.setPadding(dp(12), dp(12), dp(12), dp(12));
                String k = p == 0 ? "apps" : Glyphs.KEYS[p - 1];
                boolean sel = p == 0 ? current == null : k.equals(current);
                iv.setImageDrawable(Glyphs.get(MainActivity.this, k, sel ? Ui.ON_ACCENT : (p == 0 ? Ui.TEXT2 : Ui.TEXT)));
                iv.setBackgroundColor(sel ? Ui.ACCENT : Ui.SURFACE2);
                return iv;
            }
        });
        g.setOnItemClickListener((p, v, pos, id) -> {
            s.dismiss();
            done.on(pos == 0 ? null : Glyphs.KEYS[pos - 1]);
        });
        int rows = (Glyphs.KEYS.length + 1 + 5) / 6;
        int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.55f);
        s.add(g, new LinearLayout.LayoutParams(-1, Math.min(max, rows * dp(56))));
        TextView note = new TextView(this);
        note.setText(L.t("Birinci xana: içindəki tətbiqlərin kiçik ikonları"));
        note.setTextColor(Ui.TEXT2);
        note.setTextSize(11);
        note.setPadding(0, dp(6), 0, 0);
        s.add(note);
        s.button("Bağla", false, null).show();
    }

    private Drawable[] miniIcons(Store.Item f) {
        Drawable[] out = new Drawable[4];
        int n = 0;
        for (String key : f.apps) {
            if (n == 4) break;
            Drawable d = icon(key);
            if (d != null) out[n++] = d;
        }
        return out;
    }

    // ================================================================ tətbiqlər səhifəsi

    private void setupDrawer() {
        appAdapter = new BaseAdapter() {
            @Override public int getCount() { return shownApps.size(); }
            @Override public Object getItem(int p) { return shownApps.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView != null ? convertView : createItemView(parent);
                bindApp(v, shownApps.get(position).key);
                return v;
            }
        };
        appGrid.setAdapter(appAdapter);
        appGrid.setOnItemClickListener((p, v, pos, id) -> launch(shownApps.get(pos)));
        appGrid.setOnItemLongClickListener((p, v, pos, id) -> {
            drawerMenu(shownApps.get(pos));
            return true;
        });

        search.addTextChangedListener(new SimpleWatcher(this::applyFilter));
        calcResult.setOnClickListener(v -> {
            if (calcValue == null) return;
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("calc", calcValue.replace(',', '.')));
            Ui.toast(root, L.t("Kopyalandı: ") + calcValue);
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (go && !shownApps.isEmpty()) {
                launch(shownApps.get(0));
                return true;
            }
            return false;
        });
    }

    private void drawerMenu(final App app) {
        boolean onHome = store.indexOfApp(app.key) >= 0;
        String[] items = onHome
                ? new String[]{"Qovluğa əlavə et", "Tətbiq məlumatı", "!Telefondan sil"}
                : new String[]{"Ana ekrana əlavə et", "Qovluğa əlavə et", "Tətbiq məlumatı", "!Telefondan sil"};
        Sheet.menu(root, app.label, items, i -> {
            int k = onHome ? i + 1 : i;
            if (k == 0) {
                store.addToHome(app.key, -1);
                saveAndRebuild();
                Ui.toast(root, "Ana ekrana əlavə olundu");
            } else if (k == 1) {
                chooseFolder(app.key);
            } else if (k == 2) {
                openAppInfo(app.key);
            } else {
                uninstall(app.key);
            }
        });
    }

    private void applyFilter() {
        String raw = search.getText().toString().trim();
        String q = raw.toLowerCase(Locale.ROOT);
        calcValue = Calc.eval(raw);
        if (calcValue != null) {
            calcResult.setText("= " + calcValue);
            calcResult.setVisibility(View.VISIBLE);
        } else {
            calcResult.setVisibility(View.GONE);
        }
        updateTopApps(q.isEmpty());
        shownApps.clear();
        if (q.isEmpty()) {
            shownApps.addAll(allApps);
        } else {
            List<App> contains = new ArrayList<>();
            for (App a : allApps) {
                if (a.search.startsWith(q)) shownApps.add(a);
                else if (a.search.contains(q)) contains.add(a);
            }
            shownApps.addAll(contains);
        }
        appAdapter.notifyDataSetChanged();
    }

    /** Axtarış boş olanda yuxarıda ən çox açılan 4 tətbiq. */
    private void updateTopApps(boolean show) {
        List<String> keys = show ? usage.top(4, appsByKey.keySet()) : Collections.<String>emptyList();
        if (keys.isEmpty()) {
            topApps.setVisibility(View.GONE);
            return;
        }
        topApps.setVisibility(View.VISIBLE);
        while (topRow.getChildCount() < 4) {
            View v = createItemView(topRow);
            topRow.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        for (int i = 0; i < 4; i++) {
            View v = topRow.getChildAt(i);
            if (i >= keys.size()) {
                v.setVisibility(View.INVISIBLE);
                v.setOnClickListener(null);
                v.setOnLongClickListener(null);
                continue;
            }
            final String key = keys.get(i);
            v.setVisibility(View.VISIBLE);
            bindApp(v, key);
            v.setOnClickListener(x -> launchKey(key));
            v.setOnLongClickListener(x -> {
                App a = appsByKey.get(key);
                if (a != null) drawerMenu(a);
                return true;
            });
        }
    }

    private void resetSearch() {
        if (search.length() > 0) search.setText("");
        search.clearFocus();
        hideKeyboard();
        appGrid.setSelection(0);
    }

    // ================================================================ vidcetlər

    private void setupWidgets() {
        widgetManager = AppWidgetManager.getInstance(this);
        widgetHost = new AppWidgetHost(getApplicationContext(), HOST_ID);
        try { widgetHost.startListening(); } catch (Exception ignored) { }
        findViewById(R.id.widget_add).setOnClickListener(v -> pickWidget());
        findViewById(R.id.widget_add).setBackground(Ui.pressable(Ui.ACCENT, 0xFFE0BC00));
        boolean changed = false;
        for (Integer id : new ArrayList<>(store.widgets)) {
            if (!addWidgetView(id)) {
                store.widgets.remove(id);
                changed = true;
            }
        }
        if (changed) store.save();
        // yarımçıq qalmış (əlavə olunmamış) vidcet ID-lərini təmizlə
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                for (int id : widgetHost.getAppWidgetIds()) {
                    if (!store.widgets.contains(id) && id != pendingWidgetId) widgetHost.deleteAppWidgetId(id);
                }
            } catch (Exception ignored) { }
        }
        updateWidgetHint();
    }

    private void updateWidgetHint() {
        widgetHint.setVisibility(store.widgets.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** 1-ci addım: vidceti olan tətbiqlərin siyahısı (hər tətbiqdə neçə vidcet var). */
    private void pickWidget() {
        final PackageManager pm = getPackageManager();
        final Map<String, List<AppWidgetProviderInfo>> byPkg = new HashMap<>();
        for (AppWidgetProviderInfo info : widgetManager.getInstalledProviders()) {
            String pkg = info.provider.getPackageName();
            List<AppWidgetProviderInfo> l = byPkg.get(pkg);
            if (l == null) {
                l = new ArrayList<>();
                byPkg.put(pkg, l);
            }
            l.add(info);
        }
        final Map<String, String> labels = new HashMap<>();
        for (String pkg : byPkg.keySet()) {
            String label;
            try {
                label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            } catch (Exception e) {
                label = pkg;
            }
            labels.put(pkg, label);
        }
        final List<String> pkgs = new ArrayList<>(byPkg.keySet());
        final Collator col = Collator.getInstance();
        Collections.sort(pkgs, (a, b) -> col.compare(labels.get(a), labels.get(b)));
        final List<String> shown = new ArrayList<>(pkgs);
        final Map<String, Drawable> icons = new HashMap<>();

        final Sheet s = new Sheet(root, L.t("Vidcet əlavə et"));
        final EditText q = Ui.input(this, "Tətbiq axtar…");
        s.add(q);
        final ListView list = plainList();
        final BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int p) { return shown.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override
            public View getView(int p, View cv, ViewGroup parent) {
                TextView t = cv instanceof TextView ? (TextView) cv : rowForList();
                String pkg = shown.get(p);
                int n = byPkg.get(pkg).size();
                t.setText(labels.get(pkg) + "   ·  " + n + L.t(" vidcet"));
                Drawable ic = icons.get(pkg);
                if (ic != null) ic.setBounds(0, 0, dp(30), dp(30));
                t.setCompoundDrawables(ic, null, null, null);
                t.setCompoundDrawablePadding(dp(14));
                return t;
            }
        };
        list.setAdapter(ad);
        s.add(list, listParams(400));
        // tətbiq ikonları arxa planda
        worker.execute(() -> {
            for (String pkg : pkgs) {
                Drawable d;
                try { d = pm.getApplicationIcon(pkg); } catch (Exception e) { d = null; }
                final Drawable fd = d;
                ui.post(() -> {
                    icons.put(pkg, fd);
                    ad.notifyDataSetChanged();
                });
            }
        });
        q.addTextChangedListener(new SimpleWatcher(() -> {
            String f = q.getText().toString().trim().toLowerCase(Locale.ROOT);
            shown.clear();
            for (String pkg : pkgs) {
                if (f.isEmpty() || labels.get(pkg).toLowerCase(Locale.ROOT).contains(f)) shown.add(pkg);
            }
            ad.notifyDataSetChanged();
        }));
        list.setOnItemClickListener((p, v, pos, id) -> {
            String pkg = shown.get(pos);
            s.dismiss();
            showWidgetsOf(labels.get(pkg), byPkg.get(pkg));
        });
        s.button("Bağla", false, null).show();
    }

    /** 2-ci addım: seçilmiş tətbiqin vidcetləri — önizləmə şəkli, adı və ölçüsü ilə. */
    private void showWidgetsOf(String appLabel, List<AppWidgetProviderInfo> infos) {
        final PackageManager pm = getPackageManager();
        final Sheet s = new Sheet(root, appLabel);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);
        float density = getResources().getDisplayMetrics().density;
        for (final AppWidgetProviderInfo info : infos) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(12), dp(12), dp(12));
            card.setBackground(Ui.pressable(Ui.SURFACE2, Ui.SURFACE3));
            card.setClickable(true);

            final ImageView pv = new ImageView(this);
            pv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            pv.setBackgroundColor(Ui.SURFACE);
            pv.setPadding(dp(8), dp(8), dp(8), dp(8));
            card.addView(pv, new LinearLayout.LayoutParams(-1, dp(140)));

            TextView name = new TextView(this);
            name.setText(info.loadLabel(pm));
            name.setTextColor(Ui.TEXT);
            name.setTypeface(Ui.BOLD);
            name.setTextSize(15);
            name.setPadding(0, dp(10), 0, 0);
            card.addView(name);

            int cw = Math.max(1, Math.round((info.minWidth / density + 30) / 70f));
            int ch = Math.max(1, Math.round((info.minHeight / density + 30) / 70f));
            TextView size = new TextView(this);
            size.setText(cw + " × " + ch);
            size.setTextColor(Ui.TEXT2);
            size.setTextSize(12);
            card.addView(size);

            card.setOnClickListener(v -> {
                s.dismiss();
                bindWidget(info);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(8);
            col.addView(card, lp);

            worker.execute(() -> {
                Drawable d = null;
                try { d = info.loadPreviewImage(this, 0); } catch (Exception ignored) { }
                if (d == null) {
                    try { d = info.loadIcon(this, 0); } catch (Exception ignored) { }
                }
                final Drawable fd = d;
                ui.post(() -> pv.setImageDrawable(fd));
            });
        }
        int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.62f);
        int want = infos.size() * dp(212);
        s.add(sv, new LinearLayout.LayoutParams(-1, Math.min(max, want)));
        s.button("Geri", false, this::pickWidget);
        s.button("Bağla", true, null).show();
    }

    private void setPending(int id) {
        pendingWidgetId = id;
        settings.edit().putInt("pending_widget", id).commit();
    }

    private static boolean configOptional(AppWidgetProviderInfo info) {
        if (Build.VERSION.SDK_INT < 28) return false;
        // WIDGET_FEATURE_RECONFIGURABLE = 1, WIDGET_FEATURE_CONFIGURATION_OPTIONAL = 4 (API 31)
        return (info.widgetFeatures & 4) != 0;
    }

    private void bindWidget(AppWidgetProviderInfo info) {
        int id = widgetHost.allocateAppWidgetId();
        boolean ok;
        try {
            if (Build.VERSION.SDK_INT >= 21 && info.getProfile() != null) {
                ok = widgetManager.bindAppWidgetIdIfAllowed(id, info.getProfile(), info.provider, null);
            } else {
                ok = widgetManager.bindAppWidgetIdIfAllowed(id, info.provider);
            }
        } catch (Exception e) {
            ok = false;
        }
        if (ok) {
            configureWidget(id, info);
        } else {
            setPending(id);
            Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            if (Build.VERSION.SDK_INT >= 21 && info.getProfile() != null) {
                i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.getProfile());
            }
            try {
                startActivityForResult(i, REQ_BIND);
            } catch (Exception e) {
                setPending(-1);
                widgetHost.deleteAppWidgetId(id);
                Ui.toast(root, "Vidcet əlavə olunmadı");
            }
        }
    }

    private void configureWidget(int id, AppWidgetProviderInfo info) {
        if (info != null && info.configure != null && !configOptional(info)) {
            setPending(id);
            if (startConfigure(id, info, REQ_CONFIG)) return;
            setPending(-1);
            // konfiqurasiya açılmadısa, elə birbaşa əlavə edək
        }
        commitWidget(id);
    }

    private boolean startConfigure(int id, AppWidgetProviderInfo info, int req) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, req, null);
            } else {
                Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
                i.setComponent(info.configure);
                i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
                startActivityForResult(i, req);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PHOTO) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                goTo(P_PHOTOS);
                photosPage.onPicked(data.getData());
            }
            return;
        }
        if (requestCode == REQ_CONFIG + 100) { // mövcud vidcetin yenidən ayarlanması
            return;
        }
        if (requestCode != REQ_BIND && requestCode != REQ_CONFIG) return;
        int id = pendingWidgetId;
        if (data != null) id = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        setPending(-1);
        if (id < 0) return;
        if (resultCode != RESULT_OK) {
            // bəzi vidcetlər ayar ekranını "ləğv" ilə bağlayır, amma artıq bağlanıblar —
            // bind ləğv olunubsa silirik, konfiqurasiya ləğvində də (Android qaydası)
            widgetHost.deleteAppWidgetId(id);
            if (requestCode == REQ_BIND) Ui.toast(root, "Vidcet icazəsi verilmədi");
            return;
        }
        final int fid = id;
        final int req = requestCode;
        ui.post(() -> {
            if (req == REQ_BIND) configureWidget(fid, widgetManager.getAppWidgetInfo(fid));
            else commitWidget(fid);
        });
    }

    private void commitWidget(int id) {
        if (store.widgets.contains(id)) return;
        if (!addWidgetView(id)) {
            widgetHost.deleteAppWidgetId(id);
            Ui.toast(root, "Vidcet əlavə olunmadı");
            return;
        }
        store.widgets.add(id);
        store.save();
        updateWidgetHint();
        goTo(P_WIDGETS);
    }

    /** Vidcetin hündürlüyü (dp). İstifadəçi dəyişibsə yadda saxlanılan, yoxsa vidcetin öz ölçüsündən. */
    private int widgetHeightDp(int id, AppWidgetProviderInfo info) {
        int saved = settings.getInt("wh_" + id, -1);
        if (saved > 0) return saved;
        float density = getResources().getDisplayMetrics().density;
        int minDp = (int) (info.minHeight / density);
        int cells;
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellHeight > 0) cells = info.targetCellHeight;
        else cells = Math.max(1, Math.round((minDp + 30) / 70f));
        return Math.max(minDp, Math.max(100, cells * 96));
    }

    private boolean addWidgetView(final int id) {
        AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(id);
        if (info == null) return false;
        AppWidgetHostView hv;
        try {
            hv = widgetHost.createView(this, id, info);
        } catch (Exception e) {
            return false;
        }
        hv.setAppWidget(id, info);
        hv.setPadding(0, 0, 0, 0);
        final WidgetFrame frame = new WidgetFrame(this);
        frame.setTag(id);
        frame.addView(hv, new FrameLayout.LayoutParams(-1, dp(widgetHeightDp(id, info))));
        frame.setOnLongPress(() -> widgetMenu(frame, id));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        widgetList.addView(frame, lp);
        sizeWidget(hv, widgetHeightDp(id, info));
        return true;
    }

    private void sizeWidget(AppWidgetHostView hv, int hDp) {
        try {
            float d = getResources().getDisplayMetrics().density;
            int wDp = (int) (getResources().getDisplayMetrics().widthPixels / d) - 28;
            Bundle o = new Bundle();
            o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wDp);
            o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wDp);
            o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, hDp);
            o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hDp);
            if (Build.VERSION.SDK_INT >= 31) {
                java.util.ArrayList<android.util.SizeF> sizes = new java.util.ArrayList<>();
                sizes.add(new android.util.SizeF(wDp, hDp));
                o.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, sizes);
            }
            widgetManager.updateAppWidgetOptions(hv.getAppWidgetId(), o);
        } catch (Exception ignored) { }
    }

    private void widgetMenu(final WidgetFrame frame, final int id) {
        final AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(id);
        final boolean canConfig = info != null && info.configure != null;
        List<String> items = new ArrayList<>();
        items.add("Hündürlüyü artır");
        items.add("Hündürlüyü azalt");
        items.add("Yuxarı");
        items.add("Aşağı");
        if (canConfig) items.add("Ayarla");
        items.add("!Sil");
        String title = info != null ? info.loadLabel(getPackageManager()) : L.t("Vidcet");
        Sheet.menu(root, title, items.toArray(new String[0]), i -> {
            String what = items.get(i);
            int idx = widgetList.indexOfChild(frame);
            switch (what) {
                case "Hündürlüyü artır":
                case "Hündürlüyü azalt": {
                    if (info == null) return;
                    int h = widgetHeightDp(id, info) + ("Hündürlüyü artır".equals(what) ? 48 : -48);
                    h = Math.max(48, Math.min(900, h));
                    settings.edit().putInt("wh_" + id, h).apply();
                    View hv = frame.getChildAt(0);
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) hv.getLayoutParams();
                    lp.height = dp(h);
                    hv.setLayoutParams(lp);
                    if (hv instanceof AppWidgetHostView) sizeWidget((AppWidgetHostView) hv, h);
                    return;
                }
                case "Ayarla":
                    startConfigure(id, info, REQ_CONFIG + 100);
                    return;
                case "!Sil":
                    widgetList.removeView(frame);
                    store.widgets.remove(Integer.valueOf(id));
                    widgetHost.deleteAppWidgetId(id);
                    settings.edit().remove("wh_" + id).apply();
                    break;
                default: {
                    int to = "Yuxarı".equals(what) ? idx - 1 : idx + 1;
                    if (to < 0 || to >= widgetList.getChildCount()) return;
                    widgetList.removeView(frame);
                    widgetList.addView(frame, to);
                    store.widgets.remove(Integer.valueOf(id));
                    store.widgets.add(Math.min(to, store.widgets.size()), id);
                }
            }
            store.save();
            updateWidgetHint();
        });
    }

    // ================================================================ tətbiqlərin yüklənməsi

    private void loadApps() {
        final int gen = ++loadGeneration;
        final PackageManager pm = getPackageManager();
        final String self = getPackageName();
        worker.execute(() -> {
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(main, 0);
            final List<App> result = new ArrayList<>(infos.size());
            for (ResolveInfo ri : infos) {
                CharSequence l = ri.loadLabel(pm);
                String label = l == null ? ri.activityInfo.packageName : l.toString().trim();
                result.add(new App(label, new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)));
            }
            final Collator collator = Collator.getInstance();
            collator.setStrength(Collator.PRIMARY);
            Collections.sort(result, (a, b) -> collator.compare(a.label, b.label));
            ui.post(() -> {
                if (gen != loadGeneration || isFinishing()) return;
                allApps.clear();
                allApps.addAll(result);
                appsByKey.clear();
                for (App a : result) appsByKey.put(a.key, a);
                if (store.retainInstalled(appsByKey.keySet())) store.save();
                ensureLiteFolder();
                applyFilter();
                homeGrid.rebuild();
                updateHint();
            });
        });
    }

    /** İkonu keşdən qaytarır; yoxdursa arxa planda yükləyir və hazır olanda ekranı yeniləyir. */
    private Drawable icon(final String key) {
        Drawable d = iconCache.get(key);
        if (d != null) return d;
        if (pendingIcons.add(key)) {
            final ComponentName cn = ComponentName.unflattenFromString(key);
            final PackageManager pm = getPackageManager();
            iconWorker.execute(() -> {
                Drawable r;
                try {
                    r = pm.getActivityIcon(cn);
                } catch (Exception e) {
                    r = pm.getDefaultActivityIcon();
                }
                final Drawable ic = r;
                ui.post(() -> {
                    pendingIcons.remove(key);
                    iconCache.put(key, ic);
                    scheduleRefresh();
                });
            });
        }
        return null;
    }

    private void scheduleRefresh() {
        if (refreshPosted) return;
        refreshPosted = true;
        ui.postDelayed(() -> {
            refreshPosted = false;
            homeGrid.rebind();
            appAdapter.notifyDataSetChanged();
            if (topApps.getVisibility() == View.VISIBLE) updateTopApps(true);
            if (folderAdapter != null) folderAdapter.notifyDataSetChanged();
        }, 50);
    }

    private void saveAndRebuild() {
        store.save();
        homeGrid.rebuild();
        if (folderAdapter != null) folderAdapter.notifyDataSetChanged();
        updateHint();
    }

    // ================================================================ View köməkçiləri

    private static final class Holder {
        IconView icon;
        TextView label;
    }

    private void bindApp(View v, String key) {
        Holder h = (Holder) v.getTag(R.id.icon);
        App a = appsByKey.get(key);
        h.label.setText(a != null ? a.label : "");
        h.label.setTextColor(Store.DEFAULT_TEXT);
        h.icon.showApp(icon(key));
    }

    private void bindFolder(View v, Store.Item f) {
        Holder h = (Holder) v.getTag(R.id.icon);
        h.label.setText(f.name);
        h.label.setTextColor(Store.DEFAULT_TEXT);
        h.icon.showFolder(f.color, Glyphs.get(this, f.icon, f.textColor), miniIcons(f));
    }

    private ListView plainList() {
        ListView l = new ListView(this);
        l.setDivider(null);
        l.setDividerHeight(0);
        l.setSelector(new GradientDrawable());
        l.setOverScrollMode(View.OVER_SCROLL_NEVER);
        l.setVerticalScrollBarEnabled(false);
        return l;
    }

    private LinearLayout.LayoutParams listParams(int heightDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(heightDp));
        lp.topMargin = dp(10);
        return lp;
    }

    private TextView rowForList() {
        TextView t = Ui.row(this, "");
        t.setClickable(false); // ListView özü kliki idarə edir
        t.setBackground(Ui.pressable(Ui.SURFACE, Ui.SURFACE3));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        return t;
    }

    private void filterOnType(final EditText q, final List<App> shown, final BaseAdapter ad) {
        q.addTextChangedListener(new SimpleWatcher(() -> {
            String needle = q.getText().toString().trim().toLowerCase(Locale.ROOT);
            shown.clear();
            for (App a : allApps) {
                if (needle.isEmpty() || a.search.contains(needle)) shown.add(a);
            }
            ad.notifyDataSetChanged();
        }));
    }

    private static final class SimpleWatcher implements TextWatcher {
        private final Runnable r;
        SimpleWatcher(Runnable r) { this.r = r; }
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void afterTextChanged(Editable s) { r.run(); }
    }

    // ================================================================ əməliyyatlar

    private void launchKey(String key) {
        App a = appsByKey.get(key);
        if (a != null) launch(a);
    }

    private void launch(App app) {
        if (getPackageName().equals(app.component.getPackageName())) {
            Sheet.closeAll();
            showHelp();
            return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Ui.toast(root, "Açmaq olmadı");
            loadApps();
            return;
        }
        usage.hit(app.key);
        hideKeyboard();
    }

    private void openAppInfo(String key) {
        ComponentName cn = ComponentName.unflattenFromString(key);
        if (cn == null) return;
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + cn.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) { }
    }

    private void uninstall(String key) {
        ComponentName cn = ComponentName.unflattenFromString(key);
        if (cn == null) return;
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + cn.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) { }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
