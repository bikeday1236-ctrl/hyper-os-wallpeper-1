package app.jiggle;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class Cfg {
    static class C {
        float x, y, r;
        C(float x, float y, float r) { this.x = x; this.y = y; this.r = r; }
    }

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("cfg", 0); }

    static List<C> circles(Context c) {
        List<C> l = new ArrayList<>();
        String s = sp(c).getString("c", "");
        if (s.isEmpty()) return l;
        for (String p : s.split(";")) {
            String[] a = p.split(",");
            if (a.length == 3) {
                try { l.add(new C(Float.parseFloat(a[0]), Float.parseFloat(a[1]), Float.parseFloat(a[2]))); }
                catch (Exception e) { }
            }
        }
        return l;
    }

    static void save(Context c, List<C> l) {
        StringBuilder b = new StringBuilder();
        for (C k : l) {
            if (b.length() > 0) b.append(';');
            b.append(k.x).append(',').append(k.y).append(',').append(k.r);
        }
        sp(c).edit().putString("c", b.toString()).apply();
    }

    static File img(Context c) { return new File(c.getFilesDir(), "wall.jpg"); }

    /** Фото, обрізане "cover" під розмір W x H. */
    static Bitmap cover(Context c, int W, int H) {
        File f = img(c);
        if (!f.exists() || W <= 0 || H <= 0) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int ss = 1;
        while (o.outWidth / (ss * 2) >= W && o.outHeight / (ss * 2) >= H) ss *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = ss;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b == null) return null;
        float s = Math.max((float) W / b.getWidth(), (float) H / b.getHeight());
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Matrix m = new Matrix();
        m.postScale(s, s);
        m.postTranslate((W - b.getWidth() * s) / 2f, (H - b.getHeight() * s) / 2f);
        cv.drawBitmap(b, m, new Paint(Paint.FILTER_BITMAP_FLAG));
        b.recycle();
        return out;
    }
}
