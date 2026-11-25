// Bodriosafío: modificación del 23-11-2026 para el nuevo canal y compatibilidad del perfil.
package org.levimc.launcher.bodriosafio.update;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import javax.net.ssl.HttpsURLConnection;

/** Public, read-only channel. No GitHub credentials belong in the launcher. */
public final class AddonChannel {
    public static final String URL = "https://raw.githubusercontent.com/alanbustamorag-source/panconqueso/main/latest.json";
    public static final long MAX_ARCHIVE = 64L * 1024 * 1024;
    public final String version, downloadUrl, sha256, notes;
    public final long size;
    public final int[] behaviorVersion, resourceVersion;

    public AddonChannel(String text, String platform, String launcherVersion) throws Exception {
        JSONObject json = new JSONObject(text);
        if (json.getInt("schema") != 1 || !"bodriosafio".equals(json.getString("project"))
                || !"stable".equals(json.getString("channel"))) throw new IOException("El canal de actualizaciones no es compatible.");
        if (!org.levimc.launcher.bodriosafio.ProfileRules.compatible(json.getString("minecraft"))) throw new IOException("Esta actualización necesita otra versión de Minecraft.");
        int[] minimum = version(json.getJSONObject("minimum_launcher").getString(platform));
        if (compare(version(launcherVersion), minimum) < 0) throw new IOException("Actualiza primero el launcher para usar esta versión del addon.");
        version = json.getString("version");
        version(version);
        JSONObject asset = json.getJSONObject("addon");
        downloadUrl = asset.getString("url");
        validateUrl(downloadUrl);
        sha256 = asset.getString("sha256").toLowerCase(java.util.Locale.ROOT);
        if (!sha256.matches("[0-9a-f]{64}")) throw new IOException("La actualización no incluye una comprobación válida.");
        size = asset.getLong("size");
        if (size < 1 || size > MAX_ARCHIVE) throw new IOException("El tamaño de la actualización no es válido.");
        notes = json.optString("notes", "");
        JSONArray packs = json.getJSONArray("packs");
        int[] bp = null, rp = null;
        if (packs.length() != 2) throw new IOException("El canal debe identificar ambos packs.");
        for (int i = 0; i < packs.length(); i++) {
            JSONObject p = packs.getJSONObject(i);
            if (AddonInstaller.BP.equalsIgnoreCase(p.getString("uuid")) && "data".equals(p.getString("type")) && bp == null) bp = version(p.getJSONArray("version"));
            else if (AddonInstaller.RP.equalsIgnoreCase(p.getString("uuid")) && "resources".equals(p.getString("type")) && rp == null) rp = version(p.getJSONArray("version"));
            else throw new IOException("La actualización no identifica los packs de Bodriosafío.");
        }
        if (bp == null || rp == null || compare(version(version), bp) != 0) throw new IOException("Las versiones del canal no coinciden.");
        behaviorVersion = bp; resourceVersion = rp;
    }

    public static void validateUrl(String value) throws Exception {
        java.net.URI uri = new java.net.URI(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"raw.githubusercontent.com".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || !uri.getPath().startsWith("/alanbustamorag-source/panconqueso/")
                || !uri.getRawPath().equals(uri.getPath()) || uri.getQuery() != null || uri.getFragment() != null
                || !uri.normalize().getPath().equals(uri.getPath())) throw new IOException("La descarga no pertenece al canal de Bodriosafío.");
    }

    public interface Progress { void update(long received, long total); }

    public static byte[] get(String value, long limit) throws Exception {
        return get(value, limit, null);
    }

    /** For an archive, limit is the exact size announced by its verified channel. */
    public static byte[] get(String value, long limit, Progress progress) throws Exception {
        for (int redirects = 0; redirects < 4; redirects++) {
            validateUrl(value);
            HttpsURLConnection connection = (HttpsURLConnection) new URL(value).openConnection();
            connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", "Bodriosafio-Launcher/0.1.5");
            connection.setRequestProperty("Cache-Control", "no-cache");
            try {
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 307 || status == 308) {
                    value = new URL(new URL(value), connection.getHeaderField("Location")).toString(); continue;
                }
                if (status != 200) throw new IOException("No se pudo descargar la actualización (HTTP " + status + ").");
                long length = connection.getContentLengthLong();
                if (length > limit) throw new IOException("La descarga supera el tamaño permitido.");
                try (InputStream input = connection.getInputStream()) {
                    return read(input, limit, progress == null ? length : limit, progress);
                }
            } finally { connection.disconnect(); }
        }
        throw new IOException("El canal devolvió demasiadas redirecciones.");
    }

    public static byte[] read(InputStream input, long limit) throws IOException {
        return read(input, limit, -1, null);
    }

    public static byte[] read(InputStream input, long limit, long total, Progress progress) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[16384]; int count;
        if (progress != null) progress.update(0, total);
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + (long) count > limit) throw new IOException("El archivo supera el tamaño permitido.");
            output.write(buffer, 0, count);
            if (progress != null) progress.update(output.size(), total);
        }
        return output.toByteArray();
    }

    public void verify(byte[] bytes) throws Exception {
        if (bytes.length != size || !sha256.equals(digest(bytes))) throw new IOException("La descarga está incompleta o no coincide con la versión publicada. No se instaló.");
    }

    public static String digest(byte[] bytes) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }

    public static int[] version(String text) throws IOException {
        if (!text.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IOException("La versión no tiene un formato válido.");
        String[] parts = text.split("\\."); int[] out = new int[3];
        try { for (int i = 0; i < 3; i++) out[i] = Integer.parseInt(parts[i]); }
        catch (NumberFormatException e) { throw new IOException("La versión está fuera de rango.", e); }
        return out;
    }

    public static int[] version(JSONArray array) throws Exception {
        if (array.length() != 3) throw new IOException("La versión del pack no es válida.");
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            Object value = array.get(i);
            if (!(value instanceof Number) || ((Number) value).doubleValue() != ((Number) value).intValue()
                    || ((Number) value).longValue() < 0) throw new IOException("La versión del pack no es válida.");
            out[i] = ((Number) value).intValue();
        }
        return out;
    }

    public static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) { int n = Integer.compare(a[i], b[i]); if (n != 0) return n; }
        return 0;
    }
    public static String display(int[] v) { return v[0] + "." + v[1] + "." + v[2]; }
}
