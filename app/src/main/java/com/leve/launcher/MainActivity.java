package com.leve.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Launcher leve: relogio, clima, 3 favoritos e grade de apps. Sem animacoes. */
public class MainActivity extends Activity {

    // ===================== CONFIGURACOES (edite aqui) =====================
    // Favoritos iniciais do dock (separados por virgula; "|" = alternativas, usa o primeiro instalado).
    // Depois voce muda pelo proprio launcher: botao "+" e segurar OK sobre um favorito ou app.
    private static final String FAVORITOS_PADRAO =
            "com.google.android.youtube.tv|com.google.android.youtube.googletv,"
            + "com.global.unitviptv,com.spotify.music";
    private static final int MAX_FAVORITOS = 5;
    // Apps que nao aparecem na grade "Aplicativos"
    private static final String[] HIDDEN = {
            "com.droidlogic.mboxlauncher",
            "com.android.documentsui",
            "com.android.providers.downloads.ui",
            "com.android.apkinstaller",
            "com.android.assistant.setting.iknew.rk"
    };
    // Clima (Open-Meteo, gratis e sem chave). Coloque false para desligar.
    private static final boolean MOSTRAR_CLIMA = true;
    private static final double LATITUDE = -8.59;
    private static final double LONGITUDE = -35.11;
    private static final long CLIMA_INTERVALO = 30L * 60L * 1000L;
    private static final int COLUNAS = 6;
    private static final int ICON_PX = 96;
    // ======================================================================

    private static class Item { String label, pkg; ComponentName cn; Bitmap icon; }

    private static class Fav {
        String[] pkgs;
        Intent intent;
        FrameLayout tile;
        ImageView img;
    }

