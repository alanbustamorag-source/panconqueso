// Bodriosafío: modificación del 23-11-2026 para el nuevo canal y compatibilidad del perfil.
package org.levimc.launcher.bodriosafio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Bodriosafio-specific profile logic. No Android dependencies, so it can be tested on the build host. */
public final class ProfileRules {
    public static final String NATIVE_VERSIONS = "26.60.4 y 26.66.1";
    public enum NativeProfile {
        LEGACY("26.60.4", "6c7b409d832e182537c0b1dbb8cc3c92f60b7795db7ea5e9f626fb6d6cb8c77f",
                "libBodriosafioEffects.so", "ff71ce36891acb8298eb949472d63cdc69e90632852251512344d60b9c6acfa7", "0.1.0"),
        HOTFIX("26.66.1", "3c24bf3a20471be41449f1e5cd9354c657995468e412d1631d9828b3f3824892",
                "26.66.1/libBodriosafioEffects.so", "c04b70ae7dbbad199e485a2a8b1cce6ea43d6a8e4d424327ee2baa77aa894209", "0.1.1");

        public final String version, engineSha256, effectsAsset, effectsSha256, effectsVersion;

        NativeProfile(String version, String engineSha256, String effectsAsset, String effectsSha256, String effectsVersion) {
            this.version = version;
            this.engineSha256 = engineSha256;
            this.effectsAsset = effectsAsset;
            this.effectsSha256 = effectsSha256;
            this.effectsVersion = effectsVersion;
        }

        public void verifyEngine(File engine) throws IOException {
            if (engine == null || !engine.isFile()) throw new IOException("No se encontró el motor de Minecraft " + version + ".");
            try (FileInputStream input = new FileInputStream(engine)) {
                MessageDigest hash = MessageDigest.getInstance("SHA-256");
                byte[] block = new byte[256 * 1024];
                int count;
                while ((count = input.read(block)) != -1) hash.update(block, 0, count);
                if (!engineSha256.equals(hex(hash.digest())))
                    throw new IOException("El motor instalado no coincide con el perfil ARM64 de Minecraft " + version + ".");
            } catch (java.security.NoSuchAlgorithmException unavailable) {
                throw new IOException("No se pudo verificar el motor de Minecraft.", unavailable);
            }
        }
    }
    public static final String MOD_ID = "bodriosafio_nether";
    public static final String NATIVE_SHA256 = "4791a3d41884a4c49bc6ba02327684433ede70a8ef03fc3a351c01f321b97a17";
    public static final String ADDON_SHA256 = "41c85c8da165240a2c6e3c4b5a33fcff7fbdc679745d5cfd4636ea02b75f798d";
    private ProfileRules() {}

    /** Versions accepted by the pack updater, including the installed Android hotfix. */
    public static boolean compatible(String version) {
        if (version == null) return false;
        String name = version.trim();
        return name.matches("(?:1\\.)?26\\.(?:60|62|63)(?:\\.[0-9]+)*")
            || name.equals("26.66.1") || name.equals("1.26.66.1");
    }

    public static NativeProfile nativeProfile(String version) {
        for (NativeProfile profile : NativeProfile.values()) {
            if (profile.version.equals(version) || ("1." + profile.version).equals(version)) return profile;
        }
        return null;
    }

    public static boolean nativeCompatible(String version) {
        return nativeProfile(version) != null;
    }

    public static NativeProfile requireNativeProfile(String version) throws IOException {
        NativeProfile profile = nativeProfile(version);
        if (profile == null) throw new IOException("Falta un perfil nativo para Minecraft " + displayVersion(version) + ".");
        return profile;
    }

    public static void requireNativeVersion(String version) throws IOException {
        requireNativeProfile(version);
    }

    public static String displayVersion(String version) {
        if (version == null || version.trim().isEmpty()) return "Sin detectar";
        String value = version.trim();
        if (value.startsWith("1.")) {
            String[] parts = value.split("\\.");
            try { if (parts.length >= 3 && Integer.parseInt(parts[1]) >= 26) return value.substring(2); }
            catch (NumberFormatException ignored) { }
        }
        return value;
    }

