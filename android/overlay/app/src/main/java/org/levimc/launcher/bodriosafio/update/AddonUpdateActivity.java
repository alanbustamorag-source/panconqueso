// Bodriosafío: modificación del 23-11-2026 para el nuevo canal y compatibilidad del perfil.
package org.levimc.launcher.bodriosafio.update;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.levimc.launcher.bodriosafio.BodriosafioProfile;
import org.levimc.launcher.bodriosafio.DownloadProgressUi;
import org.levimc.launcher.bodriosafio.ProfileRules;
import org.levimc.launcher.core.minecraft.MinecraftActivityState;
import org.levimc.launcher.core.minecraft.MinecraftLauncher;
import org.levimc.launcher.core.versions.GameVersion;
import org.levimc.launcher.util.LauncherStorage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Public channel and local repair share the same identity-aware installer. */
public final class AddonUpdateActivity extends Activity {
    private static final String ROOTS = "bodriosafio.update.roots";
    private static final int FILE_REQUEST = 8204;
    private static final int BACKGROUND = Color.rgb(14, 23, 20), SURFACE = Color.rgb(25, 39, 32);
    private static final int ACCENT = Color.rgb(157, 227, 178), WHITE = Color.rgb(234, 243, 237);
    private TextView installed, published, status;
    private DownloadProgressUi progress;
    private Button update, check, file, bundled;
    private final List<Path> roots = new ArrayList<>();
    private Path cache, journals;
    private AddonChannel channel;
    private volatile boolean busy;

    private static List<Path> gameRoots(android.content.Context context, GameVersion version) throws Exception {
        List<Path> roots = new ArrayList<>();
        for (boolean external : new boolean[] {false, true}) {
            Path path = LauncherStorage.getStorageGameDataDir(context, MinecraftLauncher.getStorageProfileId(version), version.versionIsolation, external).getCanonicalFile().toPath();
            if (!roots.contains(path)) roots.add(path);
        }
        return roots;
    }

    /** Restore a pending transaction before Minecraft can open a world. */
    public static void recoverBeforeLaunch(android.content.Context context, GameVersion version) throws Exception {
        if (version != null && ProfileRules.compatible(version.versionCode))
            AddonInstaller.recover(gameRoots(context, version), StoragePaths.privateChild(context.getFilesDir(), "bodriosafio-update-journals"));
    }

    public static void checkHome(Activity host, GameVersion version, Button target) {
        Object request = new Object();
        target.setTag(request);
        target.setText("Actualizar addon");
        if (version == null || !ProfileRules.compatible(version.versionCode)) return;
        java.lang.ref.WeakReference<Activity> owner = new java.lang.ref.WeakReference<>(host);
        java.lang.ref.WeakReference<Button> control = new java.lang.ref.WeakReference<>(target);
        android.content.Context context = host.getApplicationContext();
        new Thread(() -> {
            try {
                AddonChannel latest = new AddonChannel(new String(AddonChannel.get(AddonChannel.URL, 128 * 1024), StandardCharsets.UTF_8), "android", org.levimc.launcher.BuildConfig.VERSION_NAME);
                List<Path> roots = gameRoots(context, version);
                int[] bp = AddonInstaller.installed(roots, 0), rp = AddonInstaller.installed(roots, 1);
                boolean current = bp != null && rp != null && AddonChannel.compare(bp, latest.behaviorVersion) >= 0 && AddonChannel.compare(rp, latest.resourceVersion) >= 0;
                Activity activity = owner.get();
                if (activity != null) activity.runOnUiThread(() -> {
                    Button button = control.get();
                    if (button != null && button.getTag() == request && !activity.isDestroyed() && !activity.isFinishing())
                        button.setText(current ? "Addon al día · " + AddonChannel.display(bp) : "Actualizar addon · " + latest.version);
                });
            } catch (Exception error) { BodriosafioProfile.log(context, "No se pudo comprobar el addon al abrir: " + error.getMessage()); }
        }, "bodriosafio-addon-check").start();
    }

