// Bodriosafío: modificación del 23-11-2026 para el nuevo canal y compatibilidad del perfil.
package org.levimc.launcher.bodriosafio.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.levimc.launcher.bodriosafio.BodriosafioProfile;
import org.levimc.launcher.bodriosafio.DownloadProgressUi;
import org.levimc.launcher.core.minecraft.MinecraftActivityState;
import java.io.File;
import java.lang.ref.WeakReference;

/** Check on the launcher home, download on Wi-Fi and use Android's installer. */
public final class LauncherSelfUpdate {
    private static WeakReference<LauncherSelfUpdate> active = new WeakReference<>(null);
    private final WeakReference<Activity> owner;
    private final Button button;
    private final DownloadProgressUi progress;
    private final Context app;
    private final File cache;
    private volatile boolean busy;
    private LauncherChannel available;
    private File prepared;
    private boolean permissionReturn;
    private long promptedCode = -1;

    private LauncherSelfUpdate(Activity host, Button target, DownloadProgressUi progress) {
        owner = new WeakReference<>(host); button = target;
        this.progress = progress;
        app = host.getApplicationContext();
        cache = new File(app.getCacheDir(), "bodriosafio-launcher-updates");
        button.setOnClickListener(view -> {
            if (busy) return;
            if (prepared != null) confirmInstall();
            else if (available != null) download();
            else check(false);
        });
    }

