package com.leve.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
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

/** Launcher leve: relogio, clima, dock de 5 banners, musica tocando e grade de apps. */
public class MainActivity extends Activity {

    // ===================== CONFIGURACOES (edite aqui) =====================
    private static final String FAVORITOS_PADRAO =
            "com.google.android.youtube.tv|com.google.android.youtube.googletv,"
            + "com.global.unitviptv,com.spotify.music";
    private static final int SLOTS = 5;
    private static final String[] HIDDEN = {
            "com.droidlogic.mboxlauncher",
            "com.android.documentsui",
            "com.android.providers.downloads.ui",
            "com.android.apkinstaller",
            "com.android.assistant.setting.iknew.rk"
    };
    private static final String PACOTE_CONFIG = "com.android.tv.settings";
    private static final boolean MOSTRAR_CLIMA = true;
    private static final double LATITUDE = -8.59;
    private static final double LONGITUDE = -35.11;
    private static final long CLIMA_INTERVALO = 30L * 60L * 1000L;
    private static final int COLUNAS = 6;
    private static final int ICON_PX = 96;
    // ======================================================================

    private static class Item { String label, pkg; ComponentName cn; Bitmap icon; }

    private static class Fav {
        String pkg;          // null = espaco vazio
        Intent intent;
        Bitmap bitmap;
        int cor = 0xFF262626;
        FrameLayout tile;
        ImageView img;
        TextView mais;
    }

    private final List<Item> items = new ArrayList<Item>();
    private final List<Fav> favs = new ArrayList<Fav>();
    private final Handler handler = new Handler();
    private BaseAdapter adapter;
    private float u;
    private FrameLayout homeLayer;
    private LinearLayout gridLayer, linha;
    private GridView grid;
    private IconButton btnApps, btnConfig;
    private LinearLayout climaBox, musicaBox;
    private TextView climaIcone, climaTemp, tituloGrade, musicaTitulo, musicaSub;
    private ImageView musicaCapa;
    private boolean primeiraCarga = true, modoEscolher = false;
    private long ultimoClima = 0;
    private SharedPreferences prefs;
    private int slotAlvo = -1, focoPendente = -1, selecionado = SLOTS / 2;

    // musica
    private MediaSessionManager msm;
    private ComponentName listenerCn;
    private final List<MediaController> controllers = new ArrayList<MediaController>();
    private final MediaController.Callback ctlCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { atualizarMusica(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { atualizarMusica(); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessListener =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override public void onActiveSessionsChanged(List<MediaController> l) { ligarControllers(l); }
            };

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
    private TextView texto(String t, int tamanho, int cor, int estilo) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(cor);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(tamanho));
        v.setTypeface(Typeface.create("sans-serif", estilo));
        v.setIncludeFontPadding(false);
        return v;
    }

    private void montarInicio() {
        homeLayer = new FrameLayout(this);

        TextClock hora = new TextClock(this);
        hora.setFormat24Hour("HH:mm");
        hora.setFormat12Hour("HH:mm");
        hora.setTextColor(Color.WHITE);
        hora.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(118));
        hora.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        hora.setIncludeFontPadding(false);
        FrameLayout.LayoutParams lh = new FrameLayout.LayoutParams(-2, -2);
        lh.leftMargin = px(70);
        lh.topMargin = px(48);
        homeLayer.addView(hora, lh);

