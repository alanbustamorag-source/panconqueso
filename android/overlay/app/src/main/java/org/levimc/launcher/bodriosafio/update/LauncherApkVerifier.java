// Bodriosafío: modificación del 23-11-2026 para el nuevo canal y compatibilidad del perfil.
package org.levimc.launcher.bodriosafio.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;

/** Check the archive itself as well as the public release metadata. */
public final class LauncherApkVerifier {
    private LauncherApkVerifier() {}

    public static long installedCode(Context context) throws Exception {
        return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
    }

    private static String signer(PackageInfo info) throws Exception {
        if (info == null || info.signingInfo == null) throw new IOException("El APK no tiene una firma válida.");
        Signature[] signatures = info.signingInfo.getApkContentsSigners();
        if (signatures == null || signatures.length != 1) throw new IOException("No se pudo comprobar la firma del launcher.");
        return LauncherChannel.hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()));
    }

    public static void verify(Context context, LauncherChannel channel, File file) throws Exception {
        channel.verify(file);
        if (Build.VERSION.SDK_INT < channel.minSdk) throw new IOException("Esta actualización necesita una versión más reciente de Android.");
        PackageManager manager = context.getPackageManager();
        PackageInfo installed = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo archive = manager.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (archive == null || !LauncherChannel.PACKAGE.equals(archive.packageName)
            || !LauncherChannel.PACKAGE.equals(context.getPackageName())) throw new IOException("El APK no es el launcher de Bodriosafío.");
        if (archive.getLongVersionCode() != channel.versionCode || !channel.version.equals(archive.versionName)
            || !channel.newerThan(installed.getLongVersionCode())) throw new IOException("La revisión del APK no coincide o ya está instalada.");
        if (archive.applicationInfo == null || archive.applicationInfo.minSdkVersion != channel.minSdk)
            throw new IOException("La compatibilidad del APK no coincide con la actualización.");
        if (!LauncherChannel.CERTIFICATE_SHA256.equals(signer(archive))
            || !LauncherChannel.CERTIFICATE_SHA256.equals(signer(installed)))
            throw new IOException("La actualización tiene una firma diferente. No se instaló.");
    }
}
