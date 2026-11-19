package az.lite.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Şəkli kəsmək üçün: şəkil ortada sığışdırılır, üstündə hərəkət etdirilə və
 * künclərindən dartıla bilən çərçivə. aspect = 0 → sərbəst nisbət.
 */
final class CropView extends View {

    private Bitmap bmp;
    /** Kəsik çərçivə — şəklin öz piksellərində. */
    final RectF crop = new RectF();
    private float aspect;               // en / hündürlük, 0 = sərbəst
    private final Matrix m = new Matrix();
    private float scale = 1, offX, offY;
    private final Paint dim = new Paint();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint();
    private final Paint handle = new Paint();
    private final float touchR, minPx;

    private int mode;                   // 0 yox, 1 hərəkət, 2..5 künclər (TL, TR, BL, BR)
    private float lastX, lastY;

    CropView(Context c) {
        super(c);
        dim.setColor(0xB0000000);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Ui.dp(c, 2));
        line.setColor(0xFFFFFFFF);
        grid.setColor(0x66FFFFFF);
        grid.setStrokeWidth(1);
        handle.setColor(Ui.ACCENT);
        touchR = Ui.dp(c, 30);
        minPx = Ui.dp(c, 40);
        setBackgroundColor(0xFF000000);
    }

    void setBitmap(Bitmap b, RectF initial) {
        bmp = b;
        if (initial != null && initial.width() > 0) {
            crop.set(initial.left * b.getWidth(), initial.top * b.getHeight(),
                    initial.right * b.getWidth(), initial.bottom * b.getHeight());
            clampMove();
        } else {
            crop.set(0, 0, b.getWidth(), b.getHeight());
            if (aspect > 0) applyAspect();
        }
        invalidate();
    }

    Bitmap bitmap() { return bmp; }

    void setAspect(float a) {
        aspect = a;
        if (bmp != null) {
            if (a > 0) applyAspect();
            invalidate();
        }
    }

    /** Mövcud mərkəz ətrafında, sığan ən böyük çərçivə (verilən nisbətlə). */
    private void applyAspect() {
        float cx = crop.centerX(), cy = crop.centerY();
        float bw = bmp.getWidth(), bh = bmp.getHeight();
        float w = bw, h = w / aspect;
        if (h > bh) {
            h = bh;
            w = h * aspect;
        }
        float l = Math.max(0, Math.min(bw - w, cx - w / 2));
        float t = Math.max(0, Math.min(bh - h, cy - h / 2));
        crop.set(l, t, l + w, t + h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        invalidate();
    }

    private void computeMatrix() {
        int w = getWidth(), h = getHeight();
        float pad = Ui.dp(getContext(), 14);
        scale = Math.min((w - 2 * pad) / bmp.getWidth(), (h - 2 * pad) / bmp.getHeight());
        offX = (w - bmp.getWidth() * scale) / 2f;
        offY = (h - bmp.getHeight() * scale) / 2f;
        m.setScale(scale, scale);
        m.postTranslate(offX, offY);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bmp == null || getWidth() == 0) return;
        computeMatrix();
        c.drawBitmap(bmp, m, null);
        float l = offX + crop.left * scale, t = offY + crop.top * scale;
        float r = offX + crop.right * scale, b = offY + crop.bottom * scale;
        float il = offX, it = offY, ir = offX + bmp.getWidth() * scale, ib = offY + bmp.getHeight() * scale;
        c.drawRect(il, it, ir, t, dim);
        c.drawRect(il, b, ir, ib, dim);
        c.drawRect(il, t, l, b, dim);
        c.drawRect(r, t, ir, b, dim);
        float tw = (r - l) / 3f, th = (b - t) / 3f;
        for (int i = 1; i < 3; i++) {
            c.drawLine(l + tw * i, t, l + tw * i, b, grid);
            c.drawLine(l, t + th * i, r, t + th * i, grid);
        }
        c.drawRect(l, t, r, b, line);
        float hs = Ui.dp(getContext(), 14), hw = Ui.dp(getContext(), 4);
        // künc tutacaqları (L şəkilli)
        c.drawRect(l - hw, t - hw, l + hs, t, handle);
        c.drawRect(l - hw, t - hw, l, t + hs, handle);
        c.drawRect(r - hs, t - hw, r + hw, t, handle);
        c.drawRect(r, t - hw, r + hw, t + hs, handle);
        c.drawRect(l - hw, b, l + hs, b + hw, handle);
        c.drawRect(l - hw, b - hs, l, b + hw, handle);
        c.drawRect(r - hs, b, r + hw, b + hw, handle);
        c.drawRect(r, b - hs, r + hw, b + hw, handle);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (bmp == null) return false;
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                float l = offX + crop.left * scale, t = offY + crop.top * scale;
                float r = offX + crop.right * scale, b = offY + crop.bottom * scale;
                mode = 0;
                if (near(x, y, l, t)) mode = 2;
                else if (near(x, y, r, t)) mode = 3;
                else if (near(x, y, l, b)) mode = 4;
                else if (near(x, y, r, b)) mode = 5;
                else if (x > l && x < r && y > t && y < b) mode = 1;
                lastX = x;
                lastY = y;
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mode == 1) {
                    crop.offset((x - lastX) / scale, (y - lastY) / scale);
                    clampMove();
                } else if (mode >= 2) {
                    dragCorner((x - offX) / scale, (y - offY) / scale);
                }
                lastX = x;
                lastY = y;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mode = 0;
                return true;
        }
        return true;
    }

    private boolean near(float x, float y, float px, float py) {
        return Math.abs(x - px) < touchR && Math.abs(y - py) < touchR;
    }

    private void clampMove() {
        float bw = bmp.getWidth(), bh = bmp.getHeight();
        if (crop.width() > bw) crop.right = crop.left + bw;
        if (crop.height() > bh) crop.bottom = crop.top + bh;
        float dx = 0, dy = 0;
        if (crop.left < 0) dx = -crop.left;
        else if (crop.right > bw) dx = bw - crop.right;
        if (crop.top < 0) dy = -crop.top;
        else if (crop.bottom > bh) dy = bh - crop.bottom;
        crop.offset(dx, dy);
    }

    /** px, py — şəklin koordinatlarında barmağın yeri. Qarşı künc sabit qalır. */
    private void dragCorner(float px, float py) {
        float bw = bmp.getWidth(), bh = bmp.getHeight();
        boolean left = mode == 2 || mode == 4, top = mode == 2 || mode == 3;
        float ax = left ? crop.right : crop.left;   // sabit künc
        float ay = top ? crop.bottom : crop.top;
        float min = minPx / scale;
        px = Math.max(0, Math.min(bw, px));
        py = Math.max(0, Math.min(bh, py));
        float w = Math.max(min, left ? ax - px : px - ax);
        float h = Math.max(min, top ? ay - py : py - ay);
        float maxW = left ? ax : bw - ax;
        float maxH = top ? ay : bh - ay;
        if (aspect > 0) {
            w = Math.max(w, h * aspect);
            w = Math.min(w, Math.min(maxW, maxH * aspect));
            h = w / aspect;
        } else {
            w = Math.min(w, maxW);
            h = Math.min(h, maxH);
        }
        float l = left ? ax - w : ax, t = top ? ay - h : ay;
        crop.set(l, t, l + w, t + h);
    }

    /** Normallaşdırılmış (0..1) çərçivə. */
    RectF normalized() {
        return new RectF(crop.left / bmp.getWidth(), crop.top / bmp.getHeight(),
                crop.right / bmp.getWidth(), crop.bottom / bmp.getHeight());
    }
}