        TextClock data = new TextClock(this);
        data.setFormat24Hour("dd/MM/yyyy");
        data.setFormat12Hour("dd/MM/yyyy");
        data.setTextColor(Color.WHITE);
        data.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(36));
        data.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        data.setIncludeFontPadding(false);
        FrameLayout.LayoutParams ld = new FrameLayout.LayoutParams(-2, -2);
        ld.leftMargin = px(75);
        ld.topMargin = px(168);
        homeLayer.addView(data, ld);

        // clima
        climaBox = new LinearLayout(this);
        climaBox.setOrientation(LinearLayout.HORIZONTAL);
        climaBox.setGravity(Gravity.CENTER_VERTICAL);
        climaIcone = texto("", 82, Color.WHITE, Typeface.NORMAL);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(16), 0, 0, 0);
        col.addView(texto("Hoje", 28, Color.WHITE, Typeface.NORMAL));
        climaTemp = texto("", 78, Color.WHITE, Typeface.BOLD);
        col.addView(climaTemp);
        climaBox.addView(climaIcone);
        climaBox.addView(col);
        climaBox.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams lc = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        lc.rightMargin = px(75);
        lc.topMargin = px(62);
        homeLayer.addView(climaBox, lc);

        // dock central
        linha = new LinearLayout(this);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        linha.setGravity(Gravity.CENTER_VERTICAL);
        montarDock();
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-2, px(448), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        ll.topMargin = px(323);
        homeLayer.addView(linha, ll);
        atualizarTamanhos();

        // musica tocando (canto inferior esquerdo)
        musicaBox = new LinearLayout(this);
        musicaBox.setOrientation(LinearLayout.HORIZONTAL);
        musicaBox.setGravity(Gravity.CENTER_VERTICAL);
        musicaCapa = new ImageView(this);
        musicaCapa.setScaleType(ImageView.ScaleType.CENTER_CROP);
        musicaBox.addView(musicaCapa, new LinearLayout.LayoutParams(px(80), px(80)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(px(28), 0, 0, 0);
        musicaTitulo = texto("", 44, Color.WHITE, Typeface.BOLD);
        musicaTitulo.setSingleLine(true);
        musicaTitulo.setEllipsize(TextUtils.TruncateAt.END);
        musicaTitulo.setMaxWidth(px(1000));
        musicaSub = texto("", 28, 0xFFDDDDDD, Typeface.NORMAL);
        musicaSub.setSingleLine(true);
        musicaSub.setEllipsize(TextUtils.TruncateAt.END);
        musicaSub.setMaxWidth(px(1000));
        musicaSub.setPadding(0, px(4), 0, 0);
        tx.addView(musicaTitulo);
        tx.addView(musicaSub);
        musicaBox.addView(tx);
        musicaBox.setVisibility(View.GONE);
        FrameLayout.LayoutParams lm = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.LEFT);
        lm.leftMargin = px(75);
        lm.bottomMargin = px(76);
        homeLayer.addView(musicaBox, lm);

        // botoes (canto inferior direito)
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(px(12), px(12), px(12), px(12));
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(0xFF1A1A1A);
        pbg.setCornerRadius(px(50));
        pill.setBackgroundDrawable(pbg);
        btnApps = new IconButton(this, 0);
        btnApps.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { mostrarGrade(); }
        });
        btnConfig = new IconButton(this, 1);
        btnConfig.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { abrirConfig(); }
        });
        pill.addView(btnApps, new LinearLayout.LayoutParams(px(76), px(76)));
        LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(px(76), px(76));
        lb.leftMargin = px(10);
        pill.addView(btnConfig, lb);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
        lp.rightMargin = px(77);
        lp.bottomMargin = px(77);
        homeLayer.addView(pill, lp);
    }

    private void abrirConfig() {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(PACOTE_CONFIG);
        if (i == null) i = pm.getLaunchIntentForPackage(PACOTE_CONFIG);
        if (i == null) i = new Intent(Settings.ACTION_SETTINGS);
        abrir(i);
    }

    /** Espaco selecionado = grande; vizinho = medio; os demais = pequenos. */
    private void atualizarTamanhos() {
        for (int i = 0; i < favs.size(); i++) {
            int d = Math.abs(i - selecionado);
            int w = d == 0 ? 374 : 284;
            int h = d == 0 ? 448 : (d == 1 ? 348 : 261);
            View t = favs.get(i).tile;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) t.getLayoutParams();
            int nw = px(w), nh = px(h);
            if (lp.width != nw || lp.height != nh) {
                lp.width = nw;
                lp.height = nh;
                t.setLayoutParams(lp);
            }
        }
    }

    // ------------------------------------------------------------ favoritos (5 espacos livres)
    private void lerFavoritos() {
        PackageManager pm = getPackageManager();
        String guardado = prefs.getString("slots", null);
        String[] partes = guardado != null
                ? guardado.split(",", -1)
                : prefs.getString("favs", FAVORITOS_PADRAO).split(",");
        favs.clear();
        for (int i = 0; i < SLOTS; i++) {
            Fav f = new Fav();
            if (i < partes.length) {
                String p = partes[i].trim();
                if (p.indexOf('|') >= 0) {
                    String[] alt = p.split("\\|");
                    p = alt[0];
                    for (String a : alt) {
                        if (pm.getLaunchIntentForPackage(a) != null
                                || pm.getLeanbackLaunchIntentForPackage(a) != null) { p = a; break; }
                    }
                }
                f.pkg = p.length() == 0 ? null : p;
            }
            favs.add(f);
        }
        if (guardado == null) salvarFavoritos();
    }

    private void salvarFavoritos() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < favs.size(); i++) {
            if (i > 0) sb.append(',');
            String p = favs.get(i).pkg;
            if (p != null) sb.append(p);
        }
        prefs.edit().putString("slots", sb.toString()).apply();
    }

    private Fav acharFav(String pkg) {
        for (Fav f : favs) if (pkg.equals(f.pkg)) return f;
        return null;
    }

    private boolean isFav(String pkg) { return acharFav(pkg) != null; }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

    private void aplicarSlot(Fav f) {
        boolean cheio = f.pkg != null && f.intent != null;
        f.img.setImageBitmap(cheio ? f.bitmap : null);
        f.mais.setVisibility(cheio ? View.GONE : View.VISIBLE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cheio ? f.cor : 0xFF222222);
        bg.setCornerRadius(px(26));
        f.tile.setBackgroundDrawable(bg);
    }

    private void criarTile(final Fav f) {
        f.tile = new FrameLayout(this);
        f.tile.setFocusable(true);
        f.tile.setClickable(true);
        f.img = new ImageView(this);
        f.img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        f.tile.addView(f.img, new FrameLayout.LayoutParams(-1, -1));
        f.mais = texto("+", 120, Color.WHITE, Typeface.BOLD);
        f.mais.setGravity(Gravity.CENTER);
        f.tile.addView(f.mais, new FrameLayout.LayoutParams(-1, -1));
        f.tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean tem) {
                if (tem) {
                    selecionado = favs.indexOf(f);
                    atualizarTamanhos();
                }
            }
        });
        f.tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (f.pkg != null && f.intent != null) abrir(f.intent);
                else escolher(favs.indexOf(f));
            }
        });
        f.tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                if (f.pkg != null && f.intent != null) menuDock(f);
                else escolher(favs.indexOf(f));
                return true;
            }
        });
    }

    private void montarDock() {
        for (int i = 0; i < favs.size(); i++) {
            Fav f = favs.get(i);
            criarTile(f);
            aplicarSlot(f);
            LinearLayout.LayoutParams lt = new LinearLayout.LayoutParams(px(284), px(261));
            lt.leftMargin = i == 0 ? 0 : px(45);
            linha.addView(f.tile, lt);
        }
    }

    private void escolher(int idx) {
        if (idx < 0) return;
        modoEscolher = true;
        slotAlvo = idx;
        mostrarGrade();
    }

    private void atribuirSlot(int idx, String pkg) {
        if (idx < 0 || idx >= favs.size()) return;
        Fav ja = acharFav(pkg);
        if (ja != null && favs.indexOf(ja) != idx) { toast("J\u00E1 est\u00E1 no dock"); return; }
        Fav f = favs.get(idx);
        f.pkg = pkg;
        f.intent = null;
        f.bitmap = null;
        aplicarSlot(f);
        salvarFavoritos();
        focoPendente = idx;
        carregar();
    }

    private void adicionarNoPrimeiroVazio(String pkg) {
        if (isFav(pkg)) { toast("J\u00E1 est\u00E1 no dock"); return; }
        for (int i = 0; i < favs.size(); i++) {
            if (favs.get(i).pkg == null) { atribuirSlot(i, pkg); return; }
        }
        toast("Dock cheio: remova ou troque um favorito");
    }

    private void removerFav(Fav f) {
        f.pkg = null;
        f.intent = null;
        f.bitmap = null;
        aplicarSlot(f);
        salvarFavoritos();
        if (homeLayer.getVisibility() == View.VISIBLE) f.tile.requestFocus();
    }

    private void moverFav(Fav f, int delta) {
        int idx = favs.indexOf(f);
        int j = idx + delta;
        if (idx < 0 || j < 0 || j >= favs.size()) return;
        Fav b = favs.get(j);
        String p = f.pkg; f.pkg = b.pkg; b.pkg = p;
        Intent in = f.intent; f.intent = b.intent; b.intent = in;
        Bitmap bm = f.bitmap; f.bitmap = b.bitmap; b.bitmap = bm;
        int cr = f.cor; f.cor = b.cor; b.cor = cr;
        aplicarSlot(f);
        aplicarSlot(b);
        salvarFavoritos();
        b.tile.requestFocus();
    }

    private void menuDock(final Fav f) {
        int idx = favs.indexOf(f);
        final List<String> nomes = new ArrayList<String>();
        final List<Integer> cod = new ArrayList<Integer>();
        nomes.add("Trocar app"); cod.add(0);
        nomes.add("Remover do dock"); cod.add(1);
        if (idx > 0) { nomes.add("Mover para a esquerda"); cod.add(2); }
        if (idx < favs.size() - 1) { nomes.add("Mover para a direita"); cod.add(3); }
        new AlertDialog.Builder(this)
                .setTitle("Dock")
                .setItems(nomes.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        int c = cod.get(which);
                        if (c == 0) escolher(favs.indexOf(f));
                        else if (c == 1) removerFav(f);
                        else if (c == 2) moverFav(f, -1);
                        else moverFav(f, 1);
                    }
                }).show();
    }

    private void menuGrade(final Item it) {
        final boolean fav = isFav(it.pkg);
        String[] ops = { fav ? "Remover do dock" : "Adicionar ao dock", "Informa\u00E7\u00F5es do app" };
        new AlertDialog.Builder(this)
                .setTitle(it.label)
                .setItems(ops, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            if (fav) removerFav(acharFav(it.pkg));
                            else adicionarNoPrimeiroVazio(it.pkg);
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

        tituloGrade = texto("Aplicativos", 56, Color.WHITE, Typeface.BOLD);
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
                    atribuirSlot(slotAlvo, it.pkg);
                    mostrarInicio();
                    return;
                }
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.setComponent(it.cn);
                abrir(i);
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                menuGrade(items.get(pos));
                return true;
            }
        });
        gridLayer.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void mostrarGrade() {
        tituloGrade.setText(modoEscolher ? "Escolha o app para este espa\u00E7o" : "Aplicativos");
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

    /** O espaco do meio sempre comeca selecionado. */
    private void focarInicio() { favs.get(SLOTS / 2).tile.requestFocus(); }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        mostrarInicio();
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
        iniciarMusica();
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
        handler.removeCallbacks(climaTask);
        pararMusica();
    }

    private void abrir(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------ musica tocando
    private void iniciarMusica() {
        try {
            msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            listenerCn = new ComponentName(this, MediaListener.class);
            msm.addOnActiveSessionsChangedListener(sessListener, listenerCn);
            ligarControllers(msm.getActiveSessions(listenerCn));
        } catch (Exception e) {
            // sem acesso (veja o LEIAME): o bloco de musica fica escondido
            musicaBox.setVisibility(View.GONE);
        }
    }

    private void pararMusica() {
        try { if (msm != null) msm.removeOnActiveSessionsChangedListener(sessListener); } catch (Exception ignored) { }
        for (MediaController c : controllers) {
            try { c.unregisterCallback(ctlCallback); } catch (Exception ignored) { }
        }
        controllers.clear();
    }

    private void ligarControllers(List<MediaController> lista) {
        for (MediaController c : controllers) {
            try { c.unregisterCallback(ctlCallback); } catch (Exception ignored) { }
        }
        controllers.clear();
        if (lista != null) {
            for (MediaController c : lista) {
                try { c.registerCallback(ctlCallback); controllers.add(c); } catch (Exception ignored) { }
            }
        }
        atualizarMusica();
    }

    private void atualizarMusica() {
        MediaController escolhido = null;
        for (MediaController c : controllers) {
            PlaybackState st = c.getPlaybackState();
            if (c.getMetadata() == null) continue;
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) { escolhido = c; break; }
            if (escolhido == null) escolhido = c;
        }
        MediaMetadata m = escolhido == null ? null : escolhido.getMetadata();
        String titulo = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (titulo == null || titulo.length() == 0) {
            musicaBox.setVisibility(View.GONE);
            return;
        }
        String artista = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artista == null) artista = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        String album = m.getString(MediaMetadata.METADATA_KEY_ALBUM);
        StringBuilder sub = new StringBuilder();
        if (album != null && album.length() > 0) sub.append(album);
        if (artista != null && artista.length() > 0) {
            if (sub.length() > 0) sub.append(" | ");
            sub.append(artista);
        }
        Bitmap arte = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (arte == null) arte = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (arte == null) arte = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (arte != null) {
            Bitmap pequena = Bitmap.createScaledBitmap(arte, px(80), px(80), true);
            musicaCapa.setImageBitmap(arredondar(pequena, px(8)));
            musicaCapa.setVisibility(View.VISIBLE);
        } else {
            musicaCapa.setVisibility(View.GONE);
        }
        musicaTitulo.setText(titulo);
        musicaSub.setText(sub.toString());
        musicaSub.setVisibility(sub.length() > 0 ? View.VISIBLE : View.GONE);
        musicaBox.setVisibility(View.VISIBLE);
    }

    private Bitmap arredondar(Bitmap src, float r) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(out).drawRoundRect(new RectF(0, 0, src.getWidth(), src.getHeight()), r, r, p);
        return out;
    }

    // ------------------------------------------------------------ carregar apps
    private void carregar() {
        final List<Fav> snap = new ArrayList<Fav>(favs);
        final String[] pkgs = new String[snap.size()];
        for (int i = 0; i < pkgs.length; i++) pkgs[i] = snap.get(i).pkg;
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
                final int[] cores = new int[snap.size()];
                for (int i = 0; i < snap.size(); i++) {
                    String p = pkgs[i];
                    if (p == null) continue;
                    Intent li = pm.getLeanbackLaunchIntentForPackage(p);
                    if (li == null) li = pm.getLaunchIntentForPackage(p);
                    if (li != null) {
                        intents[i] = li;
                        int[] cor = new int[1];
                        fotos[i] = montarTile(pm, p, li, cor);
                        cores[i] = cor[0];
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        items.clear();
                        items.addAll(novos);
                        adapter.notifyDataSetChanged();
                        for (int i = 0; i < snap.size(); i++) {
                            Fav f = snap.get(i);
                            if (pkgs[i] == null ? f.pkg != null : !pkgs[i].equals(f.pkg)) continue;
                            f.intent = intents[i];
                            f.bitmap = fotos[i];
                            if (intents[i] != null) f.cor = cores[i];
                            aplicarSlot(f);
                        }
                        if (focoPendente >= 0) {
                            if (focoPendente < favs.size() && homeLayer.getVisibility() == View.VISIBLE) {
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

    /**
     * Imagem do dock. Com banner: o banner inteiro (a cor de fundo do tile vem da borda do banner).
     * Sem banner: icone do app + nome.
     */
    private Bitmap montarTile(PackageManager pm, String pkg, Intent launch, int[] cor) {
        int bw = px(374);
        cor[0] = 0xFF262626;
        try {
            Drawable d = null;
            try { d = pm.getActivityBanner(launch); } catch (Exception ignored) { }
            if (d == null) d = pm.getApplicationBanner(pkg);
            if (d != null) {
                int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
                int bh = (iw > 0 && ih > 0) ? Math.max(1, bw * ih / iw) : bw * 9 / 16;
                Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                d.setBounds(0, 0, bw, bh);
                d.draw(new Canvas(b));
                int amostra = b.getPixel(2, 2);
                if (Color.alpha(amostra) > 250) cor[0] = amostra;
                return b;
            }
            Bitmap b = Bitmap.createBitmap(bw, bw, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Drawable ic = pm.getApplicationIcon(pkg);
            int s = px(150);
            int l = (bw - s) / 2, t = px(70);
            ic.setBounds(l, t, l + s, t + s);
            ic.draw(c);
            CharSequence nome = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            tp.setColor(Color.WHITE);
            tp.setTextSize(px(34));
            tp.setTextAlign(Paint.Align.CENTER);
            CharSequence txt = TextUtils.ellipsize(nome, tp, bw - px(40), TextUtils.TruncateAt.END);
            c.drawText(txt.toString(), bw / 2f, px(300), tp);
            return b;
        } catch (Exception e) {
            return Bitmap.createBitmap(bw, bw, Bitmap.Config.ARGB_8888);
        }
    }

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
                TextView txt = texto("", 26, Color.WHITE, Typeface.NORMAL);
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

    // ------------------------------------------------------------ botoes redondos
    /** 0 = todos os apps (pontinhos), 1 = engrenagem. */
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
            float w = getWidth();
            float cx = w / 2f, cy = getHeight() / 2f;
            boolean foco = isFocused();
            float sw = w * (foco ? 0.06f : 0.02f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(foco ? 0x55FFFFFF : 0xFF1A1A1A);
            c.drawCircle(cx, cy, w / 2f - sw, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setColor(foco ? Color.WHITE : 0xFF666666);
            c.drawCircle(cx, cy, w / 2f - sw, p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            if (tipo == 0) {
                float r = w * 0.052f, step = w * 0.17f;
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) c.drawCircle(cx + i * step, cy + j * step, r, p);
                }
            } else {
                float R = w * 0.22f, tw = w * 0.11f, tl = w * 0.10f;
                Path g = new Path();
                g.addCircle(cx, cy, R, Path.Direction.CW);
                Path dente = new Path();
                dente.addRoundRect(new RectF(-tw / 2f, -(R + tl), tw / 2f, -(R - w * 0.03f)),
                        tw * 0.25f, tw * 0.25f, Path.Direction.CW);
                Matrix m = new Matrix();
                for (int k = 0; k < 8; k++) {
                    m.reset();
                    m.postRotate(k * 45f);
                    m.postTranslate(cx, cy);
                    g.addPath(dente, m);
                }
                g.addCircle(cx, cy, w * 0.095f, Path.Direction.CCW);
                g.setFillType(Path.FillType.WINDING);
                c.drawPath(g, p);
            }
        }
    }
}
