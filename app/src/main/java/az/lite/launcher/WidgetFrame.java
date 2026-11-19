package az.lite.launcher;

import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/** Vidcet qabı: vidcetin öz toxunuşları işləyir, amma uzun basma bizim menyunu açır. */
class WidgetFrame extends FrameLayout {

    private final int slop;
    private float downX, downY;
    private boolean fired;
    private Runnable onLongPress;
    private final Runnable check = () -> {
        fired = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (onLongPress != null) onLongPress.run();
    };

    WidgetFrame(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    void setOnLongPress(Runnable r) { onLongPress = r; }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                fired = false;
                postDelayed(check, ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(ev.getX() - downX, ev.getY() - downY) > slop) removeCallbacks(check);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(check);
                break;
        }
        return fired; // uzun basma işlədisə qalan toxunuşları vidcetə vermə
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_UP || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(check);
        }
        return fired;
    }
}
