// Bodriosafío: modificación del 23-11-2026 para el nuevo canal y compatibilidad del perfil.
package org.levimc.launcher.bodriosafio;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import org.levimc.launcher.core.content.ResourcePackManager;
import org.levimc.launcher.core.minecraft.MinecraftLauncher;
import org.levimc.launcher.core.mods.Mod;
import org.levimc.launcher.core.mods.ModManager;
import org.levimc.launcher.core.versions.GameVersion;
import org.levimc.launcher.util.LauncherStorage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Installs only the pinned, bundled profile into this launcher's own storage. */
public final class BodriosafioProfile {
    private BodriosafioProfile() {}

    private static byte[] asset(Context context, String name, String expected) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = context.getAssets().open("bodriosafio/" + name)) {
            byte[] buffer = new byte[16384]; int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) continue;
                output.write(buffer, 0, count);
                if (output.size() > 16 * 1024 * 1024) throw new IOException("El archivo incluido excede el tamaño previsto.");
            }
        }
        byte[] bytes = output.toByteArray();
        if (!ProfileRules.sha256(bytes).equals(expected)) throw new IOException("No se pudo verificar el archivo incluido. No se cargó.");
        return bytes;
    }

    private static void installAsset(Context context, String name, File destination, String expected) throws IOException {
        byte[] bytes = asset(context, name, expected);
        if (destination.isFile() && destination.length() == bytes.length
                && ProfileRules.sha256(Files.readAllBytes(destination.toPath())).equals(expected)) return;
        ProfileRules.atomicWrite(destination, bytes);
    }

    private static File nativeConfigBase(Context context) throws IOException {
        String process = Application.getProcessName();
        if (!context.getPackageName().equals(process))
            throw new IOException("El complemento debe prepararse en el proceso principal del launcher.");
        // Follow the author's constructor search order exactly; avoid writing a config it will never read.
        File games = new File(Environment.getExternalStorageDirectory(), "games/" + process);
        if (games.isDirectory() && games.canWrite()) return games;
        File expected = new File(Environment.getExternalStorageDirectory(), "Android/media/" + process);
        File[] media = context.getExternalMediaDirs();
        if (media != null) for (File directory : media) {
            if (directory == null || !directory.getCanonicalFile().equals(expected.getCanonicalFile())) continue;
            if ((directory.isDirectory() || directory.mkdirs()) && directory.canWrite()) return directory;
        }
        throw new IOException("Android no permitió preparar la carpeta de Bodriosafío en el almacenamiento del dispositivo.");
    }

    public static synchronized void prepareForLaunch(Context context, GameVersion version) throws Exception {
        ProfileRules.requireNativeVersion(version == null ? null : version.versionCode);
        log(context, "Inicio: recuperando el perfil y preparando complementos.");
        org.levimc.launcher.bodriosafio.update.AddonUpdateActivity.recoverBeforeLaunch(context, version);
        if (version == null || !ProfileRules.compatible(version.versionCode))
            throw new IOException("Este perfil requiere Minecraft 26.66. Versión actual: " + ProfileRules.displayVersion(version == null ? null : version.versionCode));
        if (version.abiList == null || !version.abiList.contains("arm64-v8a"))
            throw new IOException("Esta APK necesita la versión de Minecraft de 64 bits (ARM64).");
        if (version.modsDir == null) throw new IOException("No se encontró el perfil de Minecraft.");
        File directory = new File(version.modsDir, ProfileRules.MOD_ID);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("No se pudo preparar el complemento.");
        File library = new File(directory, "libBuildLimitChanger.so");
        installAsset(context, "libBuildLimitChanger.so", library, ProfileRules.NATIVE_SHA256);

        JSONObject manifest = new JSONObject();
        manifest.put("type", "preload-native");
        manifest.put("name", "Complementos Bodriosafío");
        manifest.put("entry", library.getName());
        manifest.put("author", "Zeuroux · integración Bodriosafío / Alan Bustamante");
        manifest.put("version", "1.1");
        manifest.put("description", "BuildLimitChanger original de Zeuroux, licencia MIT. Perfil de Bodriosafío incluido.");
        JSONArray versions = new JSONArray();
        versions.put("26.66").put("26.66.*").put("1.26.66").put("1.26.66.*");
        manifest.put("minecraft_versions", versions);
        ProfileRules.atomicWrite(new File(directory, "manifest.json"), manifest.toString(2).getBytes(StandardCharsets.UTF_8));

        File config = new File(nativeConfigBase(context), "BuildLimitChanger/config.ini");
        boolean changed = ProfileRules.prepareConfig(config);
        ModManager manager = ModManager.getInstance();
        manager.setCurrentVersion(version);
        manager.reloadMods();
        manager.setModEnabled(ProfileRules.MOD_ID, true);
        boolean found = false;
        for (Mod mod : manager.getMods()) if (ProfileRules.MOD_ID.equals(mod.getId()) && mod.isEnabled()) found = true;
        if (!found) throw new IOException("No se pudo activar el complemento incluido.");
        NativeEffectsProfile.prepareForLaunch(context, version);
        log(context, "Perfil preparado para " + version.versionCode + "; config actualizado=" + changed);
    }

    public interface ImportCallback { void success(); void error(String message); }

    public static void importAddon(Context context, GameVersion version, ImportCallback callback) {
        new Thread(() -> {
            try {
                if (version == null) throw new IOException("Primero instala Minecraft para seleccionar su perfil.");
                File addon = new File(context.getCacheDir(), "Bodriosafio_0_3_15.mcaddon");
                installAsset(context, "Bodriosafio_0_3_15.mcaddon", addon, ProfileRules.ADDON_SHA256);
                List<File> roots = new ArrayList<>();
                for (boolean external : new boolean[] { false, true }) {
                    File root = LauncherStorage.getStorageGameDataDir(context, MinecraftLauncher.getStorageProfileId(version), version.versionIsolation, external).getCanonicalFile();
                    if (!roots.contains(root)) roots.add(root);
                }
                importRoot(context.getApplicationContext(), addon, roots, 0, callback);
            } catch (Exception error) { log(context, "Error: " + error.getMessage()); callback.error(error.getMessage()); }
        }, "bodriosafio-addon-import").start();
    }

    private static void importRoot(Context context, File addon, List<File> roots, int index, ImportCallback callback) {
        if (index == roots.size()) { log(context, "Addon 0.3.15 preparado en ambos almacenamientos del perfil."); callback.success(); return; }
        File gameData = roots.get(index);
        if (hasPack(new File(gameData, "behavior_packs"), "0c6db7ac-4c6f-4942-bae6-c16b5065036b", new int[] {0, 3, 15})
                && hasPack(new File(gameData, "resource_packs"), "1d763fb9-7cb5-429f-9789-b4b16e2301d1", new int[] {1, 5, 15})) {
            importRoot(context, addon, roots, index + 1, callback); return;
        }
        ResourcePackManager importer = new ResourcePackManager(context);
        importer.setPackDirectories(new File(gameData, "resource_packs"), new File(gameData, "behavior_packs"), new File(gameData, "skin_packs"));
        importer.importPack(Uri.fromFile(addon), new ResourcePackManager.PackOperationCallback() {
            @Override public void onSuccess(String ignored) {
                importer.shutdown();
                if (!hasPack(new File(gameData, "behavior_packs"), "0c6db7ac-4c6f-4942-bae6-c16b5065036b", new int[] {0, 3, 15})
                        || !hasPack(new File(gameData, "resource_packs"), "1d763fb9-7cb5-429f-9789-b4b16e2301d1", new int[] {1, 5, 15})) {
                    callback.error("No se encontraron ambos packs después de importarlos. Vuelve a intentarlo."); return;
                }
                importRoot(context, addon, roots, index + 1, callback);
            }
            @Override public void onError(String message) {
                importer.shutdown(); log(context, "Error de importación: " + message); callback.error(message);
            }
            @Override public void onProgress(int progress) { }
        });
    }

    private static boolean hasPack(File root, String uuid, int[] version) {
        File[] directories = root.listFiles(File::isDirectory);
        if (directories == null) return false;
        for (File directory : directories) {
            File manifest = new File(directory, "manifest.json");
            if (!manifest.isFile() || manifest.length() > 1024 * 1024) continue;
            try {
                String text = new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8);
                if (text.startsWith("\uFEFF")) text = text.substring(1);
                JSONObject header = new JSONObject(text).getJSONObject("header");
                if (!uuid.equalsIgnoreCase(header.getString("uuid"))) continue;
                JSONArray found = header.getJSONArray("version");
                if (found.length() == 3 && found.getInt(0) == version[0] && found.getInt(1) == version[1] && found.getInt(2) == version[2]) return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    public static File logFile(Context context) { return new File(context.getFilesDir(), "bodriosafio-launcher.log"); }

    public static synchronized void log(Context context, String message) {
        try (FileOutputStream output = new FileOutputStream(logFile(context), true)) {
            output.write((System.currentTimeMillis() + " | " + message + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }
}
