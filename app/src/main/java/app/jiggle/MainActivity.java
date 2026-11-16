package app.jiggle;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {
    static final int PICK = 1;
    EditorView ed;
    SeekBar sbR;
    SharedPreferences sp;
    float dens;

    static abstract class SL implements SeekBar.OnSeekBarChangeListener {
        public void onStartTrackingTouch(SeekBar s) { }
        public void onStopTrackingTouch(SeekBar s) { }
    }

    TextView label(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(0xFFCCCCDD);
        v.setTextSize(14);
        v.setPadding(0, (int) (10 * dens), 0, 0);
        return v;
    }

    Button btn(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        return b;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = Cfg.sp(this);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        dens = dm.density;
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF111018);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pd = (int) (14 * dens);
        root.setPadding(pd, pd * 2, pd, pd);
        sv.addView(root);

        TextView hint = label("Обери фото. Торкнись фото, щоб поставити коло, тягни, щоб перемістити. Тонке зовнішнє кільце показує зону впливу.");
        root.addView(hint);

        ed = new EditorView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (dm.heightPixels * 0.55f));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = pd;
        root.addView(ed, lp);

        Button pick = btn("Обрати фото");
        pick.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(i, PICK);
        });
        root.addView(pick);

        LinearLayout row = new LinearLayout(this);
        Button del = btn("Видалити коло");
        del.setOnClickListener(v -> ed.deleteSel());
        Button clr = btn("Очистити кола");
        clr.setOnClickListener(v -> ed.clearAll());
        row.addView(del, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(clr, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(row);

        root.addView(label("Розмір вибраного кола"));
        sbR = new SeekBar(this);
        sbR.setMax(40);
        sbR.setProgress(20);
        sbR.setOnSeekBarChangeListener(new SL() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                if (u) ed.setR(Math.max(5, p) / 100f);
            }
        });
        root.addView(sbR);
        ed.onSel = () -> {
            if (ed.sel >= 0 && ed.sel < ed.cs.size()) sbR.setProgress(Math.round(ed.cs.get(ed.sel).r * 100));
        };

        root.addView(label("Сила пружини"));
        root.addView(seek("k", 200, 100));
        root.addView(label("Паралакс (зсув від нахилу)"));
        root.addView(seek("p", 200, 100));

        CheckBox clk = new CheckBox(this);
        clk.setText("Показувати великий годинник");
        clk.setTextColor(0xFFCCCCDD);
        clk.setChecked(sp.getBoolean("clk", false));
        clk.setOnCheckedChangeListener((v, c) -> sp.edit().putBoolean("clk", c).apply());
        root.addView(clk);

        CheckBox inv = new CheckBox(this);
        inv.setText("Інвертувати напрям нахилу");
        inv.setTextColor(0xFFCCCCDD);
        inv.setChecked(sp.getBoolean("inv", false));
        inv.setOnCheckedChangeListener((v, c) -> sp.edit().putBoolean("inv", c).apply());
        root.addView(inv);

        Button apply = btn("Встановити як живі шпалери");
        apply.setOnClickListener(v -> applyWallpaper());
        root.addView(apply);

        Button alt = btn("Не спрацювало? Відкрити список живих шпалер");
        alt.setOnClickListener(v -> openChooser());
        root.addView(alt);

        setContentView(sv);
    }

    SeekBar seek(final String key, int max, int def) {
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.setProgress(Math.round(sp.getFloat(key, def / 100f) * 100));
        s.setOnSeekBarChangeListener(new SL() {
            public void onProgressChanged(SeekBar sb, int p, boolean u) {
                sp.edit().putFloat(key, p / 100f).apply();
            }
        });
        return s;
    }

    void applyWallpaper() {
        if (!Cfg.img(this).exists()) { Toast.makeText(this, "Спочатку обери фото", Toast.LENGTH_SHORT).show(); return; }
        try {
            Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(this, JiggleService.class));
            startActivity(i);
        } catch (Exception e) {
            openChooser();
        }
    }

    void openChooser() {
        Intent[] tries = {
            new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER),
            new Intent().setComponent(new ComponentName("com.android.wallpaper.livepicker",
                "com.android.wallpaper.livepicker.LiveWallpaperActivity")),
            new Intent(Intent.ACTION_SET_WALLPAPER)
        };
        for (Intent t : tries) {
            try { startActivity(t); return; } catch (Exception e) { }
        }
        Toast.makeText(this, "Не вдалося відкрити вибір шпалер", Toast.LENGTH_LONG).show();
    }

    @Override protected void onActivityResult(int rq, int rs, Intent d) {
        super.onActivityResult(rq, rs, d);
        if (rq == PICK && rs == RESULT_OK && d != null && d.getData() != null) {
            try {
                importImage(d.getData());
                ed.reload();
            } catch (Exception e) {
                Toast.makeText(this, "Не вдалося відкрити фото", Toast.LENGTH_SHORT).show();
            }
        }
    }

    void importImage(Uri u) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
        int ss = 1;
        while (Math.max(o.outWidth, o.outHeight) / ss > 2400) ss *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = ss;
        Bitmap b;
        try (InputStream in = getContentResolver().openInputStream(u)) { b = BitmapFactory.decodeStream(in, null, o); }
        int rot = 0;
        try (InputStream in = getContentResolver().openInputStream(u)) {
            int or = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            if (or == 6) rot = 90; else if (or == 3) rot = 180; else if (or == 8) rot = 270;
        } catch (Exception e) { }
        if (rot != 0) {
            Matrix m = new Matrix();
            m.postRotate(rot);
            b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        }
        try (FileOutputStream fo = new FileOutputStream(Cfg.img(this))) { b.compress(Bitmap.CompressFormat.JPEG, 92, fo); }
    }
}
