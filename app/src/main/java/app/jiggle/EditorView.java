package app.jiggle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import java.util.List;

class EditorView extends View {
    Bitmap bmp;
    List<Cfg.C> cs;
    int sel = -1, drag = -1;
    float defR = 0.2f;
    boolean pend;
    float x0, y0;
    Runnable onSel;
    final Paint pr = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG);
    final Paint tx = new Paint(Paint.ANTI_ALIAS_FLAG);
    final float dp;

    EditorView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        cs = Cfg.circles(c);
        pr.setStyle(Paint.Style.STROKE);
        tx.setColor(0xFFAAAAAA);
        tx.setTextAlign(Paint.Align.CENTER);
        tx.setTextSize(16 * dp);
    }

    void reload() {
        bmp = Cfg.cover(getContext(), getWidth(), getHeight());
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { reload(); }

    @Override protected void onMeasure(int wm, int hm) {
        int w0 = MeasureSpec.getSize(wm), h0 = MeasureSpec.getSize(hm);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float ratio = (float) dm.heightPixels / dm.widthPixels;
        int w, h;
        if (w0 * ratio <= h0) { w = w0; h = (int) (w0 * ratio); }
        else { h = h0; w = (int) (h0 / ratio); }
        setMeasuredDimension(w, h);
    }

    @Override protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        c.drawColor(0xFF000000);
        if (bmp != null) {
            c.save();
            c.scale(1.06f, 1.06f, W / 2f, H / 2f);
            c.drawBitmap(bmp, 0, 0, bp);
            c.restore();
        } else {
            c.drawText("Обери фото", W / 2f, H / 2f, tx);
        }
        for (int i = 0; i < cs.size(); i++) {
            Cfg.C k = cs.get(i);
            float cx = k.x * W, cy = k.y * H, r = k.r * W;
            pr.setStrokeWidth(3 * dp);
            pr.setColor(i == sel ? 0xFF80EBFF : 0xFFFFFFFF);
            c.drawCircle(cx, cy, r, pr);
            pr.setStrokeWidth(1.5f * dp);
            pr.setColor(0x66FFFFFF);
            c.drawCircle(cx, cy, r * 2.7f, pr);
        }
    }

    static float cl(float v) { return Math.max(0f, Math.min(1f, v)); }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int W = getWidth(), H = getHeight();
        float x = cl(e.getX() / W), y = cl(e.getY() / H);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                drag = -1;
                pend = false;
                for (int i = cs.size() - 1; i >= 0; i--) {
                    Cfg.C k = cs.get(i);
                    double d = Math.hypot((x - k.x) * W, (y - k.y) * H);
                    if (d < k.r * W) { sel = i; drag = i; break; }
                }
                if (drag < 0) { pend = true; x0 = e.getX(); y0 = e.getY(); }
                if (onSel != null) onSel.run();
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (drag >= 0) { cs.get(drag).x = x; cs.get(drag).y = y; invalidate(); }
                if (pend && Math.hypot(e.getX() - x0, e.getY() - y0) > 10 * dp) pend = false;
                return true;
            case MotionEvent.ACTION_UP:
                if (pend && cs.size() < 8) {
                    cs.add(new Cfg.C(x, y, defR));
                    sel = cs.size() - 1;
                    if (onSel != null) onSel.run();
                }
                pend = false;
                drag = -1;
                Cfg.save(getContext(), cs);
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pend = false;
                drag = -1;
                return true;
        }
        return super.onTouchEvent(e);
    }

    void deleteSel() {
        if (sel >= 0 && sel < cs.size()) { cs.remove(sel); sel = -1; Cfg.save(getContext(), cs); invalidate(); }
    }

    void clearAll() { cs.clear(); sel = -1; Cfg.save(getContext(), cs); invalidate(); }

    void setR(float r) {
        defR = r;
        if (sel >= 0 && sel < cs.size()) { cs.get(sel).r = r; Cfg.save(getContext(), cs); }
        invalidate();
    }
}
