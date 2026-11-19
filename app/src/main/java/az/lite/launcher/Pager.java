package az.lite.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

/** Çox sadə üfüqi səhifələyici (AndroidX ViewPager əvəzinə, ~120 sətir). */
public class Pager extends ViewGroup {

    public interface Listener {
        void onPageChanged(int page);
    }

    private final Scroller scroller;
    private final int touchSlop;
    private final int minFling;
    private VelocityTracker velocity;
    private float downX, downY, lastX;
    private boolean dragging;
    private int page;
    private Listener listener;

    public Pager(Context context, AttributeSet attrs) {
        super(context, attrs);
        scroller = new Scroller(context, new DecelerateInterpolator(2f));
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity() * 4;
    }

    public void setListener(Listener l) { listener = l; }
    public int getPage() { return page; }
    public boolean isDragging() { return dragging; }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        int cw = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
        int ch = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(cw, ch);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).layout(i * w, 0, i * w + w, h);
        }
        if (scroller.isFinished()) scrollTo(page * w, 0);
    }

    public void setPage(int p, boolean animate) {
        p = Math.max(0, Math.min(getChildCount() - 1, p));
        boolean changed = p != page;
        page = p;
        int w = getWidth();
        if (!scroller.isFinished()) scroller.abortAnimation();
        if (w > 0) {
            int dx = p * w - getScrollX();
            if (animate && dx != 0) {
                int duration = Math.min(320, 120 + Math.abs(dx) * 250 / w);
                scroller.startScroll(getScrollX(), 0, dx, 0, duration);
                postInvalidateOnAnimation();
            } else {
                scrollTo(p * w, 0);
            }
        }
        if (changed && listener != null) listener.onPageChanged(p);
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            postInvalidateOnAnimation();
        }
    }

    private void track(MotionEvent ev) {
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(ev);
    }

    private void recycleTracker() {
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
    }

    private boolean checkStartDrag(MotionEvent ev) {
        float dx = ev.getX() - downX;
        float dy = ev.getY() - downY;
        if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.3f) {
            dragging = true;
            lastX = ev.getX();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        }
        return dragging;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        track(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = ev.getX();
                downY = ev.getY();
                dragging = !scroller.isFinished();
                if (dragging) scroller.abortAnimation();
                break;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) checkStartDrag(ev);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) recycleTracker();
                break;
        }
        return dragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        track(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = ev.getX();
                downY = ev.getY();
                if (!scroller.isFinished()) {
                    scroller.abortAnimation();
                    dragging = true;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) checkStartDrag(ev);
                if (dragging) {
                    float dx = lastX - ev.getX();
                    lastX = ev.getX();
                    int max = (getChildCount() - 1) * getWidth();
                    int nx = (int) Math.max(0, Math.min(max, getScrollX() + dx));
                    scrollTo(nx, 0);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    int w = Math.max(1, getWidth());
                    int target = Math.round(getScrollX() / (float) w);
                    if (velocity != null && ev.getActionMasked() == MotionEvent.ACTION_UP) {
                        velocity.computeCurrentVelocity(1000);
                        float v = velocity.getXVelocity();
                        if (v < -minFling) target = getScrollX() / w + 1;
                        else if (v > minFling) target = getScrollX() / w;
                    }
                    dragging = false;
                    setPage(target, true);
                }
                recycleTracker();
                return true;
        }
        return true;
    }

    @Override
    public boolean shouldDelayChildPressedState() { return true; }

    /** Uşaq View-larda istifadə üçün. */
    @SuppressWarnings("unused")
    static boolean isPagerChild(View v) { return v.getParent() instanceof Pager; }
}
