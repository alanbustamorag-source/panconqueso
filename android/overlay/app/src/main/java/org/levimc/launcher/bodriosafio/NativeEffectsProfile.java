// Bodriosafío: modificación del 23-11-2026 para el nuevo canal y compatibilidad del perfil.
package org.levimc.launcher.bodriosafio;

import android.content.Context;
import android.system.Os;
import org.json.JSONArray;
import org.json.JSONObject;
import org.levimc.launcher.core.versions.GameVersion;
import org.levimc.launcher.core.mods.ModManager;
import org.levimc.launcher.core.mods.Mod;
import org.levimc.launcher.core.minecraft.MinecraftActivityState;
import org.levimc.launcher.core.minecraft.MinecraftLauncher;
import org.levimc.launcher.util.LauncherStorage;
import org.levimc.launcher.bodriosafio.update.AddonChannel;
import org.levimc.launcher.bodriosafio.update.AddonInstaller;
import org.levimc.launcher.bodriosafio.update.StoragePaths;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Exact-build native registration plus the matching complete addon. */
public final class NativeEffectsProfile {
    public static final String MOD_ID = "bodriosafio_native_effects";
    private NativeEffectsProfile() {}

    public static void prepareForLaunch(Context context, GameVersion version) throws Exception {
        ProfileRules.NativeProfile profile = ProfileRules.requireNativeProfile(version == null ? null : version.versionCode);
        if (version == null || version.modsDir == null) throw new Exception("No se encontró el perfil de Minecraft.");
        if (MinecraftActivityState.isRunning(context)) throw new Exception("Cierra Minecraft antes de preparar Anticombustión.");
        File directory = new File(version.modsDir, MOD_ID);
        Files.createDirectories(directory.toPath());
        File library = new File(directory, "libBodriosafioEffects.so");
        byte[] bytes;
        try (InputStream input = context.getAssets().open("bodriosafio/" + profile.effectsAsset)) {
            bytes = AddonChannel.read(input, 1024 * 1024);
        }
        if (!profile.effectsSha256.equals(AddonChannel.digest(bytes))) throw new Exception("No se pudo verificar el módulo nativo de Anticombustión.");
        if (!library.isFile() || !profile.effectsSha256.equals(AddonChannel.digest(Files.readAllBytes(library.toPath()))))
            ProfileRules.atomicWrite(library, bytes);
        JSONObject manifest = new JSONObject();
        manifest.put("type", "preload-native");
        manifest.put("name", "Anticombustión nativa");
        manifest.put("entry", library.getName());
        manifest.put("author", "Alan Bustamante · Bodriosafío");
        manifest.put("version", profile.effectsVersion);
        manifest.put("description", "Anticombustión para el motor ARM64 " + profile.version + ".");
        JSONArray versions = new JSONArray();
        versions.put(profile.version).put("1." + profile.version);
        manifest.put("minecraft_versions", versions);
        ProfileRules.atomicWrite(new File(directory, "manifest.json"), manifest.toString(2).getBytes(StandardCharsets.UTF_8));
        File statusFile = new File(directory, "native-effects-status.json");
        Files.deleteIfExists(statusFile.toPath());
        String statusPath = statusFile.getAbsolutePath();
        Os.setenv("BODRIOSAFIO_EFFECTS_STATUS", statusPath, true);
        context.getSharedPreferences("bodriosafio-native-effects", Context.MODE_PRIVATE).edit()
            .putString("status_path", statusPath).putString("status_version", profile.version).apply();
        ModManager manager = ModManager.getInstance();
        manager.reloadMods();
        manager.setModEnabled(MOD_ID, true);
        boolean found = false;
        for (Mod mod : manager.getMods()) if (MOD_ID.equals(mod.getId()) && mod.isEnabled()) found = true;
        if (!found) throw new Exception("No se pudo activar el módulo de Anticombustión.");
        ensureBundledAddon(context, version);
        BodriosafioProfile.log(context, "Anticombustión nativa preparada; la confirmación del registro aparece tras inicializar Minecraft.");
    }

    private static void ensureBundledAddon(Context context, GameVersion version) throws Exception {
        BodriosafioProfile.log(context, "Inicio: comprobando el addon incluido (BP y RP).");
        List<Path> roots = new ArrayList<>();
        for (boolean external : new boolean[] {false, true}) {
            Path path = LauncherStorage.getStorageGameDataDir(context, MinecraftLauncher.getStorageProfileId(version), version.versionIsolation, external).getCanonicalFile().toPath();
            if (!roots.contains(path)) roots.add(path);
        }
        Path journals = StoragePaths.privateChild(context.getFilesDir(), "bodriosafio-update-journals");
        AddonInstaller.recover(roots, journals);
        int[] bp = AddonInstaller.installed(roots, 0), rp = AddonInstaller.installed(roots, 1);
        if (bp != null && rp != null && AddonChannel.compare(bp, new int[] {0,3,16}) >= 0
            && AddonChannel.compare(rp, new int[] {1,5,15}) >= 0) return;
        byte[] bytes;
        try (InputStream input = context.getAssets().open("bodriosafio/Bodriosafio_0_3_16.mcaddon")) {
            bytes = AddonChannel.read(input, AddonChannel.MAX_ARCHIVE);
        }
        if (!ProfileRules.ADDON_SHA256.equals(AddonChannel.digest(bytes))) throw new Exception("No se pudo verificar el addon de Anticombustión.");
        Path cache = StoragePaths.privateChild(context.getCacheDir(), "bodriosafio-native-addon");
        Files.createDirectories(cache);
        try (AddonInstaller.Prepared prepared = AddonInstaller.prepare(bytes, cache)) {
            if (AddonChannel.compare(prepared.behavior.version, new int[] {0,3,16}) != 0
                || AddonChannel.compare(prepared.resource.version, new int[] {1,5,15}) != 0)
                throw new Exception("El addon incluido no coincide con el módulo de Anticombustión.");
            String result = new AddonInstaller().install(prepared, roots, journals);
            BodriosafioProfile.log(context, "Addon nativo incluido: " + result);
        }
    }

    public static void requireLoaded(Set<String> loaded, String gameVersion) {
        if (!ProfileRules.nativeCompatible(gameVersion)) throw new IllegalStateException(
            "Esta compilación de Minecraft aún necesita adaptar los módulos nativos de Bodriosafío.");
        if (!loaded.contains(MOD_ID)) throw new IllegalStateException(
            "No se pudo cargar Anticombustión nativa. Revisa el Registro del launcher para Minecraft " + gameVersion + ".");
    }

    public static String appendStatus(Context context, String previous, GameVersion version) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("bodriosafio-native-effects", Context.MODE_PRIVATE);
        if (version == null || version.modsDir == null || !ProfileRules.displayVersion(version.versionCode).equals(prefs.getString("status_version", "")))
            return previous;
        String path = new File(new File(version.modsDir, MOD_ID), "native-effects-status.json").getAbsolutePath();
        if (!path.equals(prefs.getString("status_path", null))) return previous;
        try {
            File file = new File(path);
            if (!file.isFile() || file.length() > 16 * 1024) return previous;
            JSONObject status = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            String phase = status.optString("phase");
            String detail = "registered".equals(phase)
                ? "REGISTRADA · ID 39 · consulta por nombre e ID confirmada."
                : "waiting_for_engine_init".equals(phase)
                    ? "Módulo cargado; esperando la inicialización del motor."
                    : "ERROR · " + status.optString("detail");
            return previous + "\n\nAnticombustión nativa: " + detail;
        } catch (Exception error) { return previous + "\nNo se pudo leer el estado nativo de Anticombustión."; }
    }
}
