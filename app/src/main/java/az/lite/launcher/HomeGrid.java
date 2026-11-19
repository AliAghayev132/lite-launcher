package az.lite.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ana ekran şəbəkəsi: 4 sütun, ekran hündürlüyünə görə sətirlər.
 * Hər element öz xanasındadır, boş xanalar boş qalır.
 * Uzun bas → sürüklə → istənilən xanaya burax (dolu xanada yerlər dəyişir).
 * Uzun bas və tərpətmədən burax → menyu.
 */
public class HomeGrid extends ViewGroup {

    public interface Callbacks {
        View createItemView(ViewGroup parent);
        void bindItemView(View v, Store.Item item);
        void onItemClick(Store.Item item);
        void onItemMenu(Store.Item item);
        void onEmptyLongPress(int cell);
        void onItemsMoved();
        void onQuickClick();
        void onQuickLongPress();
    }

    static final int COLS = 4;

    private Callbacks cb;
    private List<Store.Item> items = new ArrayList<>();
    private int rows = 6;
    private int cellW, cellH;
    private final int touchSlop, longPressTimeout, prefCellH;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint hoverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hoverFill = new Paint();

    private float downX, downY;
    private int downCell = -1;
    private View downView;
    private boolean longFired, dragging, moved;
    private View dragView;
    private Store.Item dragItem;
    private int hoverCell = -1;

    /** Ən aşağı sətrin ortadakı 2 xanası — "sürətli" düyməsi üçün ayrılıb. */
    final Set<Integer> reserved = new HashSet<>();
    private View quickView;
    private boolean downOnQuick;

    private final Runnable longPress = this::onLongPress;

    public HomeGrid(Context c, AttributeSet a) {
        super(c, a);
        ViewConfiguration vc = ViewConfiguration.get(c);
        touchSlop = vc.getScaledTouchSlop();
        longPressTimeout = ViewConfiguration.getLongPressTimeout();
        prefCellH = Ui.dp(c, 98);
        setWillNotDraw(false);
        setClipChildren(false);
        hoverPaint.setStyle(Paint.Style.STROKE);
        hoverPaint.setStrokeWidth(Ui.dp(c, 2));
        hoverPaint.setColor(Ui.ACCENT);
        hoverFill.setColor(0x33FFD60A);
    }

    void setCallbacks(Callbacks c) { cb = c; }

    void setQuickView(View v) {
        quickView = v;
        rebuild();
    }

    private boolean isQuickCell(int cell) { return reserved.contains(cell); }

    void setItems(List<Store.Item> list) {
        items = list;
        rebuild();
    }

    /** Bütün elementləri yenidən yaradır (element əlavə/silinəndə). */
    void rebuild() {
        if (dragging || cb == null) return;
        removeAllViews();
        if (quickView != null) addView(quickView);
        for (Store.Item it : items) {
            View v = cb.createItemView(this);
            v.setTag(it);
            cb.bindItemView(v, it);
            addView(v);
        }
        requestLayout();
    }