    private final List<Item> items = new ArrayList<Item>();
    private final List<Fav> favs = new ArrayList<Fav>();
    private final Handler handler = new Handler();
    private BaseAdapter adapter;
    private float u;
    private FrameLayout homeLayer;
    private LinearLayout gridLayer;
    private GridView grid;
    private IconButton btnApps, btnConfig;
    private LinearLayout climaBox;
    private TextView climaIcone, climaTemp;
    private boolean primeiraCarga = true;
    private long ultimoClima = 0;
    private SharedPreferences prefs;
    private LinearLayout linha;
    private FrameLayout plusTile;
    private TextView tituloGrade;
    private boolean modoEscolher = false;
    private int focoPendente = -1;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { carregar(); }
    };

    private final Runnable climaTask = new Runnable() {
        @Override public void run() {
            atualizarClima();
            handler.postDelayed(this, CLIMA_INTERVALO);
        }
    };

    private int px(float v) { return (int) (v * u + 0.5f); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        u = Math.max(dm.widthPixels, dm.heightPixels) / 1920f;
        prefs = getSharedPreferences("leve", MODE_PRIVATE);
        lerFavoritos();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        montarInicio();
        montarGrade();
        root.addView(homeLayer, new FrameLayout.LayoutParams(-1, -1));
        root.addView(gridLayer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        mostrarInicio();
        carregar();
    }

    // ------------------------------------------------------------ tela inicial
    private void montarInicio() {
        homeLayer = new FrameLayout(this);

        TextClock hora = new TextClock(this);
        hora.setFormat24Hour("HH:mm");
        hora.setFormat12Hour("HH:mm");
        hora.setTextColor(Color.WHITE);
        hora.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(185));
        hora.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        hora.setIncludeFontPadding(false);
        FrameLayout.LayoutParams lh = new FrameLayout.LayoutParams(-2, -2);
        lh.leftMargin = px(108);
        lh.topMargin = px(80);
        homeLayer.addView(hora, lh);

        TextClock data = new TextClock(this);
        data.setFormat24Hour("dd/MM/yyyy");
        data.setFormat12Hour("dd/MM/yyyy");
        data.setTextColor(Color.WHITE);
        data.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(50));
        data.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        data.setIncludeFontPadding(false);
        FrameLayout.LayoutParams ld = new FrameLayout.LayoutParams(-2, -2);
        ld.leftMargin = px(115);
        ld.topMargin = px(262);
        homeLayer.addView(data, ld);

        // clima (canto superior direito)
        climaBox = new LinearLayout(this);
        climaBox.setOrientation(LinearLayout.HORIZONTAL);
        climaBox.setGravity(Gravity.CENTER_VERTICAL);
        climaIcone = new TextView(this);
        climaIcone.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(105));
        climaIcone.setIncludeFontPadding(false);
        climaIcone.setTextColor(Color.WHITE);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(18), 0, 0, 0);
        TextView hoje = new TextView(this);
        hoje.setText("Hoje");
        hoje.setTextColor(Color.WHITE);
        hoje.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(40));
        hoje.setIncludeFontPadding(false);
        climaTemp = new TextView(this);
        climaTemp.setTextColor(Color.WHITE);
        climaTemp.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(100));
        climaTemp.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        climaTemp.setIncludeFontPadding(false);
        col.addView(hoje);
        col.addView(climaTemp);
        climaBox.addView(climaIcone);
        climaBox.addView(col);
        climaBox.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams lc = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        lc.rightMargin = px(70);
        lc.topMargin = px(85);
        homeLayer.addView(climaBox, lc);

        // linha de baixo: Aplicativos + favoritos
        linha = new LinearLayout(this);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        linha.setGravity(Gravity.TOP);

        btnApps = new IconButton(this, 0);
        btnApps.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { mostrarGrade(); }
        });
        linha.addView(coluna(btnApps, "Aplicativos"));

        reconstruirDock();
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.LEFT);
        ll.leftMargin = px(100);
        ll.bottomMargin = px(100);
        homeLayer.addView(linha, ll);

        // configuracoes (canto inferior direito)
        btnConfig = new IconButton(this, 1);
        btnConfig.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) { }
            }
        });
        FrameLayout.LayoutParams lcfg = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
        lcfg.rightMargin = px(85);
        lcfg.bottomMargin = px(100);
        homeLayer.addView(coluna(btnConfig, "Configura\u00E7\u00F5es"), lcfg);
    }

    private LinearLayout coluna(View botao, String texto) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(botao, new LinearLayout.LayoutParams(px(124), px(124)));
        TextView t = new TextView(this);
        t.setText(texto);
        t.setTextColor(0xFFEEEEEE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(27));
        t.setIncludeFontPadding(false);
        t.setPadding(0, px(14), 0, 0);
        c.addView(t);
        return c;
    }

    // ------------------------------------------------------------ favoritos (dock)
    private void lerFavoritos() {
        String txt = prefs.getString("favs", FAVORITOS_PADRAO);
        favs.clear();
        for (String parte : txt.split(",")) {
            if (parte.trim().length() == 0) continue;
            Fav f = new Fav();
            f.pkgs = parte.trim().split("\\|");
            favs.add(f);
        }
    }

    private void salvarFavoritos() {
        StringBuilder sb = new StringBuilder();
        for (Fav f : favs) {
            if (sb.length() > 0) sb.append(',');
            for (int i = 0; i < f.pkgs.length; i++) {
                if (i > 0) sb.append('|');
                sb.append(f.pkgs[i]);
            }
        }
        prefs.edit().putString("favs", sb.toString()).apply();
    }

    private boolean isFav(String pkg) { return acharFav(pkg) != null; }

    private Fav acharFav(String pkg) {
        for (Fav f : favs) {
            for (String p : f.pkgs) if (p.equals(pkg)) return f;
        }
        return null;
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

    private void criarTile(final Fav f) {
        f.tile = new FrameLayout(this);
        f.tile.setFocusable(true);
        f.tile.setClickable(true);
        f.img = new ImageView(this);
        f.img.setScaleType(ImageView.ScaleType.FIT_XY);
        f.tile.addView(f.img, new FrameLayout.LayoutParams(-1, -1));
        StateListDrawable sl = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setColor(Color.TRANSPARENT);
        on.setStroke(px(5), Color.WHITE);
        on.setCornerRadius(px(14));
        sl.addState(new int[]{ android.R.attr.state_focused }, on);
        sl.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        f.tile.setForeground(sl);
        f.tile.setVisibility(View.GONE);
        f.tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (f.intent != null) abrir(f.intent); }
        });
        f.tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { menuDock(f); return true; }
        });
    }

    private void criarPlus() {
        plusTile = new FrameLayout(this);
        plusTile.setFocusable(true);
        plusTile.setClickable(true);
        TextView t = new TextView(this);
        t.setText("+");
        t.setTextColor(0xFFBBBBBB);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(80));
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        plusTile.addView(t, new FrameLayout.LayoutParams(-1, -1));
        StateListDrawable sl = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setColor(0x33FFFFFF);
        on.setStroke(px(5), Color.WHITE);
        on.setCornerRadius(px(14));
        GradientDrawable off = new GradientDrawable();
        off.setColor(0xFF101010);
        off.setStroke(px(3), 0xFF555555);
        off.setCornerRadius(px(14));
        sl.addState(new int[]{ android.R.attr.state_focused }, on);
        sl.addState(new int[]{}, off);
        plusTile.setBackgroundDrawable(sl);
        plusTile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (favs.size() >= MAX_FAVORITOS) {
                    toast("Dock cheio (m\u00E1ximo " + MAX_FAVORITOS + ")");
                    return;
                }
                modoEscolher = true;
                mostrarGrade();
            }
        });
    }

    private void reconstruirDock() {
        while (linha.getChildCount() > 1) linha.removeViewAt(1);
        for (int i = 0; i < favs.size(); i++) {
            Fav f = favs.get(i);
            if (f.tile == null) criarTile(f);
            LinearLayout.LayoutParams lt = new LinearLayout.LayoutParams(px(230), px(122));
            lt.leftMargin = px(i == 0 ? 38 : 31);
            linha.addView(f.tile, lt);
        }
        if (plusTile == null) criarPlus();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(230), px(122));
        lp.leftMargin = px(favs.isEmpty() ? 38 : 31);
        linha.addView(plusTile, lp);
        plusTile.setVisibility(favs.size() >= MAX_FAVORITOS ? View.GONE : View.VISIBLE);
    }

    private void adicionarFav(String pkg) {
        if (isFav(pkg)) { toast("J\u00E1 est\u00E1 no dock"); return; }
        if (favs.size() >= MAX_FAVORITOS) {
            toast("Dock cheio (m\u00E1ximo " + MAX_FAVORITOS + ")");
            return;
        }
        Fav f = new Fav();
        f.pkgs = new String[]{ pkg };
        favs.add(f);
        salvarFavoritos();
        reconstruirDock();
        focoPendente = favs.size() - 1;
        carregar();
    }

    private void removerFav(Fav f) {
        int idx = favs.indexOf(f);
        if (idx < 0) return;
        favs.remove(idx);
        salvarFavoritos();
        reconstruirDock();
        if (homeLayer.getVisibility() == View.VISIBLE) {
            if (favs.isEmpty()) plusTile.requestFocus();
            else favs.get(Math.min(idx, favs.size() - 1)).tile.requestFocus();
        }
    }

    private void moverFav(final Fav f, int delta) {
        int idx = favs.indexOf(f);
        int j = idx + delta;
        if (idx < 0 || j < 0 || j >= favs.size()) return;
        Collections.swap(favs, idx, j);
        salvarFavoritos();
        reconstruirDock();
        f.tile.post(new Runnable() {
            @Override public void run() { f.tile.requestFocus(); }
        });
    }

    /** Menu ao segurar OK em um favorito do dock. */
    private void menuDock(final Fav f) {
        int idx = favs.indexOf(f);
        final List<String> nomes = new ArrayList<String>();
        final List<Integer> cod = new ArrayList<Integer>();
        nomes.add("Remover do dock");
        cod.add(0);
        if (idx > 0) { nomes.add("Mover para a esquerda"); cod.add(1); }
        if (idx < favs.size() - 1) { nomes.add("Mover para a direita"); cod.add(2); }
        new AlertDialog.Builder(this)
                .setTitle("Dock")
                .setItems(nomes.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        int c = cod.get(which);
                        if (c == 0) removerFav(f);
                        else if (c == 1) moverFav(f, -1);
                        else moverFav(f, 1);
                    }
                }).show();
    }

    /** Menu ao segurar OK em um app da grade. */
    private void menuGrade(final Item it) {
        final boolean fav = isFav(it.pkg);
        String[] ops = { fav ? "Remover do dock" : "Adicionar ao dock", "Informa\u00E7\u00F5es do app" };
        new AlertDialog.Builder(this)
                .setTitle(it.label)
                .setItems(ops, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            if (fav) removerFav(acharFav(it.pkg));
                            else adicionarFav(it.pkg);
                        } else {
                            try {
                                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + it.pkg)));
                            } catch (Exception ignored) { }
                        }
                    }
                }).show();
    }

    // ------------------------------------------------------------ grade de apps
    private void montarGrade() {
        gridLayer = new LinearLayout(this);
        gridLayer.setOrientation(LinearLayout.VERTICAL);
        gridLayer.setPadding(px(100), px(60), px(100), px(30));

        tituloGrade = new TextView(this);
        tituloGrade.setText("Aplicativos");
        tituloGrade.setTextColor(Color.WHITE);
        tituloGrade.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(56));
        tituloGrade.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tituloGrade.setPadding(0, 0, 0, px(30));
        gridLayer.addView(tituloGrade);

        grid = new GridView(this);
        grid.setNumColumns(COLUNAS);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(px(16));
        grid.setHorizontalSpacing(px(16));
        GradientDrawable sel = new GradientDrawable();
        sel.setColor(0x33FFFFFF);
        sel.setStroke(px(3), Color.WHITE);
        sel.setCornerRadius(px(14));
        grid.setSelector(sel);
        grid.setDrawSelectorOnTop(false);
        grid.setVerticalScrollBarEnabled(false);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Item it = items.get(pos);
                if (modoEscolher) {
                    adicionarFav(it.pkg);
                    mostrarInicio();
                    return;
                }
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.setComponent(it.cn);
                abrir(i);
            }
        });
        // segurar OK = adicionar/remover do dock ou ver informacoes do app
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                menuGrade(items.get(pos));
                return true;
            }
        });
        gridLayer.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void mostrarGrade() {
        tituloGrade.setText(modoEscolher ? "Escolha o app para adicionar ao dock" : "Aplicativos");
        homeLayer.setVisibility(View.GONE);
        gridLayer.setVisibility(View.VISIBLE);
        grid.requestFocus();
    }

    private void mostrarInicio() {
        modoEscolher = false;
        gridLayer.setVisibility(View.GONE);
        homeLayer.setVisibility(View.VISIBLE);
        focarInicio();
    }

    private void focarInicio() {
        for (Fav f : favs) {
            if (f.tile.getVisibility() == View.VISIBLE) { f.tile.requestFocus(); return; }
        }
        btnApps.requestFocus();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        mostrarInicio(); // botao Home sempre volta para a tela inicial
    }

    @Override public void onBackPressed() {
        if (gridLayer.getVisibility() == View.VISIBLE) mostrarInicio();
    }

    @Override protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
        if (MOSTRAR_CLIMA) handler.post(climaTask);
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
        handler.removeCallbacks(climaTask);
    }

    private void abrir(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------ carregar apps
    private void carregar() {
        final List<Fav> snap = new ArrayList<Fav>(favs);
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

                final Bitmap[] fotos = new Bitmap[snap.size()];
                final Intent[] intents = new Intent[snap.size()];
                for (int i = 0; i < snap.size(); i++) {
                    for (String p : snap.get(i).pkgs) {
                        Intent li = pm.getLeanbackLaunchIntentForPackage(p);
                        if (li == null) li = pm.getLaunchIntentForPackage(p);
                        if (li != null) {
                            intents[i] = li;
                            fotos[i] = montarTile(pm, p);
                            break;
                        }
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        items.clear();
                        items.addAll(novos);
                        adapter.notifyDataSetChanged();
                        for (int i = 0; i < snap.size(); i++) {
                            Fav f = snap.get(i);
                            f.intent = intents[i];
                            if (intents[i] != null) {
                                f.img.setImageBitmap(fotos[i]);
                                f.tile.setVisibility(View.VISIBLE);
                            } else {
                                f.tile.setVisibility(View.GONE);
                            }
                        }
                        if (focoPendente >= 0) {
                            if (focoPendente < favs.size()
                                    && favs.get(focoPendente).tile.getVisibility() == View.VISIBLE
                                    && homeLayer.getVisibility() == View.VISIBLE) {
                                favs.get(focoPendente).tile.requestFocus();
                            }
                            focoPendente = -1;
                        }
                        if (primeiraCarga && homeLayer.getVisibility() == View.VISIBLE) {
                            primeiraCarga = false;
                            focarInicio();
                        }
                    }
                });
            }
        }).start();
    }

    /** Banner do app (ou o icone sobre fundo escuro), com cantos arredondados. */
    private Bitmap montarTile(PackageManager pm, String pkg) {
        int w = px(230), h = px(122);
        Bitmap src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(src);
        try {
            Drawable d = pm.getApplicationBanner(pkg);
            if (d != null) {
                d.setBounds(0, 0, w, h);
                d.draw(c);
            } else {
                c.drawColor(0xFF262626);
                Drawable ic = pm.getApplicationIcon(pkg);
                int s = px(76);
                int l = (w - s) / 2, t = (h - s) / 2;
                ic.setBounds(l, t, l + s, t + s);
                ic.draw(c);
            }
        } catch (Exception ignored) { }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(out).drawRoundRect(new RectF(0, 0, w, h), px(14), px(14), p);
        src.recycle();
        return out;
    }

    /** Reduz o icone para economizar RAM. */
    private Bitmap reduzir(Drawable d) {
        Bitmap bmp = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, ICON_PX, ICON_PX);
        d.draw(c);
        return bmp;
    }

    // ------------------------------------------------------------ clima
    private void atualizarClima() {
        if (System.currentTimeMillis() - ultimoClima < 10L * 60L * 1000L
                && climaBox.getVisibility() == View.VISIBLE) return;
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection con = null;
                try {
                    URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude="
                            + LATITUDE + "&longitude=" + LONGITUDE
                            + "&current=temperature_2m,weather_code,is_day");
                    con = (HttpURLConnection) url.openConnection();
                    con.setConnectTimeout(8000);
                    con.setReadTimeout(8000);
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(con.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    JSONObject cur = new JSONObject(sb.toString()).getJSONObject("current");
                    final int temp = (int) Math.round(cur.getDouble("temperature_2m"));
                    final String emoji = emoji(cur.getInt("weather_code"), cur.getInt("is_day") == 1);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            climaIcone.setText(emoji);
                            climaTemp.setText(temp + "\u00B0");
                            climaBox.setVisibility(View.VISIBLE);
                            ultimoClima = System.currentTimeMillis();
                        }
                    });
                } catch (Exception ignored) {
                    // sem internet ou erro: o clima simplesmente nao aparece
                } finally {
                    if (con != null) con.disconnect();
                }
            }
        }).start();
    }

    private static String emoji(int code, boolean dia) {
        if (code == 0) return dia ? "\u2600\uFE0F" : "\uD83C\uDF19";
        if (code <= 2) return dia ? "\u26C5" : "\u2601\uFE0F";
        if (code == 3) return "\u2601\uFE0F";
        if (code == 45 || code == 48) return "\uD83C\uDF2B\uFE0F";
        if (code >= 51 && code <= 57) return "\uD83C\uDF26\uFE0F";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "\uD83C\uDF27\uFE0F";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "\u2744\uFE0F";
        if (code >= 95) return "\u26C8\uFE0F";
        return "\u2601\uFE0F";
    }

    // ------------------------------------------------------------ adaptador da grade
    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) {
                LinearLayout box = new LinearLayout(MainActivity.this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setGravity(Gravity.CENTER_HORIZONTAL);
                box.setPadding(px(8), px(16), px(8), px(12));
                ImageView img = new ImageView(MainActivity.this);
                img.setLayoutParams(new LinearLayout.LayoutParams(px(96), px(96)));
                TextView txt = new TextView(MainActivity.this);
                txt.setTextColor(Color.WHITE);
                txt.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(26));
                txt.setSingleLine(true);
                txt.setGravity(Gravity.CENTER);
                txt.setPadding(0, px(8), 0, 0);
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

    // ------------------------------------------------------------ botoes desenhados
    /** Botao quadrado com icone desenhado em codigo: 0 = grade de apps, 1 = ajustes. */
    private static class IconButton extends View {
        private final int tipo;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        IconButton(Context c, int tipo) {
            super(c);
            this.tipo = tipo;
            setFocusable(true);
            setClickable(true);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            boolean foco = isFocused();
            float sw = w * (foco ? 0.045f : 0.016f);
            RectF r = new RectF(sw, sw, w - sw, h - sw);
            float rad = w * 0.15f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(foco ? 0x44FFFFFF : 0xFF0A0A0A);
            c.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setColor(foco ? Color.WHITE : 0xFFDDDDDD);
            c.drawRoundRect(r, rad, rad, p);

            p.setColor(Color.WHITE);
            if (tipo == 0) {
                float s = w * 0.195f, g = w * 0.065f;
                float o = (w - (3 * s + 2 * g)) / 2f;
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        float x = o + i * (s + g), y = o + j * (s + g);
                        c.drawRoundRect(new RectF(x, y, x + s, y + s), s * 0.3f, s * 0.3f, p);
                    }
                }
            } else {
                float[] ys = { 0.27f, 0.5f, 0.73f };
                float[] xs = { 0.66f, 0.33f, 0.58f };
                float hs = w * 0.08f;
                for (int k = 0; k < 3; k++) {
                    float y = h * ys[k], x = w * xs[k];
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(w * 0.03f);
                    p.setColor(Color.WHITE);
                    c.drawLine(w * 0.17f, y, w * 0.83f, y, p);
                    RectF hr = new RectF(x - hs, y - hs, x + hs, y + hs);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFF0A0A0A);
                    c.drawRoundRect(hr, hs * 0.35f, hs * 0.35f, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(Color.WHITE);
                    c.drawRoundRect(hr, hs * 0.35f, hs * 0.35f, p);
                }
            }
        }
    }
}
