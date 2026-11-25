// Bodriosafío: modificación del 23-11-2026 para el nuevo canal y compatibilidad del perfil.
package org.levimc.launcher.bodriosafio;

import java.io.File;
import java.io.FileOutputStream;
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
    public static final String COMPATIBLE_VERSION = "26.66";
    public static final String MOD_ID = "bodriosafio_nether";
    public static final String NATIVE_SHA256 = "4791a3d41884a4c49bc6ba02327684433ede70a8ef03fc3a351c01f321b97a17";
    public static final String ADDON_SHA256 = "900687d53f42d257fcd0642cde8a49952aad696e42b99b467c747879f5e255e3";
    private ProfileRules() {}

    public static boolean compatible(String version) {
        return version != null && version.trim().matches("(?:1\\.)?26\\.(?:60|62|63|66)(?:\\.[0-9]+)*");
    }

    /** Native offsets are validated only for this exact Android build. */
    public static boolean nativeCompatible(String version) {
        if (version == null) return false;
        String value = version.trim();
        if (value.startsWith("1.")) value = value.substring(2);
        return "26.66".equals(value) || "26.66.1".equals(value) || "26.66.01".equals(value);
    }

    public static void requireNativeVersion(String version) throws IOException {
        if (!nativeCompatible(version)) {
            throw new IOException("Los módulos nativos de Bodriosafío todavía no están adaptados a Minecraft "
                + displayVersion(version) + ". La preparación requiere Nether y Anticombustión nativos; no se sustituirán por modo addon.");
        }
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
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) {
                int n = item & 255;
                result.append(Character.forDigit(n >>> 4, 16)).append(Character.forDigit(n & 15, 16));
            }
            return result.toString();
        } catch (Exception error) { throw new IOException("No se pudo verificar el archivo.", error); }
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