    /** Yalnız görünüşü yeniləyir (ikonlar yüklənəndə). */
    void rebind() {
        if (dragging || cb == null) return;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v == quickView) continue;
            cb.bindItemView(v, (Store.Item) v.getTag());
        }
    }

    boolean isDragging() { return dragging; }

    void cancelDrag() {
        handler.removeCallbacks(longPress);
        if (dragging) finishDrag(false);
    }

    /** Son "normal" (klaviatura açıq olmayan) ölçü — sətir sayı yalnız ondan hesablanır. */
    private int stableW = -1, stableH = -1;

    private boolean imeVisible() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsets wi = getRootWindowInsets();
            return wi != null && wi.isVisible(android.view.WindowInsets.Type.ime());
        }
        return false;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        int innerW = w - getPaddingLeft() - getPaddingRight();
        int innerH = h - getPaddingTop() - getPaddingBottom();
        // Klaviatura açılanda (adjustResize) ekran müvəqqəti kiçilir. Bu zaman sətirləri
        // yenidən hesablasaq, "sürətli" xanalar yuxarı sürüşür və elementlərin yeri pozulub
        // yaddaşa yazılırdı. Ona görə yalnız normal ölçüdə hesablayırıq.
        boolean tempSize = h <= 0 || innerH <= 0
                || (stableH > 0 && w == stableW && (imeVisible() || innerH < stableH * 0.75f));
        if (!tempSize) {
            stableW = w;
            stableH = innerH;
        }
        int useH = stableH > 0 ? stableH : Math.max(1, innerH);
        cellW = Math.max(1, innerW / COLS);
        int newRows = Math.max(1, useH / prefCellH);
        cellH = Math.max(1, useH / newRows);
        int cw = MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY);
        int ch = MeasureSpec.makeMeasureSpec(Math.min(cellH, prefCellH), MeasureSpec.EXACTLY);
        boolean rowsChanged = newRows != rows || reserved.isEmpty();
        rows = newRows;
        if (rowsChanged) {
            reserved.clear();
            reserved.add((rows - 1) * COLS + 1);
            reserved.add((rows - 1) * COLS + 2);
        }
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v == quickView) {
                v.measure(MeasureSpec.makeMeasureSpec(cellW * 2, MeasureSpec.EXACTLY), ch);
            } else {
                v.measure(cw, ch);
            }
        }
        setMeasuredDimension(w, h);
        if (!tempSize) {
            // ölçü bir neçə dəfə dəyişə bilər (insets, status bar) — son ölçü sabitləşəndə yoxla
            removeCallbacks(ensureRun);
            postDelayed(ensureRun, 300);
        }
    }

    private final Runnable ensureRun = this::ensurePositions;

    /** Mövqesi olmayan, üst-üstə düşən və ya ekrandan kənarda qalan elementlərə boş xana verir. */
    private void ensurePositions() {
        if (dragging) return;
        int cells = rows * COLS;
        Set<Integer> used = new HashSet<>(reserved);
        List<Store.Item> need = new ArrayList<>();
        for (Store.Item it : items) {
            if (it.pos >= 0 && it.pos < cells && used.add(it.pos)) continue;
            need.add(it);
        }
        if (need.isEmpty()) return;
        for (Store.Item it : need) {
            int p = 0;
            while (used.contains(p)) p++;
            it.pos = p;
            used.add(p);
        }
        requestLayout();
        if (cb != null) cb.onItemsMoved();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v == quickView) {
                int x = getPaddingLeft() + cellW;
                int y = getPaddingTop() + (rows - 1) * cellH + (cellH - v.getMeasuredHeight()) / 2;
                v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
                continue;
            }
            Store.Item it = (Store.Item) v.getTag();
            int col = it.pos % COLS, row = it.pos / COLS;
            int x = getPaddingLeft() + col * cellW;
            int y = getPaddingTop() + row * cellH + (cellH - v.getMeasuredHeight()) / 2;
            v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
        }
    }

    private int cellAt(float x, float y) {
        int col = (int) ((x - getPaddingLeft()) / cellW);
        int row = (int) ((y - getPaddingTop()) / cellH);
        col = Math.max(0, Math.min(COLS - 1, col));
        row = Math.max(0, Math.min(rows - 1, row));
        return row * COLS + col;
    }

    private Store.Item itemAt(int cell) {
        for (Store.Item it : items) if (it.pos == cell) return it;
        return null;
    }

    private View viewFor(Store.Item item) {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) != quickView && getChildAt(i).getTag() == item) return getChildAt(i);
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        float x = ev.getX(), y = ev.getY();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downX = x;
                downY = y;
                downCell = cellAt(x, y);
                downOnQuick = quickView != null && isQuickCell(downCell);
                Store.Item it = downOnQuick ? null : itemAt(downCell);
                downView = downOnQuick ? quickView : it != null ? viewFor(it) : null;
                longFired = dragging = moved = false;
                if (downView != null) downView.setAlpha(0.6f);
                handler.postDelayed(longPress, longPressTimeout);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float dx = x - downX, dy = y - downY;
                boolean far = Math.hypot(dx, dy) > touchSlop;
                if (dragging) {
                    dragView.setTranslationX(dx);
                    dragView.setTranslationY(dy);
                    if (far) moved = true;
                    int c = cellAt(x, y);
                    if (c != hoverCell) {
                        hoverCell = c;
                        invalidate();
                    }
                } else if (!longFired && far) {
                    handler.removeCallbacks(longPress);
                    resetPressed();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                handler.removeCallbacks(longPress);
                boolean tap = !longFired && Math.hypot(x - downX, y - downY) <= touchSlop;
                if (dragging) {
                    finishDrag(true);
                } else if (tap && downOnQuick) {
                    resetPressed();
                    cb.onQuickClick();
                } else if (tap && downView != null) {
                    resetPressed();
                    cb.onItemClick((Store.Item) downView.getTag());
                }
                resetPressed();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPress);
                if (dragging) finishDrag(false);
                resetPressed();
                return true;
        }
        return true;
    }

    private void onLongPress() {
        longFired = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (downOnQuick) {
            if (downView != null) downView.setAlpha(1f);
            cb.onQuickLongPress();
            return;
        }
        if (downView == null) {
            cb.onEmptyLongPress(downCell);
            return;
        }
        dragging = true;
        dragItem = (Store.Item) downView.getTag();
        dragView = downView;
        dragView.setAlpha(1f);
        dragView.setTranslationZ(Ui.dp(getContext(), 8));
        dragView.animate().scaleX(1.12f).scaleY(1.12f).setDuration(100).start();
        hoverCell = downCell;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        invalidate();
    }

    private void finishDrag(boolean drop) {
        View v = dragView;
        Store.Item it = dragItem;
        dragging = false;
        dragView = null;
        dragItem = null;
        if (v != null) {
            v.animate().cancel();
            v.setTranslationX(0);
            v.setTranslationY(0);
            v.setTranslationZ(0);
            v.setScaleX(1f);
            v.setScaleY(1f);
            v.setAlpha(1f);
        }
        if (drop && it != null) {
            if (!moved) {
                cb.onItemMenu(it);
            } else if (hoverCell >= 0 && hoverCell != it.pos && !isQuickCell(hoverCell)) {
                Store.Item other = itemAt(hoverCell);
                if (other != null) other.pos = it.pos;   // dolu xana → yer dəyişmə
                it.pos = hoverCell;
                requestLayout();
                cb.onItemsMoved();
            }
        }
        hoverCell = -1;
        invalidate();
    }

    private void resetPressed() {
        if (downView != null && !dragging) downView.setAlpha(1f);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!dragging || hoverCell < 0 || isQuickCell(hoverCell)) return;
        int col = hoverCell % COLS, row = hoverCell / COLS;
        int inset = Ui.dp(getContext(), 4);
        float l = getPaddingLeft() + col * cellW + inset;
        float t = getPaddingTop() + row * cellH + inset;
        float r = l + cellW - 2 * inset;
        float b = t + cellH - 2 * inset;
        c.drawRect(l, t, r, b, hoverFill);
        c.drawRect(l, t, r, b, hoverPaint);
    }
}
