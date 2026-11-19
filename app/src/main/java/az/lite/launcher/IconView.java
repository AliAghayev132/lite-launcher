package az.lite.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

/**
 * Bir View həm tətbiq ikonunu, həm də qovluq ikonunu (solid fon + öz ikonumuz və ya 2x2 mini ikonlar) çəkir.
 * Bitmap yaradılmır — hər şey birbaşa Canvas-a çəkilir.
 */
public class IconView extends View {

    private Drawable app;
    private boolean folder;
    private int bg;
    private Drawable glyph;
    private final Drawable[] minis = new Drawable[4];
    private int miniCount;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public IconView(Context context) { this(context, null); }

    public IconView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void showApp(Drawable d) {
        folder = false;
        app = d;
        invalidate();
    }

    /** glyph == null olduqda içindəki tətbiqlərin mini ikonları göstərilir. */
    public void showFolder(int color, Drawable glyph, Drawable[] icons) {
        folder = true;
        bg = color;
        this.glyph = glyph;
        miniCount = 0;
        if (icons != null) {
            for (Drawable d : icons) {
                if (d != null && miniCount < 4) minis[miniCount++] = d;
            }
        }
        for (int i = miniCount; i < 4; i++) minis[i] = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        int s = Math.min(w, h);
        float left = (w - s) / 2f, top = (h - s) / 2f;

        if (!folder) {
            if (app != null) {
                app.setBounds((int) left, (int) top, (int) left + s, (int) top + s);
                app.draw(c);
            }
            return;
        }

        rect.set(left, top, left + s, top + s);
        bgPaint.setColor(bg);
        c.drawRect(rect, bgPaint);

        if (glyph != null) {
            int g = (int) (s * 0.5f);
            int gl = (int) (left + (s - g) / 2f), gt = (int) (top + (s - g) / 2f);
            glyph.setBounds(gl, gt, gl + g, gt + g);
            glyph.draw(c);
        } else {
            float pad = s * 0.15f, gap = s * 0.08f;
            float cell = (s - 2 * pad - gap) / 2f;
            for (int i = 0; i < miniCount; i++) {
                int col = i % 2, row = i / 2;
                int x = (int) (left + pad + col * (cell + gap));
                int y = (int) (top + pad + row * (cell + gap));
                minis[i].setBounds(x, y, x + (int) cell, y + (int) cell);
                minis[i].draw(c);
            }
        }
    }
}