    public static String sha256(byte[] bytes) throws IOException {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception error) { throw new IOException("No se pudo verificar el archivo.", error); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            int n = item & 255;
            result.append(Character.forDigit(n >>> 4, 16)).append(Character.forDigit(n & 15, 16));
        }
        return result.toString();
    }

    private static String clean(String line) {
        int comment = line.indexOf('#');
        return (comment >= 0 ? line.substring(0, comment) : line).trim();
    }

    private static void finishSection(List<String> output, boolean nether, boolean hasMin, boolean hasMax) {
        if (!nether) return;
        if (!hasMin) output.add("min=0");
        if (!hasMax) output.add("max=256");
    }

    public static String configureNether(String original) throws IOException {
        if (original == null) original = "";
        if (original.length() > 1024 * 1024 || original.indexOf('\0') >= 0)
            throw new IOException("El config.ini no es un archivo de texto válido.");
        if (original.startsWith("\uFEFF")) original = original.substring(1);
        if (original.isEmpty()) return "# Bodriosafio Android\n[Overworld]\nmin=-64\nmax=320\n\n[Nether]\nmin=0\nmax=256\n\n[TheEnd]\nmin=0\nmax=256\n";
        String newline = original.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = original.split("\\r?\\n", -1);
        int count = lines.length;
        if (count > 0 && lines[count - 1].isEmpty()) count--;
        List<String> output = new ArrayList<>();
        boolean nether = false, found = false, hasMin = false, hasMax = false;
        for (int i = 0; i < count; i++) {
            String line = lines[i], value = clean(line);
            if (!value.isEmpty() && !value.startsWith("[") && !value.contains("="))
                throw new IOException("El config.ini contiene una línea no válida.");
            if (value.startsWith("[")) {
                int end = value.indexOf(']');
                if (end < 0) throw new IOException("El config.ini contiene una sección incompleta.");
                finishSection(output, nether, hasMin, hasMax);
                nether = "Nether".equals(value.substring(1, end));
                found |= nether; hasMin = false; hasMax = false;
            }
            int eq = value.indexOf('=');
            String key = eq >= 0 ? value.substring(0, eq).trim() : "";
            if (nether && ("min".equals(key) || "max".equals(key))) {
                String replacement = "min".equals(key) ? "min=0" : "max=256";
                int comment = line.indexOf('#');
                if (comment >= 0) replacement += " " + line.substring(comment);
                output.add(replacement);
                if ("min".equals(key)) hasMin = true; else hasMax = true;
            } else output.add(line);
        }
        finishSection(output, nether, hasMin, hasMax);
        if (!found) {
            if (!output.isEmpty() && !output.get(output.size() - 1).isEmpty()) output.add("");
            output.add("[Nether]"); output.add("min=0"); output.add("max=256");
        }
        StringBuilder result = new StringBuilder();
        for (String line : output) result.append(line).append(newline);
        return result.toString();
    }

    public static void atomicWrite(File file, byte[] bytes) throws IOException {
        File parent = file.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs()))
            throw new IOException("No se pudo preparar la carpeta de Bodriosafío.");
        File temporary = new File(parent, file.getName() + ".tmp." + System.nanoTime());
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes); output.getFD().sync();
            }
            try { Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { if (temporary.exists()) temporary.delete(); }
    }

    public static boolean prepareConfig(File file) throws IOException {
        if (file.exists() && file.length() > 1024 * 1024) throw new IOException("El config.ini es demasiado grande.");
        String previous = file.isFile() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : "";
        String desired = configureNether(previous);
        if (desired.equals(previous)) return false;
        if (file.exists()) {
            File backup = new File(file.getParentFile(), "config.ini.antes_launcher." + System.currentTimeMillis() + "_" + System.nanoTime() + ".bak");
            Files.copy(file.toPath(), backup.toPath());
        }
        atomicWrite(file, desired.getBytes(StandardCharsets.UTF_8));
        return true;
    }
}
