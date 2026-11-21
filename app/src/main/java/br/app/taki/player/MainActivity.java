package br.app.taki.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.InputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements EntryAdapter.Listener {
    private static final int REQUEST_M3U = 810;
    private static final int PAGE_SIZE = 100;
    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final int[] TAB_TYPES = {
            M3uParser.LIVE, M3uParser.MOVIES, M3uParser.SERIES,
            M3uParser.OTHERS, M3uParser.ALL
    };
    private static final String[] TAB_NAMES = {"Ao vivo", "Filmes", "Séries", "Outros", "Tudo"};

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int[] counts = new int[5];
    private PlaylistDatabase db;
    private ExoPlayer player;
    private PlayerView playerView;
    private EntryAdapter adapter;
    private RecyclerView recycler;
    private LinearLayout tabsContainer;
    private TextView txtSummary, txtNowPlaying, txtPlayerState, txtPlayerOverlay, txtListStatus;
    private EditText editSearch;
    private Spinner spinnerCategory;
    private Button btnFavorites, btnImport;
    private ProgressBar progress;
    private View topPanel, tabsScroll, filterPanel, playerBar;
    private FrameLayout videoFrame;
    private MediaEntry playing;
    private String selectedCategory = "";
    private int selectedType = M3uParser.LIVE;
    private boolean favoritesOnly = false;
    private boolean importing = false;
    private boolean loadingPage = false;
    private boolean noMorePages = false;
    private boolean spinnerUpdating = false;
    private boolean fullscreen = false;
    private int loaded = 0;
    private int queryGeneration = 0;
    private int categoryGeneration = 0;
    private Runnable pendingSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        db = new PlaylistDatabase(getApplicationContext());
        bindViews();
        configurePlayer();
        configureList();
        configureControls();
        updateTabs();
        updateSummary();
        refreshCategories();
        loadFirstPage();
        worker.execute(() -> {
            int[] current = db.counts();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                System.arraycopy(current, 0, counts, 0, counts.length);
                updateTabs();
                updateSummary();
                if (counts[0] == 0) txtListStatus.setText("Importe sua lista M3U para começar.");
            });
        });
    }

    private void bindViews() {
        playerView = findViewById(R.id.playerView);
        recycler = findViewById(R.id.recyclerItems);
        tabsContainer = findViewById(R.id.tabsContainer);
        txtSummary = findViewById(R.id.txtSummary);
        txtNowPlaying = findViewById(R.id.txtNowPlaying);
        txtPlayerState = findViewById(R.id.txtPlayerState);
        txtPlayerOverlay = findViewById(R.id.txtPlayerOverlay);
        txtListStatus = findViewById(R.id.txtListStatus);
        editSearch = findViewById(R.id.editSearch);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        btnFavorites = findViewById(R.id.btnFavorites);
        btnImport = findViewById(R.id.btnImport);
        progress = findViewById(R.id.progress);
        videoFrame = findViewById(R.id.videoFrame);
        topPanel = findViewById(R.id.topPanel);
        tabsScroll = findViewById(R.id.tabsScroll);
        filterPanel = findViewById(R.id.filterPanel);
        playerBar = findViewById(R.id.playerBar);
    }

    private void configurePlayer() {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(25000)
                .setUserAgent("TakiPlayer/1.0 Android");
        DefaultDataSource.Factory data = new DefaultDataSource.Factory(this, http);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(data))
                .build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (playing == null) return;
                if (state == Player.STATE_BUFFERING) {
                    txtPlayerState.setText("Carregando transmissão...");
                } else if (state == Player.STATE_READY) {
                    txtPlayerOverlay.setVisibility(View.GONE);
                    txtPlayerState.setText("Reprodução disponível");
                } else if (state == Player.STATE_ENDED) {
                    txtPlayerState.setText("Reprodução concluída");
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                txtPlayerOverlay.setText("Não foi possível reproduzir.\n\n" +
                        "Verifique sua conexão e a validade do link. " +
                        "Experimente também a opção VLC.");
                txtPlayerOverlay.setVisibility(View.VISIBLE);
                txtPlayerState.setText("Falha no vídeo: " + error.getErrorCodeName());
                playerView.setKeepScreenOn(false);
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                playerView.setKeepScreenOn(isPlaying);
                if (isPlaying) txtPlayerState.setText("▶ Reproduzindo");
            }
        });
    }

    private void configureList() {
        adapter = new EntryAdapter(this);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        recycler.setLayoutManager(manager);
        recycler.setAdapter(adapter);
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (dy > 0 && manager.findLastVisibleItemPosition() >= adapter.getItemCount() - 18)
                    loadNextPage();
            }
        });
    }

    private void configureControls() {
        btnImport.setOnClickListener(v -> pickM3u());
        findViewById(R.id.btnFullscreen).setOnClickListener(v -> setFullscreen(!fullscreen));
        findViewById(R.id.btnExternal).setOnClickListener(v -> openInVlc());
        btnFavorites.setOnClickListener(v -> {
            favoritesOnly = !favoritesOnly;
            btnFavorites.setText(favoritesOnly ? "★" : "☆");
            btnFavorites.setContentDescription(favoritesOnly ? "Mostrar todos" : "Mostrar favoritos");
            loadFirstPage();
        });
        editSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                pendingSearch = () -> loadFirstPage();
                handler.postDelayed(pendingSearch, 400);
            }
            @Override public void afterTextChanged(android.text.Editable e) { }
        });
        spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (spinnerUpdating) return;
                selectedCategory = position == 0 ? "" : String.valueOf(parent.getItemAtPosition(position));
                loadFirstPage();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void pickM3u() {
        if (importing) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // Deixar M3U visível em diferentes gerenciadores de arquivos.
        startActivityForResult(intent, REQUEST_M3U);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_M3U && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) startImport(uri);
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return "playlist.m3u";
    }

    private void startImport(Uri uri) {
        if (importing) return;
        importing = true;
        btnImport.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        adapter.replace(new ArrayList<>());
        txtListStatus.setText("Lendo arquivo e indexando no aparelho...");
        String name = displayName(uri);
        worker.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("Não foi possível abrir o arquivo.");
                int total = db.importM3u(input, n -> {
                    if (n % 10000 == 0) runOnUiThread(() ->
                            txtListStatus.setText("Importando: " + format(n) + " itens..."));
                });
                int[] result = db.counts();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    System.arraycopy(result, 0, counts, 0, counts.length);
                    getPreferences(MODE_PRIVATE).edit().putString("playlist_name", name).apply();
                    importing = false;
                    btnImport.setEnabled(true);
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, format(total) + " itens importados", Toast.LENGTH_LONG).show();
                    updateTabs();
                    updateSummary();
                    selectedCategory = "";
                    refreshCategories();
                    loadFirstPage();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    importing = false;
                    btnImport.setEnabled(true);
                    progress.setVisibility(View.GONE);
                    txtListStatus.setText("Não foi possível importar a lista.");
                    new AlertDialog.Builder(this)
                            .setTitle("Falha na importação")
                            .setMessage(e.getMessage() == null ? "Arquivo inválido ou inacessível." : e.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                    // Se houve erro, a transação SQL preserva a biblioteca anterior.
                    loadFirstPage();
                });
            }
        });
    }

    private void updateSummary() {
        String name = getPreferences(MODE_PRIVATE).getString("playlist_name", "Nenhuma lista importada");
        txtSummary.setText(counts[0] == 0 ? "Sua biblioteca · nenhuma lista importada" :
                format(counts[0]) + " conteúdos · " + name);
    }

    private void updateTabs() {
        tabsContainer.removeAllViews();
        for (int i = 0; i < TAB_TYPES.length; i++) {
            final int type = TAB_TYPES[i];
            boolean active = type == selectedType;
            TextView tab = new TextView(this);
            tab.setText(TAB_NAMES[i] + "  " + format(counts[type]));
            tab.setTextSize(12);
            tab.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
            tab.setTextColor(active ? Color.WHITE : getColor(R.color.muted));
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(12), 0, dp(12), 0);
            GradientDrawable background = new GradientDrawable();
            background.setColor(getColor(active ? R.color.primary_dark : R.color.surface));
            background.setCornerRadius(dp(10));
            background.setStroke(dp(1), getColor(active ? R.color.primary : R.color.stroke));
            tab.setBackground(background);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(37));
            lp.setMargins(dp(3), 0, dp(3), 0);
            tabsContainer.addView(tab, lp);
            tab.setOnClickListener(v -> {
                if (selectedType == type || importing) return;
                selectedType = type;
                selectedCategory = "";
                updateTabs();
                refreshCategories();
                loadFirstPage();
            });
        }
    }

    private void refreshCategories() {
        final int generation = ++categoryGeneration;
        final int type = selectedType;
        worker.execute(() -> {
            List<String> options = db.categories(type);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != categoryGeneration) return;
                ArrayList<String> values = new ArrayList<>();
                values.add("Todas as categorias");
                values.addAll(options);
                ArrayAdapter<String> menu = new ArrayAdapter<String>(
                        this, android.R.layout.simple_spinner_item, values) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        return drawSpinnerItem(super.getView(position, convertView, parent), true);
                    }
                    @Override
                    public View getDropDownView(int position, View convertView, ViewGroup parent) {
                        return drawSpinnerItem(super.getDropDownView(position, convertView, parent), false);
                    }
                };
                menu.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerUpdating = true;
                spinnerCategory.setAdapter(menu);
                spinnerCategory.setSelection(0);
                spinnerUpdating = false;
            });
        });
    }

    private View drawSpinnerItem(View view, boolean selected) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            text.setTextColor(getColor(R.color.white));
            text.setTextSize(13);
            text.setPadding(dp(12), dp(10), dp(12), dp(10));
            if (!selected) text.setBackgroundColor(getColor(R.color.surface_alt));
        }
        return view;
    }

    private void loadFirstPage() {
        if (importing || db == null) return;
        ++queryGeneration;
        loaded = 0;
        noMorePages = false;
        loadingPage = false;
        adapter.replace(new ArrayList<>());
        recycler.scrollToPosition(0);
        loadNextPage();
    }

    private void loadNextPage() {
        if (importing || loadingPage || noMorePages) return;
        loadingPage = true;
        int generation = queryGeneration;
        int offset = loaded;
        int type = selectedType;
        String category = selectedCategory;
        String query = editSearch.getText().toString();
        boolean onlyFavorites = favoritesOnly;
        worker.execute(() -> {
            try {
                List<MediaEntry> items = db.page(type, category, query, onlyFavorites, PAGE_SIZE, offset);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != queryGeneration) return;
                    loadingPage = false;
                    adapter.append(items);
                    loaded += items.size();
                    noMorePages = items.size() < PAGE_SIZE;
                    txtListStatus.setText(loaded == 0
                            ? (counts[0] == 0 ? "Importe sua lista M3U para começar."
                                               : "Nenhum conteúdo encontrado neste filtro.")
                            : format(loaded) + " itens exibidos · role para carregar mais");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (generation != queryGeneration) return;
                    loadingPage = false;
                    txtListStatus.setText("Erro ao pesquisar os conteúdos.");
                });
            }
        });
    }

    @Override
    public void onPlay(MediaEntry item) {
        playing = item;
        txtNowPlaying.setText(item.name);
        txtPlayerState.setText("Conectando ao servidor...");
        txtPlayerOverlay.setVisibility(View.GONE);
        String lower = item.url.toLowerCase(Locale.ROOT);
        MediaItem.Builder builder = new MediaItem.Builder()
                .setUri(Uri.parse(item.url))
                .setMediaId(String.valueOf(item.id));
        if (lower.contains(".m3u8")) builder.setMimeType(MimeTypes.APPLICATION_M3U8);
        try {
            player.setMediaItem(builder.build());
            player.prepare();
            player.play();
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(editSearch.getWindowToken(), 0);
            editSearch.clearFocus();
        } catch (Exception ignored) {
            txtPlayerOverlay.setText("Formato não suportado neste aparelho. Tente abrir no VLC.");
            txtPlayerOverlay.setVisibility(View.VISIBLE);
            txtPlayerState.setText("Não foi possível iniciar o vídeo");
        }
    }

    @Override
    public void onFavorite(MediaEntry item, boolean value) {
        worker.execute(() -> {
            db.setFavorite(item.url, value);
            if (favoritesOnly) runOnUiThread(this::loadFirstPage);
        });
    }

    @Override
    public void onCopyUrl(MediaEntry item) {
        new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setMessage("Deseja copiar o link deste conteúdo?")
                .setPositiveButton("Copiar link", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("Link de vídeo", item.url));
                    Toast.makeText(this, "Endereço copiado", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void openInVlc() {
        if (playing == null) {
            Toast.makeText(this, "Selecione um vídeo primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setPackage("org.videolan.vlc");
        intent.setDataAndType(Uri.parse(playing.url), "video/*");
        try {
            startActivity(intent);
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("VLC não encontrado")
                    .setMessage("Instale o VLC para Android ou toque e segure um item da lista para copiar seu endereço.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void setFullscreen(boolean enable) {
        fullscreen = enable;
        topPanel.setVisibility(enable ? View.GONE : View.VISIBLE);
        playerBar.setVisibility(enable ? View.GONE : View.VISIBLE);
        tabsScroll.setVisibility(enable ? View.GONE : View.VISIBLE);
        filterPanel.setVisibility(enable ? View.GONE : View.VISIBLE);
        recycler.setVisibility(enable ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) videoFrame.getLayoutParams();
        if (enable) {
            lp.height = 0;
            lp.weight = 1;
            lp.setMargins(0, 0, 0, 0);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            lp.height = dp(210);
            lp.weight = 0;
            lp.setMargins(dp(12), dp(4), dp(12), dp(4));
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(0);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        videoFrame.setLayoutParams(lp);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (fullscreen) setFullscreen(false);
        else super.onBackPressed();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
        worker.shutdown();
        super.onDestroy();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String format(int count) {
        return NumberFormat.getIntegerInstance(PT_BR).format(count);
    }
}
