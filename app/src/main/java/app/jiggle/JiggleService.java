package app.jiggle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.wallpaper.WallpaperService;
import android.view.Choreographer;
import android.view.SurfaceHolder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class JiggleService extends WallpaperService {
    static final int MW = 28, MH = 56;

    @Override public Engine onCreateEngine() { return new E(); }

    class E extends Engine implements SensorEventListener, Choreographer.FrameCallback {
        SensorManager sm;
        Sensor acc;
        boolean vis;
        int W, H;
        Bitmap bmp;
        List<Cfg.C> cs = new ArrayList<>();
        float k = 1, par = 1;
        boolean clk, inv;
        final float[] gv = new float[3];
        boolean gInit, bInit;
        float linx, liny, bx, by;
        final float[] ox = new float[8], oy = new float[8], vx = new float[8], vy = new float[8], sq = new float[8];
        float px, py;
        long lastDraw, tSec;
        String tStr = "";
        final float[] verts = new float[(MW + 1) * (MH + 1) * 2];
        final Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override public void onCreate(SurfaceHolder h) {
            super.onCreate(h);
            sm = (SensorManager) getSystemService(SENSOR_SERVICE);
            acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        void load() {
            SharedPreferences sp = Cfg.sp(JiggleService.this);
            k = sp.getFloat("k", 1f);
            par = sp.getFloat("p", 1f);
            clk = sp.getBoolean("clk", false);
            inv = sp.getBoolean("inv", false);
            cs = Cfg.circles(JiggleService.this);
            if (W > 0 && H > 0) {
                if (bmp != null) bmp.recycle();
                bmp = Cfg.cover(JiggleService.this, W, H);
            }
        }

        @Override public void onSurfaceChanged(SurfaceHolder h, int f, int w, int hh) {
            super.onSurfaceChanged(h, f, w, hh);
            W = w;
            H = hh;
            load();
        }

        @Override public void onVisibilityChanged(boolean v) {
            vis = v;
            Choreographer.getInstance().removeFrameCallback(this);
            if (v) {
                load();
                bInit = false;
                lastDraw = 0;
                if (acc != null) sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME);
                Choreographer.getInstance().postFrameCallback(this);
            } else {
                sm.unregisterListener(this);
            }
        }

        @Override public void onDestroy() {
            vis = false;
            sm.unregisterListener(this);
            Choreographer.getInstance().removeFrameCallback(this);
            super.onDestroy();
        }

        @Override public void onSensorChanged(SensorEvent e) {
            float[] v = e.values;
            if (!gInit) { gv[0] = v[0]; gv[1] = v[1]; gv[2] = v[2]; gInit = true; }
            for (int i = 0; i < 3; i++) gv[i] = 0.92f * gv[i] + 0.08f * v[i];
            linx = v[0] - gv[0];
            liny = v[1] - gv[1];
        }

        @Override public void onAccuracyChanged(Sensor s, int a) { }

        @Override public void doFrame(long ns) {
            if (!vis) return;
            Choreographer.getInstance().postFrameCallback(this);
            if (ns - lastDraw < 18000000L) return;
            float dt = lastDraw == 0 ? 0.016f : Math.min(0.033f, (ns - lastDraw) / 1e9f);
            lastDraw = ns;
            step(dt);
            draw();
        }

        void step(float dt) {
            float s = inv ? -1f : 1f;
            float gdx = Math.max(-1.2f, Math.min(1.2f, -gv[0] / 9.81f));
            float gdy = Math.max(-1.2f, Math.min(1.2f, gv[1] / 9.81f));
            if (!bInit) { bx = gdx; by = gdy; bInit = true; }
            bx += (gdx - bx) * dt * 0.25f;
            by += (gdy - by) * dt * 0.25f;
            float rx = s * (gdx - bx), ry = s * (gdy - by);
            px = Math.max(-0.5f, Math.min(0.5f, rx)) * par * 0.04f * W;
            py = Math.max(-0.5f, Math.min(0.5f, ry)) * par * 0.04f * H;
            float tgx = rx * 0.05f * k, tgy = ry * 0.05f * k;
            float ax = linx * s, ay = -liny * s;
            int n = Math.min(cs.size(), 8);
            for (int i = 0; i < n; i++) {
                float w0 = 9.5f * (1 + 0.06f * i), zt = 0.07f;
                vx[i] += (-w0 * w0 * (ox[i] - tgx) - 2 * zt * w0 * vx[i] - 0.03f * k * ax) * dt;
                vy[i] += (-w0 * w0 * (oy[i] - tgy) - 2 * zt * w0 * vy[i] - 0.05f * k * ay) * dt;
                ox[i] = 0.05f * (float) Math.tanh((ox[i] + vx[i] * dt) / 0.05f);
                oy[i] = 0.05f * (float) Math.tanh((oy[i] + vy[i] * dt) / 0.05f);
                sq[i] = Math.max(-0.07f, Math.min(0.07f, vy[i] * 0.2f));
            }
        }

        void draw() {
            SurfaceHolder h = getSurfaceHolder();
            Canvas c = null;
            try {
                c = Build.VERSION.SDK_INT >= 26 ? h.lockHardwareCanvas() : h.lockCanvas();
                if (c != null) render(c);
            } catch (Exception ex) {
            } finally {
                if (c != null) {
                    try { h.unlockCanvasAndPost(c); } catch (Exception ex) { }
                }
            }
        }

        void render(Canvas c) {
            c.drawColor(Color.BLACK);
            if (bmp == null) {
                tp.setColor(0xFFAAAAAA);
                tp.setTextAlign(Paint.Align.CENTER);
                tp.setTextSize(W * 0.05f);
                c.drawText("Відкрий застосунок і обери фото", W / 2f, H / 2f, tp);
                return;
            }
            final float z = 1.06f;
            int n = Math.min(cs.size(), 8);
            int idx = 0;
            for (int j = 0; j <= MH; j++) {
                float y = j * H / (float) MH;
                for (int i = 0; i <= MW; i++) {
                    float x = i * W / (float) MW;
                    float dx = 0, dy = 0;
                    for (int q = 0; q < n; q++) {
                        Cfg.C ci = cs.get(q);
                        float cx = ci.x * W, cy = ci.y * H, R = ci.r * W * 2.7f;
                        float ddx = x - cx, ddy = y - cy;
                        float xr = (float) Math.sqrt(ddx * ddx + ddy * ddy) / R;
                        if (xr >= 1f) continue;
                        float f = (float) ((Math.exp(-3 * xr * xr) - 0.05) / 0.95);
                        if (f <= 0) continue;
                        dx += (ox[q] * W - ddx * 0.5f * sq[q] + (ddy / H) * ox[q] * 2f * W) * f;
                        dy += (oy[q] * H + ddy * sq[q]) * f;
                    }
                    verts[idx++] = W / 2f + (x - W / 2f) * z + dx + px;
                    verts[idx++] = H / 2f + (y - H / 2f) * z + dy + py;
                }
            }
            c.drawBitmapMesh(bmp, MW, MH, verts, 0, null, 0, bp);
            if (clk) {
                long sec = System.currentTimeMillis() / 1000;
                if (sec != tSec) {
                    tSec = sec;
                    tStr = new java.text.SimpleDateFormat("HHmm", Locale.US).format(new Date());
                }
                tp.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                tp.setTextAlign(Paint.Align.CENTER);
                tp.setTextSize(300);
                float sz = 300 * W * 0.92f / tp.measureText(tStr);
                tp.setTextSize(sz);
                tp.setColor(0x9EBEA5FF);
                c.drawText(tStr, W / 2f - px * 0.5f, H * 0.1f + sz * 0.74f, tp);
            }
        }
    }
}
