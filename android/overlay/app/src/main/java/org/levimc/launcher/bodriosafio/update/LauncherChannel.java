// Bodriosafío: modificación del 23-11-2026 para el nuevo canal y compatibilidad del perfil.
package org.levimc.launcher.bodriosafio.update;

import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import javax.net.ssl.HttpsURLConnection;

/** Launcher releases have their own feed, independent of the addon feed. */
public final class LauncherChannel {
    public static final String FEED_URL = "https://raw.githubusercontent.com/alanbustamorag-source/panconqueso/main/launcher-latest.json";
    public static final String PACKAGE = "org.levimc.launcher";
    public static final String CERTIFICATE_SHA256 = "144b63f97269476b81895a3de9ac6ee3172cedde94399f8dae7c2bc4279beb47";
    public static final long MAX_APK = 128L * 1024 * 1024;
    public final String version, downloadUrl, sha256, notes;
    public final long versionCode, size;
    public final int minSdk;
    private final String originalJson;

    public LauncherChannel(String text) throws Exception {
        if (text.length() > 64 * 1024) throw new IOException("El canal del launcher es demasiado grande.");
        originalJson = text;
        JSONObject json = new JSONObject(text);
        if (integer(json, "schema") != 1 || !"bodriosafio".equals(json.getString("project"))
            || !"launcher".equals(json.getString("kind")) || !"android".equals(json.getString("platform"))
            || !PACKAGE.equals(json.getString("package"))) throw new IOException("El canal no corresponde al launcher de Bodriosafío.");
        version = json.getString("version");
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IOException("La versión del launcher no es válida.");
        versionCode = integer(json, "version_code");
        if (versionCode < 1 || versionCode > Integer.MAX_VALUE) throw new IOException("La revisión del launcher no es válida.");
        long sdk = integer(json, "min_android");
        if (sdk < 28 || sdk > 100) throw new IOException("La versión mínima de Android no es válida.");
        minSdk = (int) sdk;
        size = integer(json, "size");
        if (size < 1 || size > MAX_APK) throw new IOException("El tamaño del launcher no es válido.");
        sha256 = json.getString("sha256").toLowerCase(java.util.Locale.ROOT);
        if (!sha256.matches("[0-9a-f]{64}")) throw new IOException("Falta la comprobación del launcher.");
        downloadUrl = json.getString("url");
        validateDownloadUrl(downloadUrl);
        notes = json.optString("notes", "");
        if (notes.length() > 4000) throw new IOException("Las notas de la actualización son demasiado largas.");
    }

    private static long integer(JSONObject json, String name) throws Exception {
        Object value = json.get(name);
        if (!(value instanceof Integer) && !(value instanceof Long))
            throw new IOException("El canal tiene un número no válido: " + name);
        return ((Number)value).longValue();
    }

    public boolean newerThan(long installed) { return versionCode > installed; }
    public String asJson() { return originalJson; }

    private static URI https(String value) throws Exception {
        URI uri = new URI(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
            || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
            || uri.getFragment() != null || !uri.normalize().getRawPath().equals(uri.getRawPath())
            || !uri.getRawPath().equals(uri.getPath())) throw new IOException("La dirección de actualización no es válida.");
        return uri;
    }

    public static void validateDownloadUrl(String value) throws Exception {
        URI uri = https(value);
        String path = uri.getPath();
        boolean release = "github.com".equalsIgnoreCase(uri.getHost())
            && path.startsWith("/alanbustamorag-source/panconqueso/releases/download/");
        boolean raw = "raw.githubusercontent.com".equalsIgnoreCase(uri.getHost())
            && path.startsWith("/alanbustamorag-source/panconqueso/main/");
        if ((!release && !raw) || uri.getQuery() != null || !path.endsWith(".apk"))
            throw new IOException("El APK no pertenece al GitHub de Bodriosafío.");
    }

