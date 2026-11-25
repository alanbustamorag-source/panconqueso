// Bodriosafío: modificación del 23-11-2026 para el nuevo canal y compatibilidad del perfil.
package org.levimc.launcher.bodriosafio;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import org.levimc.launcher.core.minecraft.MinecraftLauncher;
import org.levimc.launcher.core.versions.GameVersion;
import org.levimc.launcher.core.versions.VersionManager;
import org.levimc.launcher.preloader.PreloaderSignatureRulesManager;
import org.levimc.launcher.ui.activities.BaseActivity;
import org.levimc.launcher.ui.activities.MainActivity;
import org.levimc.launcher.util.LauncherStorage;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.levimc.launcher.core.minecraft.MinecraftProcessRestarterKt.ACTION_MAIN_ACTIVITY_FIRST_DRAWN;
import static org.levimc.launcher.core.minecraft.MinecraftProcessRestarterKt.EXTRA_CLOSE_RESTART_ACTIVITY_ON_FIRST_DRAW;

/** Simple Bodriosafio home, backed by LeviLaunchroid's existing game runtime. */
public class BodriosafioActivity extends BaseActivity {
    public static final String ADVANCED_EXTRA = "bodriosafio.advanced";
    private static final int BACKGROUND = Color.rgb(14, 23, 20);
    private static final int SURFACE = Color.rgb(25, 39, 32);
    private static final int ACCENT = Color.rgb(157, 227, 178);
    private static final int WHITE = Color.rgb(234, 243, 237);
    private static final int MUTED = Color.rgb(167, 188, 174);
    private TextView actualVersion, status;
    private Button play, addon;
    private GameVersion installed;
    private boolean ready;