    /** The launcher supplies its existing home view; no resource IDs change. */
    public static void attach(Activity host, View home) {
        if (!(home instanceof ScrollView)) return;
        View child = ((ScrollView)home).getChildAt(0);
        if (!(child instanceof LinearLayout)) return;
        LinearLayout layout = (LinearLayout)child;
        int after = -1;
        for (int i = 0; i < layout.getChildCount(); i++) {
            View view = layout.getChildAt(i);
            if ("bodriosafio.launcher.update".equals(view.getTag())) return;
            if (view instanceof Button && "Actualizar addon".contentEquals(((Button)view).getText())) after = i + 1;
        }
        Button target = new Button(host);
        target.setTag("bodriosafio.launcher.update");
        target.setText("Actualizar launcher"); target.setAllCaps(false); target.setTextSize(16);
        target.setTextColor(Color.rgb(234, 243, 237)); target.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        int min = Math.round(50 * host.getResources().getDisplayMetrics().density);
        int margin = Math.round(10 * host.getResources().getDisplayMetrics().density);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(25, 39, 32)); background.setCornerRadius(margin * 1.2f);
        target.setBackgroundTintList(null); target.setBackground(background);
        target.setMinHeight(min); target.setMinimumHeight(min);
        target.setPadding(margin, margin, margin, margin);
        LinearLayout.LayoutParams parameters = new LinearLayout.LayoutParams(-1, -2); parameters.topMargin = margin;
        layout.addView(target, after < 0 ? layout.getChildCount() : after, parameters);
        DownloadProgressUi progress = new DownloadProgressUi(host, layout, layout.indexOfChild(target) + 1);
        LauncherSelfUpdate controller = new LauncherSelfUpdate(host, target, progress);
        active = new WeakReference<>(controller);
        controller.check(true);
    }

    /** Return from Android's install permission page without downloading twice. */
    public static void resume(Activity host) {
        LauncherSelfUpdate current = active.get();
        if (current == null || current.owner.get() != host) return;
        host.getWindow().getDecorView().postDelayed(() -> {
            if (current.permissionReturn) {
                current.permissionReturn = false;
                if (host.getPackageManager().canRequestPackageInstalls()) current.install();
                else current.message("Instalación pendiente", "Para actualizar desde el launcher, permite instalar aplicaciones desde Bodriosafío y vuelve a pulsar Actualizar launcher.");
            } else current.promptWhenVisible();
        }, 350);
    }

    /** The advanced settings must use Bodriosafio's channel as well. */
    public static void open(Activity host) {
        LauncherSelfUpdate current = active.get();
        if (current != null && current.owner.get() == host) {
            if (!current.busy) {
                if (current.prepared != null) current.confirmInstall();
                else if (current.available != null) current.download();
                else current.check(false);
            }
        } else host.startActivity(new Intent().setClassName(host, "org.levimc.launcher.bodriosafio.BodriosafioActivity"));
    }

    private boolean alive(Activity host) { return host != null && !host.isFinishing() && !host.isDestroyed(); }
    private void ui(Runnable work) {
        Activity host = owner.get();
        if (alive(host)) host.runOnUiThread(() -> { if (alive(host)) work.run(); });
    }
    private void label(String text) { ui(() -> button.setText(text)); }
    private void log(String text) { BodriosafioProfile.log(app, "Actualización del launcher: " + text); }
    private void message(String title, String text) {
        Activity host = owner.get();
        if (alive(host)) new AlertDialog.Builder(host).setTitle(title).setMessage(text).setPositiveButton("Aceptar", null).show();
    }
    private boolean unmetered() {
        ConnectivityManager manager = (ConnectivityManager)app.getSystemService(Context.CONNECTIVITY_SERVICE);
        return manager != null && manager.getActiveNetwork() != null && !manager.isActiveNetworkMetered();
    }

    private void check(boolean automatic) {
        if (busy) return;
        busy = true; button.setEnabled(false);
        if (!automatic) button.setText("Buscando actualización del launcher…");
        new Thread(() -> {
            LauncherChannel result = null; Exception failure = null;
            try {
                try { result = LauncherChannel.latest(); }
                catch (Exception error) {
                    String cached = app.getSharedPreferences("bodriosafio-launcher-update", Context.MODE_PRIVATE).getString("feed", null);
                    if (cached == null) throw error;
                    result = new LauncherChannel(cached);
                    if (!new File(cache, result.sha256 + ".apk").isFile()) throw error;
                    log("Usando una descarga pendiente ya comprobada; GitHub no está disponible.");
                }
                if (result != null) {
                    if (result.newerThan(LauncherApkVerifier.installedCode(app)) && Build.VERSION.SDK_INT < result.minSdk)
                        throw new Exception("La nueva versión necesita Android API " + result.minSdk + " o posterior.");
                    app.getSharedPreferences("bodriosafio-launcher-update", Context.MODE_PRIVATE).edit().putString("feed", result.asJson()).apply();
                }
            } catch (Exception error) { failure = error; log(error.toString()); }
            LauncherChannel channel = result; Exception error = failure;
            ui(() -> {
                busy = false; button.setEnabled(true);
                if (error != null) {
                    button.setText("Actualizar launcher");
                    if (!automatic) message("No se pudo comprobar", error.getMessage());
                    return;
                }
                if (channel == null) {
                    button.setText("Actualizar launcher");
                    if (!automatic) message("Actualizar launcher", "El canal del launcher todavía no está publicado en GitHub.");
                    return;
                }
                try {
                    if (!channel.newerThan(LauncherApkVerifier.installedCode(app))) {
                        available = null; prepared = null; button.setText("Launcher al día");
                        if (!automatic) message("Launcher al día", "Ya tienes la revisión más reciente del launcher.");
                        return;
                    }
                    available = channel;
                    button.setText("Actualizar launcher · " + channel.version);
                    // A tap also permits downloads using mobile data.
                    if (!automatic || unmetered() || new File(cache, channel.sha256 + ".apk").isFile()) download();
                } catch (Exception problem) { log(problem.toString()); if (!automatic) message("No se pudo comprobar", problem.getMessage()); }
            });
        }, "bodriosafio-launcher-check").start();
    }

    private void download() {
        LauncherChannel channel = available;
        if (busy || channel == null) return;
        busy = true; button.setEnabled(false); button.setText("Descargando launcher…");
        progress.begin();
        new Thread(() -> {
            try {
                File file = channel.download(cache, progress::update);
                LauncherApkVerifier.verify(app, channel, file);
                log("APK " + channel.version + ", revisión " + channel.versionCode + ": hash, paquete y firma comprobados.");
                ui(() -> { progress.complete(); busy = false; button.setEnabled(true); prepared = file; button.setText("Instalar actualización del launcher"); promptWhenVisible(); });
            } catch (Exception error) {
                log(error.toString());
                ui(() -> { progress.hide(); busy = false; button.setEnabled(true); button.setText("Reintentar actualización del launcher"); message("No se pudo actualizar", error.getMessage()); });
            }
        }, "bodriosafio-launcher-download").start();
    }

    private void promptWhenVisible() {
        Activity host = owner.get();
        if (prepared != null && available != null && promptedCode != available.versionCode
            && alive(host) && host.hasWindowFocus() && !MinecraftActivityState.isRunning(app)) confirmInstall();
    }

    private void confirmInstall() {
        Activity host = owner.get();
        if (!alive(host) || available == null || prepared == null) return;
        if (MinecraftActivityState.isRunning(app)) {
            message("Actualización preparada", "Cierra Minecraft antes de actualizar el launcher."); return;
        }
        promptedCode = available.versionCode;
        new AlertDialog.Builder(host).setTitle("Actualizar Bodriosafío")
            .setMessage("La versión " + available.version + " está lista. Android te pedirá confirmar la actualización.\n\n" + available.notes)
            .setPositiveButton("Actualizar", (dialog, which) -> install())
            .setNegativeButton("Más tarde", null).show();
    }

    private void install() {
        Activity host = owner.get();
        LauncherChannel channel = available; File file = prepared;
        if (!alive(host) || channel == null || file == null || busy) return;
        if (MinecraftActivityState.isRunning(app)) { message("Cierra Minecraft", "La actualización podrá instalarse cuando cierres el juego."); return; }
        if (!host.getPackageManager().canRequestPackageInstalls()) {
            permissionReturn = true;
            try { host.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + host.getPackageName()))); }
            catch (Exception error) { permissionReturn = false; message("Permiso de instalación", error.getMessage()); }
            return;
        }
        busy = true; button.setEnabled(false); button.setText("Comprobando actualización…");
        new Thread(() -> {
            try {
                // Re-check immediately before handing the file to Android,
                // including revision and installed signing certificate.
                LauncherApkVerifier.verify(app, channel, file);
                ui(() -> {
                    if (MinecraftActivityState.isRunning(app)) { message("Cierra Minecraft", "La actualización sigue preparada para cuando cierres el juego."); return; }
                    try {
                        Uri uri = FileProvider.getUriForFile(host, host.getPackageName() + ".fileprovider", file);
                        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.setClipData(ClipData.newRawUri("Bodriosafío", uri));
                        host.startActivity(intent);
                    } catch (Exception error) { message("No se pudo abrir el instalador", error.getMessage()); }
                });
            } catch (Exception error) { log(error.toString()); ui(() -> message("No se pudo instalar", error.getMessage())); }
            finally { ui(() -> { busy = false; button.setEnabled(true); button.setText("Instalar actualización del launcher"); }); }
        }, "bodriosafio-launcher-install").start();
    }
}