    public static void validateRedirect(String value) throws Exception {
        URI uri = https(value);
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        if ("release-assets.githubusercontent.com".equals(host) || "objects.githubusercontent.com".equals(host)) return;
        if (FEED_URL.equals(value)) return;
        validateDownloadUrl(value);
    }

    private static HttpsURLConnection connect(String value, boolean feed) throws Exception {
        for (int count = 0; count < 5; count++) {
            if (count == 0) {
                if (feed) { if (!FEED_URL.equals(value)) throw new IOException("Canal del launcher incorrecto."); }
                else validateDownloadUrl(value);
            } else validateRedirect(value);
            HttpsURLConnection connection = (HttpsURLConnection)new URL(value).openConnection();
            connection.setConnectTimeout(12000); connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", "Bodriosafio-Launcher/0.1.5");
            connection.setRequestProperty("Cache-Control", "no-cache");
            try {
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String next = connection.getHeaderField("Location");
                    if (next == null) throw new IOException("La descarga devolvió una redirección vacía.");
                    value = new URL(new URL(value), next).toString();
                    connection.disconnect(); continue;
                }
                if (feed && status == 404) { connection.disconnect(); return null; }
                if (status != 200) throw new IOException("GitHub no pudo entregar la actualización (HTTP " + status + ").");
                return connection;
            } catch (Exception error) { connection.disconnect(); throw error; }
        }
        throw new IOException("GitHub devolvió demasiadas redirecciones.");
    }

    public static LauncherChannel latest() throws Exception {
        HttpsURLConnection connection = connect(FEED_URL, true);
        if (connection == null) return null;
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 64 * 1024) throw new IOException("El canal del launcher es demasiado grande.");
                output.write(buffer, 0, count);
            }
            return new LauncherChannel(new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        } finally { connection.disconnect(); }
    }

    public interface Progress { void update(long downloaded, long total); }

    /** Streaming verification keeps partial/corrupt APKs away from the installer. */
    public void copyVerified(InputStream input, OutputStream output, Progress progress) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024]; long total = 0; int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > size) throw new IOException("La descarga supera el tamaño anunciado.");
            digest.update(buffer, 0, count); output.write(buffer, 0, count);
            if (progress != null) progress.update(total, size);
        }
        if (total != size || !sha256.equals(hex(digest.digest()))) throw new IOException("El APK está incompleto o no coincide con la actualización.");
    }

    public void verify(File file) throws Exception {
        if (!file.isFile() || file.length() != size) throw new IOException("El APK descargado está incompleto.");
        try (InputStream input = Files.newInputStream(file.toPath())) { copyVerified(input, new OutputStream() { @Override public void write(int b) {} @Override public void write(byte[] b, int off, int len) {} }, null); }
    }

    public File download(File directory, Progress progress) throws Exception {
        Files.createDirectories(directory.toPath());
        File saved = new File(directory, sha256 + ".apk");
        if (saved.exists()) {
            try { verify(saved); return saved; } catch (Exception badCache) { Files.deleteIfExists(saved.toPath()); }
        }
        HttpsURLConnection connection = connect(downloadUrl, false);
        File temporary = File.createTempFile("download-", ".part", directory);
        try {
            if (connection.getContentLengthLong() > size) throw new IOException("GitHub anuncia un tamaño diferente para el APK.");
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(temporary)) {
                copyVerified(input, output, progress); output.getFD().sync();
            }
            Files.move(temporary.toPath(), saved.toPath(), StandardCopyOption.REPLACE_EXISTING);
            // Keep only the newest verified launcher APK in this private cache.
            File[] old = directory.listFiles();
            if (old != null) for (File file : old)
                if (!file.equals(saved) && file.getName().matches("[0-9a-f]{64}\\.apk")) Files.deleteIfExists(file.toPath());
            return saved;
        } finally { connection.disconnect(); Files.deleteIfExists(temporary.toPath()); }
    }

    public static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) { out.append(Character.forDigit((b & 255) >>> 4, 16)); out.append(Character.forDigit(b & 15, 16)); }
        return out.toString();
    }
}