    @Override protected boolean shouldSkipNavBar() { return true; }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(BACKGROUND);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(28), dp(24), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = label("B", 29, BACKGROUND, true);
        logo.setGravity(Gravity.CENTER); logo.setBackground(rounded(ACCENT, 10));
        header.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout brand = new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL);
        brand.addView(label("BODRIOSAFÍO", 24, WHITE, true));
        brand.addView(label("LAUNCHER PARA ANDROID", 11, MUTED, false));
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(-2, -2); brandParams.leftMargin = dp(14);
        header.addView(brand, brandParams); content.addView(header);

        TextView headline = label("Tu próxima partida empieza acá.", 25, WHITE, true);
        add(content, headline, 32, 14);
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18)); card.setBackground(rounded(SURFACE, 18));
        card.addView(label("MINECRAFT", 12, MUTED, false));
        add(card, label("Versión con módulos nativos: " + ProfileRules.NATIVE_VERSION, 16, WHITE, false), 12, 0);
        actualVersion = label("Perfil seleccionado: buscando…", 16, WHITE, false);
        add(card, actualVersion, 8, 0); add(content, card, 6, 0);
        status = label("Buscando tu instalación de Minecraft…", 15, WHITE, false);
        add(content, status, 22, 16);

        play = button("Jugar", true); play.setEnabled(false); add(content, play, 6, 0);
        addon = button("Actualizar addon", false); addon.setEnabled(false); add(content, addon, 10, 0);
        LinearLayout options = new LinearLayout(this);
        String[] titles = {"Registro", "Ayuda", "Créditos"};
        for (int i = 0; i < titles.length; i++) {
            final int index = i;
            Button option = button(titles[i], false);
            option.setTextSize(13);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) p.leftMargin = dp(8);
            options.addView(option, p);
            option.setOnClickListener(v -> { if (index == 0) showLog(); else if (index == 1) help(); else credits(); });
        }
        add(content, options, 10, 0);
        Button advanced = button("Herramientas", false); add(content, advanced, 10, 0);
        advanced.setOnClickListener(v -> {
            if (org.levimc.launcher.core.minecraft.LaunchPreparation.allowContentChanges(this))
                startActivity(new Intent(this, MainActivity.class).putExtra(ADVANCED_EXTRA, true));
        });
        play.setOnClickListener(v -> startGame()); addon.setOnClickListener(v -> importAddon());
        org.levimc.launcher.bodriosafio.update.LauncherSelfUpdate.attach(this, scroll);
        setContentView(scroll);
        LaunchLoadingUi.attach(this, status);
        closeRestartAfterFirstDraw(scroll);
        LauncherStorage.ensureNoMedia(getApplicationContext());
        PreloaderSignatureRulesManager.refreshOnLauncherStart(getApplicationContext());
        refreshInstallation(false);
    }

    private void refreshInstallation(boolean reload) {
        ready = false; play.setEnabled(false); addon.setEnabled(false);
        VersionManager.initializeAsync(this, manager -> {
            if (reload) manager.reload();
            GameVersion selected = manager.getSelectedVersion();
            GameVersion result = BodriosafioProfile.forHome(selected, manager.getInstalledVersions(), manager.getCustomVersions());
            if (result != null && selected != result) manager.selectVersion(result);
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                installed = result; ready = true;
                String version = result == null ? null : result.versionCode;
                boolean canPlay = ProfileRules.nativeCompatible(version);
                actualVersion.setText("Perfil seleccionado: " + ProfileRules.displayVersion(version));
                if (result == null) {
                    status.setText("Instala tu copia de Minecraft desde Google Play para comenzar.");
                } else if (canPlay) {
                    status.setText("Pulsa Jugar para iniciar Minecraft.");
                } else {
                    status.setText("Los módulos nativos aún no están adaptados a este perfil. Si ya importaste Minecraft "
                            + ProfileRules.NATIVE_VERSION + ", puedes elegirlo en Herramientas.");
                }
                play.setEnabled(canPlay);
                play.setAlpha(canPlay ? 1f : .55f);
                addon.setEnabled(ProfileRules.compatible(version));
                org.levimc.launcher.bodriosafio.update.AddonUpdateActivity.checkHome(this, result, addon);
            });
        });
    }

    @Override protected void onResume() {
        super.onResume();
        org.levimc.launcher.bodriosafio.update.LauncherSelfUpdate.resume(this);
        if (ready && play != null && !org.levimc.launcher.core.minecraft.LaunchPreparation.isBusy()) {
            refreshInstallation(true);
        }
    }

    private void startGame() {
        if (!ready) return;
        if (installed == null) {
            showText("Minecraft no encontrado", "Instala Minecraft Bedrock desde Google Play. Esta APK no incluye el juego."); return;
        }
        play.setEnabled(false); status.setText("Preparando complementos… La primera vez puede tardar.");
        LaunchLoadingUi.show(this);
        Intent source = new Intent();
        if (getIntent().getData() != null && "minecraft".equalsIgnoreCase(getIntent().getData().getScheme())) {
            source.setAction(Intent.ACTION_VIEW); source.setData(getIntent().getData());
        }
        new MinecraftLauncher(this).launch(source, installed, new MinecraftLauncher.LaunchCallback() {
            @Override public void onLaunchStarted() {
                LaunchLoadingUi.hide(BodriosafioActivity.this);
                status.setText("Abriendo Minecraft…");
            }
            @Override public void onLaunchFailed(Exception error) {
                BodriosafioProfile.log(getApplicationContext(), "Error de inicio: " + error.getMessage());
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    LaunchLoadingUi.hide(BodriosafioActivity.this);
                    play.setEnabled(true); status.setText("No se pudo iniciar Minecraft. Revisa el mensaje y el registro.");
                    showText("No se pudo iniciar Minecraft", error.getMessage());
                });
            }
        });
    }

    private void importAddon() {
        org.levimc.launcher.bodriosafio.update.AddonUpdateActivity.open(this, installed);
    }

    private void help() {
        showText("Ayuda", "Bodriosafío Launcher " + org.levimc.launcher.BuildConfig.VERSION_NAME
                + "\n\nJugar requiere el perfil ARM64 " + ProfileRules.NATIVE_VERSION
                + " para cargar el Nether ampliado y Anticombustión. Puedes seleccionar una copia importada en Herramientas."
                + "\n\nEn Minecraft 26.66.1 puedes actualizar los packs del perfil. El arranque con los dos módulos aún está pendiente para esa versión."
                + "\n\nActualizar addon descarga y verifica los packs. Cierra Minecraft antes de actualizarlos."
                + "\n\nActualizar launcher busca nuevas versiones en GitHub. Por Wi-Fi se descargan automáticamente. Android te pide confirmar la instalación."
                + "\n\nNecesitas Minecraft instalado desde Google Play. Los mundos se guardan en el perfil que elijas.");
    }

    private void showLog() {
        try {
            java.io.File file = BodriosafioProfile.logFile(this);
            byte[] bytes = file.isFile() ? Files.readAllBytes(file.toPath()) : new byte[0];
            String value = new String(bytes, StandardCharsets.UTF_8);
            if (value.length() > 24000) value = value.substring(value.length() - 24000);
            if (value.isEmpty()) value = "Todavía no hay eventos registrados.";
            if (installed != null && ProfileRules.nativeCompatible(installed.versionCode))
                value = NativeEffectsProfile.appendStatus(this, value);
            showText("Registro", value, true);
        } catch (Exception error) { showText("Registro", error.getMessage()); }
    }

    private void credits() {
        try (InputStream input = getAssets().open("bodriosafio/CREDITOS_Y_LICENCIAS.txt")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
            showText("Créditos", new String(output.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception error) { showText("Créditos", "Bodriosafío: proyecto de Alan Bustamante.\nBasado en LeviLaunchroid de LeviMC (Apache-2.0).\nBuildLimitChanger de Zeuroux (MIT)."); }
    }

    private void showText(String title, String message) {
        showText(title, message, false);
    }

    private void showText(String title, String message, boolean scrollToEnd) {
        TextView body = new TextView(this); body.setText(message == null ? "Sin detalles." : message);
        body.setTextSize(15); body.setTextIsSelectable(true); body.setPadding(dp(24), dp(12), dp(24), dp(20));
        ScrollView scroll = new ScrollView(this); scroll.addView(body);
        new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Aceptar", null).show();
        if (scrollToEnd) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable result = new GradientDrawable(); result.setColor(color); result.setCornerRadius(dp(radius)); return result;
    }
    private TextView label(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); return text;
    }
    private Button button(String title, boolean primary) {
        Button button = new Button(this); button.setText(title); button.setAllCaps(false); button.setTextSize(16);
        button.setTextColor(primary ? BACKGROUND : WHITE); button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundTintList(null); button.setBackground(rounded(primary ? ACCENT : SURFACE, 12));
        button.setMinHeight(dp(50)); button.setMinimumHeight(dp(50)); button.setPadding(dp(12), dp(10), dp(12), dp(10));
        return button;
    }
    private void add(LinearLayout parent, View child, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top); params.bottomMargin = dp(bottom); parent.addView(child, params);
    }

    private void closeRestartAfterFirstDraw(View root) {
        if (!getIntent().getBooleanExtra(EXTRA_CLOSE_RESTART_ACTIVITY_ON_FIRST_DRAW, false)) return;
        getIntent().removeExtra(EXTRA_CLOSE_RESTART_ACTIVITY_ON_FIRST_DRAW);
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(this);
                root.post(() -> sendBroadcast(new Intent(ACTION_MAIN_ACTIVITY_FIRST_DRAWN).setPackage(getPackageName())));
                return true;
            }
        });
    }
}
