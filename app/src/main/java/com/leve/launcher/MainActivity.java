package com.leve.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Launcher minimo para Android TV box com pouca RAM. Sem libs, sem animacoes. */
public class MainActivity extends Activity {

    // Pacotes que voce NAO quer ver na tela inicial (edite a vontade)
    private static final String[] HIDDEN = {
            "com.droidlogic.mboxlauncher",
            "com.android.documentsui",
            "com.android.providers.downloads.ui",
            "com.android.apkinstaller",
            "com.android.assistant.setting.iknew.rk"
    };
    private static final int COLUNAS = 6;
    private static final int ICON_PX = 96;

    private static class Item { String label, pkg; ComponentName cn; Bitmap icon; }

    private final List<Item> items = new ArrayList<Item>();
    private BaseAdapter adapter;
    private float dp;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { carregar(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dpi(48), dpi(24), dpi(48), dpi(16));

        TextClock hora = new TextClock(this);
        hora.setFormat24Hour("HH:mm");
        hora.setFormat12Hour("HH:mm");
        hora.setTextColor(Color.WHITE);
        hora.setTextSize(44);
        root.addView(hora);

        TextClock data = new TextClock(this);
        data.setFormat24Hour("EEEE, d 'de' MMMM");
        data.setFormat12Hour("EEEE, d 'de' MMMM");
        data.setTextColor(0xFF9E9E9E);
        data.setTextSize(18);
        data.setPadding(0, 0, 0, dpi(24));
        root.addView(data);

        GridView grid = new GridView(this);
        grid.setNumColumns(COLUNAS);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dpi(12));
        grid.setHorizontalSpacing(dpi(12));
        grid.setSelector(selector());
        grid.setDrawSelectorOnTop(false);
        grid.setVerticalScrollBarEnabled(false);
        grid.setClipToPadding(false);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                abrir(items.get(pos));
            }
        });
        // segurar OK = informacoes do app (limpar cache, forcar parada, desinstalar)
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + items.get(pos).pkg)));
                } catch (Exception ignored) { }
                return true;
            }
        });
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        grid.requestFocus();
        carregar();
    }

    @Override protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
    }

    @Override public void onBackPressed() { /* launcher nao fecha */ }

    private void abrir(Item it) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setComponent(it.cn);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Exception ignored) { }
    }

    private void carregar() {
        new Thread(new Runnable() {
            @Override public void run() {
                PackageManager pm = getPackageManager();
                Set<String> hidden = new HashSet<String>();
                Collections.addAll(hidden, HIDDEN);
                Set<String> vistos = new HashSet<String>();
                final List<Item> novos = new ArrayList<Item>();
                String[] cats = { Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER };
                for (String cat : cats) {
                    Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                    for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                        String p = ri.activityInfo.packageName;
                        if (p.equals(getPackageName()) || hidden.contains(p) || !vistos.add(p)) continue;
                        Item it = new Item();
                        it.pkg = p;
                        it.cn = new ComponentName(p, ri.activityInfo.name);
                        it.label = String.valueOf(ri.loadLabel(pm));
                        it.icon = reduzir(ri.loadIcon(pm));
                        novos.add(it);
                    }
                }
                Collections.sort(novos, new Comparator<Item>() {
                    @Override public int compare(Item a, Item b) {
                        return a.label.compareToIgnoreCase(b.label);
                    }
                });
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        items.clear();
                        items.addAll(novos);
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    /** Reduz o icone para 96x96 para economizar RAM. */
    private Bitmap reduzir(Drawable d) {
        Bitmap bmp = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, ICON_PX, ICON_PX);
        d.draw(c);
        return bmp;
    }

    private GradientDrawable selector() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x33FFFFFF);
        g.setStroke(dpi(2), Color.WHITE);
        g.setCornerRadius(dpi(10));
        return g;
    }

    private int dpi(int v) { return (int) (v * dp); }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int pos, View v, ViewGroup parent) {
            LinearLayout box;
            ImageView img;
            TextView txt;
            if (v == null) {
                box = new LinearLayout(MainActivity.this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setGravity(Gravity.CENTER_HORIZONTAL);
                box.setPadding(dpi(8), dpi(12), dpi(8), dpi(10));
                img = new ImageView(MainActivity.this);
                img.setLayoutParams(new LinearLayout.LayoutParams(dpi(56), dpi(56)));
                txt = new TextView(MainActivity.this);
                txt.setTextColor(Color.WHITE);
                txt.setTextSize(14);
                txt.setSingleLine(true);
                txt.setGravity(Gravity.CENTER);
                txt.setPadding(0, dpi(6), 0, 0);
                box.addView(img);
                box.addView(txt);
                box.setTag(new View[]{ img, txt });
                v = box;
            }
            View[] t = (View[]) v.getTag();
            Item it = items.get(pos);
            ((ImageView) t[0]).setImageBitmap(it.icon);
            ((TextView) t[1]).setText(it.label);
            return v;
        }
    }
}