    public static void open(Activity host, GameVersion version) {
        if (!org.levimc.launcher.core.minecraft.LaunchPreparation.allowContentChanges(host)) return;
        try {
            if (version == null || !ProfileRules.compatible(version.versionCode))
                throw new Exception("Selecciona en Herramientas un perfil compatible con el addon.");
            if (MinecraftActivityState.isRunning(host)) throw new Exception("Cierra Minecraft antes de actualizar sus packs.");
            ArrayList<String> paths = new ArrayList<>();
            for (Path path : gameRoots(host, version)) paths.add(path.toString());
            host.startActivity(new Intent(host, AddonUpdateActivity.class).putStringArrayListExtra(ROOTS, paths));
        } catch (Exception e) { Toast.makeText(host, e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(BACKGROUND); getWindow().setNavigationBarColor(BACKGROUND);
        try {
            cache = StoragePaths.privateChild(getCacheDir(), "bodriosafio-updates");
            journals = StoragePaths.privateChild(getFilesDir(), "bodriosafio-update-journals");
        } catch (java.io.IOException error) {
            BodriosafioProfile.log(this, error.getMessage());
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            finish(); return;
        }
        ArrayList<String> paths = getIntent().getStringArrayListExtra(ROOTS);
        if (paths == null || paths.isEmpty()) { finish(); return; }
        for (String path : paths) roots.add(java.nio.file.Paths.get(path));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BACKGROUND);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(dp(24), dp(28), dp(24), dp(24)); scroll.addView(layout);
        text(layout, "BODRIOSAFÍO", 23, true);
        text(layout, "Actualizaciones del addon", 21, true);
        installed = text(layout, "Versión instalada: comprobando…", 16, false);
        published = text(layout, "Versión disponible: comprobando…", 16, false);
        status = text(layout, "Buscando la última versión…", 15, false);
        progress = new DownloadProgressUi(this, layout, layout.getChildCount());
        update = button(layout, "Actualizar addon", true); update.setEnabled(false);
        check = button(layout, "Buscar actualizaciones", false);
        file = button(layout, "Actualizar desde un archivo", false);
        bundled = button(layout, "Usar versión incluida (0.3.16)", false);
        Button back = button(layout, "Volver", false);
        update.setOnClickListener(v -> download());
        check.setOnClickListener(v -> refresh());
        file.setOnClickListener(v -> chooseFile());
        bundled.setOnClickListener(v -> installBundled());
        back.setOnClickListener(v -> onBackPressed());
        setContentView(scroll); refresh();
    }

    private interface Job { String run() throws Exception; }
    private void work(String message, Job job) {
        if (busy) return;
        busy = true; controls(false); status.setVisibility(View.GONE); progress.begin();
        new Thread(() -> {
            String result; boolean success;
            try {
                if (MinecraftActivityState.isRunning(this)) throw new Exception("Cierra Minecraft antes de actualizar sus packs.");
                AddonInstaller.recover(roots, journals);
                result = job.run(); success = true;
                BodriosafioProfile.log(getApplicationContext(), result);
            } catch (Exception error) {
                result = error.getMessage() == null ? "No se pudo completar la actualización." : error.getMessage(); success = false;
                BodriosafioProfile.log(getApplicationContext(), "Actualización del addon: " + result);
            }
            String text = result; boolean ok = success;
            runOnUiThread(() -> {
                busy = false;
                if (isDestroyed() || isFinishing()) return;
                if (ok) progress.complete(); else progress.hide();
                status.setText(text); status.setVisibility(View.VISIBLE); controls(true);
                if (!ok && channel == null) published.setText("Versión disponible: no se pudo comprobar");
                if (ok) showInstalled();
            });
        }, "bodriosafio-addon-update").start();
    }

    private void controls(boolean enabled) {
        check.setEnabled(enabled); file.setEnabled(enabled); bundled.setEnabled(enabled);
        update.setEnabled(enabled && channel != null);
    }

    private void refresh() {
        if (busy) return;
        channel = null; published.setText("Versión disponible: comprobando…");
        work("Buscando la última versión…", () -> {
            AddonChannel latest = new AddonChannel(new String(AddonChannel.get(AddonChannel.URL, 128 * 1024), StandardCharsets.UTF_8), "android", org.levimc.launcher.BuildConfig.VERSION_NAME);
            channel = latest;
            runOnUiThread(() -> { if (!isDestroyed()) published.setText("Versión disponible: " + latest.version); });
            int[] bp = AddonInstaller.installed(roots, 0), rp = AddonInstaller.installed(roots, 1);
            boolean current = bp != null && rp != null && AddonChannel.compare(bp, latest.behaviorVersion) >= 0 && AddonChannel.compare(rp, latest.resourceVersion) >= 0;
            return current ? "El addon está al día. Puedes volver a instalarlo si necesitas reparar sus archivos." : "Hay una actualización lista. Pulsa Actualizar addon.";
        });
        showInstalled();
    }

    private void showInstalled() {
        try {
            int[] bp = AddonInstaller.installed(roots, 0), rp = AddonInstaller.installed(roots, 1);
            installed.setText("Versión instalada: " + (bp == null ? "sin BP" : AddonChannel.display(bp)) + " · RP " + (rp == null ? "sin instalar" : AddonChannel.display(rp)));
        } catch (Exception e) { installed.setText("Versión instalada: no se pudo comprobar"); }
    }

    private void download() {
        AddonChannel latest = channel;
        if (latest == null) return;
        work("Descargando y comprobando Bodriosafío " + latest.version + "…", () -> {
            Files.createDirectories(cache);
            Path saved = cache.resolve(latest.sha256 + ".mcaddon");
            byte[] bytes = Files.isRegularFile(saved) && Files.size(saved) <= AddonChannel.MAX_ARCHIVE ? Files.readAllBytes(saved) : null;
            if (bytes != null) { try { latest.verify(bytes); } catch (Exception badCache) { bytes = null; } }
            if (bytes == null) {
                bytes = AddonChannel.get(latest.downloadUrl, latest.size, progress::update); latest.verify(bytes);
                Path temporary = Files.createTempFile(cache, "download-", ".tmp");
                try { Files.write(temporary, bytes); Files.move(temporary, saved, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                finally { Files.deleteIfExists(temporary); }
            }
            progress.update(bytes.length, bytes.length);
            try (AddonInstaller.Prepared prepared = AddonInstaller.prepare(bytes, cache)) {
                if (AddonChannel.compare(prepared.behavior.version, latest.behaviorVersion) != 0 || AddonChannel.compare(prepared.resource.version, latest.resourceVersion) != 0)
                    throw new Exception("La versión descargada no coincide con el canal. No se instaló.");
                return new AddonInstaller().install(prepared, roots, journals);
            }
        });
    }

    private void chooseFile() {
        if (busy) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        startActivityForResult(intent, FILE_REQUEST);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != FILE_REQUEST || result != RESULT_OK || data == null || data.getData() == null) return;
        android.net.Uri uri = data.getData();
        work("Comprobando el archivo del addon…", () -> {
            byte[] bytes;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new Exception("No se pudo abrir el archivo.");
                bytes = AddonChannel.read(input, AddonChannel.MAX_ARCHIVE);
            }
            try (AddonInstaller.Prepared prepared = AddonInstaller.prepare(bytes, cache)) { return new AddonInstaller().install(prepared, roots, journals); }
        });
    }

    private void installBundled() {
        work("Preparando la versión incluida…", () -> {
            byte[] bytes;
            try (InputStream input = getAssets().open("bodriosafio/Bodriosafio_0_3_16.mcaddon")) { bytes = AddonChannel.read(input, AddonChannel.MAX_ARCHIVE); }
            if (!ProfileRules.ADDON_SHA256.equals(AddonChannel.digest(bytes))) throw new Exception("No se pudo verificar el addon incluido.");
            try (AddonInstaller.Prepared prepared = AddonInstaller.prepare(bytes, cache)) { return new AddonInstaller().install(prepared, roots, journals); }
        });
    }

    @Override public void onBackPressed() {
        if (busy) { Toast.makeText(this, "Espera a que termine la operación.", Toast.LENGTH_SHORT).show(); return; }
        super.onBackPressed();
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout layout, String value, int size, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextColor(WHITE); view.setTextSize(size);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(16); layout.addView(view, params); return view;
    }
    private Button button(LinearLayout layout, String title, boolean primary) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setTextSize(16); b.setTextColor(primary ? BACKGROUND : WHITE);
        b.setMinHeight(dp(50)); b.setBackgroundTintList(null);
        GradientDrawable background = new GradientDrawable(); background.setColor(primary ? ACCENT : SURFACE); background.setCornerRadius(dp(12)); b.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(10); layout.addView(b, params); return b;
    }
}
